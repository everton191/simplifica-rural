package br.com.simplificarural.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.simplificarural.ai.RuralLanguageNormalizer
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.data.local.MilkSecretaryService
import br.com.simplificarural.data.local.milkCycle
import br.com.simplificarural.domain.agenda.AgendaStatus
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.financial.CashEntryKind
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.health.HealthEventType
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.domain.reproduction.ReproductionStage
import br.com.simplificarural.navigation.RuralRoutes
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class QuickModuleResult(val text: String, val route: String, val save: (() -> Unit)? = null)

internal fun quickModuleAction(context: Context, message: String): QuickModuleResult? {
    val input = RuralLanguageNormalizer.normalize(message).trimEnd('.')
    val farm = FarmContextStore(context)
    val scope = farm.current()
    val cashScope = CashViewScope.SelectedUnit(scope)
    val today = LocalDate.now()
    val management = FarmManagementService(context)
    val locale = Locale.forLanguageTag("pt-BR")
    val currency = NumberFormat.getCurrencyInstance(locale)
    val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    fun amount(value: BigDecimal) = value.stripTrailingZeros().toPlainString().replace('.', ',')
    fun draft(text: String, route: String, save: () -> Unit) = QuickModuleResult(text, route) {
        check(farm.current() == scope) { "A propriedade mudou. Revise o lançamento novamente." }
        save()
    }

    if (input in setOf("quanto vendi hoje", "quanto entrou hoje", "quanto gastei hoje", "quanto saiu hoje", "saldo do caixa hoje")) {
        val entries = management.cashEntries(cashScope).filter { it.date == today }
        val incoming = entries.filter { it.kind == CashEntryKind.ENTRADA }.sumOf { it.amount }
        val outgoing = entries.filter { it.kind == CashEntryKind.SAIDA }.sumOf { it.amount }
        val sales = management.confirmedRecords(cashScope).filter {
            it.date == today && it.category == br.com.simplificarural.domain.management.FinancialCategory.VENDA_PRODUCAO
        }.sumOf { it.totalAmount ?: BigDecimal.ZERO }
        val answer = when (input) {
            "quanto vendi hoje" -> "Valor recebido em vendas hoje: ${currency.format(sales)}."
            "quanto entrou hoje" -> "Entrou hoje: ${currency.format(incoming)}."
            "quanto gastei hoje", "quanto saiu hoje" -> "Saiu hoje: ${currency.format(outgoing)}."
            else -> "Resultado de hoje: ${currency.format(incoming - outgoing)}."
        }
        return QuickModuleResult(answer, RuralRoutes.FINANCE)
    }
    Regex("""(?:quanto resta de|quanto tem de|saldo de) (.+)""").matchEntire(input)?.let { match ->
        val product = match.groupValues[1]
        val balances = management.stock(cashScope).filter {
            val name = RuralLanguageNormalizer.normalize(it.productName)
            name == product || name.startsWith("$product ")
        }
        val answer = if (balances.isEmpty()) "Nenhum item encontrado para $product." else balances.joinToString("\n") {
            "${it.productName}: ${amount(it.quantity)} ${it.unit}"
        }
        return QuickModuleResult(answer, RuralRoutes.STOCK)
    }
    if (input in setOf("quanto leite tirei nesta semana", "quanto leite tirei nessa semana", "total do ciclo de leite", "quanto leite tirei hoje")) {
        val dairy = MilkSecretaryService(context)
        val cycle = milkCycle(today, dairy.closingDay(scope))
        val answer = if (input.endsWith(" hoje")) "Hoje: ${amount(dairy.dayTotal(scope, today))} L." else {
            "Ciclo de ${cycle.start.format(dateFormat)} a ${cycle.endInclusive.format(dateFormat)}: ${amount(dairy.weeklyTotal(scope, dairy.closingDay(scope), today))} L até hoje."
        }
        return QuickModuleResult(answer, RuralRoutes.CATTLE)
    }
    if (input in setOf("agenda de hoje", "agenda de amanha", "quais lembretes tenho hoje")) {
        val date = if (input.endsWith("amanha")) today.plusDays(1) else today
        val tasks = AnimalRecordsService(context).tasks(scope).filter { it.dueDate == date && it.status != AgendaStatus.CANCELADA }
        return QuickModuleResult(tasks.joinToString("\n") { it.title }.ifBlank { "Nenhum lembrete em ${date.format(dateFormat)}." }, RuralRoutes.AGENDA)
    }
    Regex("""(?:agendar|lembre[- ]me de|criar lembrete para) (.+) (hoje|amanh[ãa]|[0-9]{4}-[0-9]{2}-[0-9]{2})""",
        RegexOption.IGNORE_CASE).matchEntire(message.trim().trimEnd('.'))?.let {
        val (title, rawDate) = it.destructured
        val date = when (RuralLanguageNormalizer.normalize(rawDate)) {
            "hoje" -> today
            "amanha" -> today.plusDays(1)
            else -> runCatching { LocalDate.parse(rawDate) }.getOrElse { error("Informe uma data válida.") }
        }
        require(!date.isBefore(today)) { "Escolha hoje ou uma data futura." }
        return draft("Agendar $title para ${date.format(dateFormat)}.", RuralRoutes.AGENDA) {
            AnimalRecordsService(context).schedule(scope, title, date)
        }
    }
    Regex("""recebi (?:r )?(\d+(?:[,.]\d{1,2})?)(?: reais| real)? de (.+)""").matchEntire(input)?.let {
        require(!Regex("""\b(?:venda|pedido|parcela|cliente|divida|pagamento|e|depois|tambem|nao)\b""")
            .containsMatchIn(it.groupValues[2])) { "Para baixar uma venda ou pedido, abra o lançamento original." }
        require(!Regex("""[-−]\s*\d""").containsMatchIn(message)) { "Informe um valor positivo." }
        val total = it.groupValues[1].replace(',', '.').toBigDecimal()
        require(total.signum() > 0 && total.precision() <= 12) { "Informe um valor recebido válido." }
        val description = it.groupValues[2]
        return draft("Outra receita de ${currency.format(total)} · $description.", RuralRoutes.FINANCE) {
            management.registerOtherIncome(scope, total, description = description)
        }
    }
    Regex("""(?:a )?(?:vaca|novilha) ([a-z0-9-]+) pariu (\d+) bezerros? vivos?(?: e (\d+) mortos?)?""").matchEntire(input)?.let {
        val (_, id, live, dead) = it.groupValues
        val bornAlive = live.toIntOrNull() ?: error("Quantidade inválida.")
        val bornDead = if (dead.isBlank()) null else dead.toIntOrNull() ?: error("Quantidade inválida.")
        val animals = AnimalRecordsService(context)
        val target = animals.findTarget(scope, AnimalSpecies.BOVINO, id) ?: error("Bovino $id não encontrado.")
        return draft("Parto da vaca $id · $bornAlive vivos${bornDead?.let { count -> ", $count mortos" }.orEmpty()}.", RuralRoutes.CATTLE_REPRODUCTION) {
            animals.registerReproduction(scope, target, ReproductionStage.PARTO, bornAlive = bornAlive, bornDead = bornDead)
        }
    }
    Regex("""(tratei|vermifuguei) (?:a |o )?(vaca|boi|novilha|suino|porco|ave|galinha) ([a-z0-9-]+) com (.+)""").matchEntire(input)?.let {
        val (verb, animal, id, product) = it.destructured
        require(!Regex("""\b(?:e|depois|tambem|nao|amanha)\b""").containsMatchIn(product)) { "Informe um tratamento por vez." }
        val species = when (animal) {
            "vaca", "boi", "novilha" -> AnimalSpecies.BOVINO
            "suino", "porco" -> AnimalSpecies.SUINO
            else -> AnimalSpecies.AVE
        }
        val animals = AnimalRecordsService(context)
        val target = animals.findTarget(scope, species, id) ?: error("Animal $id não encontrado.")
        val type = if (verb == "tratei") HealthEventType.TRATAMENTO else HealthEventType.VERMIFUGO
        return draft("Saúde · $animal $id · $product.", RuralRoutes.HEALTH) {
            animals.registerHealth(scope, target, product, type)
        }
    }
    val destinations = mapOf(
        "pedidos" to RuralRoutes.ORDERS, "pesagem" to RuralRoutes.SWINE_WEIGHT,
        "cadastro de bovinos" to RuralRoutes.CATTLE_NEW, "lotes de aves" to RuralRoutes.BIRD_LOTS,
        "lotes de suinos" to RuralRoutes.SWINE_LOTS, "fechamento do leite" to RuralRoutes.CATTLE_MILK_CLOSURE,
        "reproducao bovina" to RuralRoutes.CATTLE_REPRODUCTION, "agenda" to RuralRoutes.AGENDA,
        "mortalidade de suinos" to RuralRoutes.feature("Mortalidade suínos"),
        "desmame" to RuralRoutes.SWINE_BREEDING
    )
    val area = input.removePrefix("abrir ")
    return destinations[area]?.takeIf { input.startsWith("abrir ") }?.let { QuickModuleResult("Abrir $area.", it) }
}

@Composable
internal fun QuickModuleDialog(result: QuickModuleResult, back: () -> Unit, open: (String) -> Unit, saved: (String) -> Unit) {
    var error by remember(result) { mutableStateOf<String?>(null) }
    var applied by remember(result) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = back,
        title = { Text(if (result.save == null) "Consulta" else "Confira antes de salvar") },
        text = { Text(error ?: result.text) },
        confirmButton = {
            Button(onClick = {
                val save = result.save
                if (save == null) open(result.route) else if (!applied) {
                    applied = true
                    runCatching(save).onSuccess { saved(result.route) }
                        .onFailure { error = it.message ?: "Não foi possível salvar." }
                }
            }, enabled = !applied) { Text(if (result.save == null) "Abrir área" else "Confirmar e salvar") }
        },
        dismissButton = { TextButton(back) { Text("Voltar") } }
    )
}
