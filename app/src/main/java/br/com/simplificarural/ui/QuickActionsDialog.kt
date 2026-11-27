package br.com.simplificarural.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.simplificarural.R
import br.com.simplificarural.ai.RuralLanguageNormalizer
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.data.local.MilkSecretaryService
import br.com.simplificarural.data.local.MilkShift
import br.com.simplificarural.domain.actions.QuickCommand
import br.com.simplificarural.domain.actions.expressesFinance
import br.com.simplificarural.domain.actions.parseQuickCommand
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.management.StockDirection
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.navigation.RuralRoutes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun QuickActionsDialog(close: () -> Unit, onOpen: (String) -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val audio = rememberRuralAudioGuide()
    var moduleResult by remember { mutableStateOf<QuickModuleResult?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<QuickActionReview?>(null) }
    var saleNeedingAmount by remember { mutableStateOf<SaleAmountRequest?>(null) }
    fun readReview(description: String) {
        audio.speak("Confira antes de salvar. $description. Se estiver certo, toque no botão verde, Confirmar e salvar.")
    }
    fun requestSaleAmount(sale: SaleAmountRequest, fromVoice: Boolean) {
        review = null
        error = null
        saleNeedingAmount = sale
        if (fromVoice) audio.speak("${sale.description}. Quanto você recebeu no total? Toque no microfone para falar o valor.")
    }
    fun prepare(command: String, fromVoice: Boolean = false) {
        keyboard?.hide()
        val parsed = parseQuickCommand(command)
        runCatching {
            quickModuleAction(context, command)?.let {
                    moduleResult = it
                    error = null
                    if (fromVoice) audio.speak(it.text)
                    return
                }
                parsed?.requestMissingSaleAmount(context)?.let {
                requestSaleAmount(it, fromVoice)
                return
            }
            prepareQuickAction(context, command, parsed)
        }
            .onSuccess {
                review = it
                error = null
                if (fromVoice) readReview(it.description)
            }
            .onFailure {
                val detail = it.message ?: "Confira o comando e tente novamente."
                error = detail
                if (fromVoice) audio.speak(detail)
            }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }?.trim()
            if (spoken.isNullOrEmpty()) {
                error = "O ditado não retornou texto. Tente novamente ou digite a ação."
            } else {
                input = spoken
                prepare(spoken, fromVoice = true)
            }
        } else {
            error = "O ditado foi encerrado sem texto. Tente novamente ou digite a ação."
        }
    }

    moduleResult?.let { result ->
        QuickModuleDialog(result, back = { moduleResult = null }, open = onOpen, saved = onSaved)
        return
    }
    saleNeedingAmount?.let { sale ->
        SaleAmountDialog(sale.description, back = { saleNeedingAmount = null }) { total ->
            saleNeedingAmount = null
            runCatching { prepareQuickAction(context, "", sale.withAmount(total)) }
                .onSuccess { review = it; readReview(it.description) }
                .onFailure { error = it.message ?: "Confira o produto e tente novamente." }
        }
        return
    }
    val selected = review
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (selected == null) "Novo lançamento" else "Confira antes de salvar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selected == null) {
                    Text("Escreva ou fale o que aconteceu.")
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(200); error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("O que aconteceu?") },
                        placeholder = { Text("300 ovos") },
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) prepare(input) })
                    )
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        keyboard?.hide()
                        runCatching {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
                        }.onFailure { error = "Ditado indisponível neste aparelho. Digite a ação." }
                    }) {
                        Icon(painterResource(R.drawable.ic_rural_microphone), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Falar no microfone")
                    }
                    Text("Exemplos: “300 ovos” ou “Vendi 50 bandejas a 12 reais”.")
                } else {
                    Text(selected.description)
                    selected.collectedEggs?.let { count ->
                        OutlinedButton({ requestSaleAmount(requireNotNull(QuickCommand.LooseEggSale(count).requestMissingSaleAmount(context)), false) }) {
                            Icon(painterResource(R.drawable.ic_rural_sale), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Foi uma venda")
                        }
                    }
                    Text("Fazenda: ${selected.farmName}")
                    Text("Data e hora são registradas ao confirmar.")
                    TextButton({ readReview(selected.description) }) {
                        Icon(painterResource(R.drawable.ic_rural_audio), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ouvir lançamento")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (selected == null) {
                Button(onClick = { prepare(input) }, enabled = input.isNotBlank()) { Text("Revisar") }
            } else {
                Button(onClick = {
                    if (review !== selected) return@Button
                    review = null
                    runCatching { selected.save() }
                        .onSuccess { onSaved(selected.route) }
                        .onFailure { error = it.message ?: "Confira o histórico antes de tentar novamente." }
                }) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Confirmar e salvar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selected == null) close() else { review = null; error = null }
            }) { Text(if (selected == null) "Cancelar" else "Corrigir") }
        }
    )
}

private class QuickActionReview(
    val description: String, val route: String, val farmName: String, val collectedEggs: Int?, val save: () -> Unit
)

private fun prepareQuickAction(context: Context, input: String, parsed: QuickCommand?): QuickActionReview {
    val command = requireNotNull(parsed) {
        if (expressesFinance(input)) {
            "Informe o produto, a quantidade e o valor. Para gastos, diga o valor e com o que gastou."
        } else "Não identifiquei uma ação completa. Informe a ação, a quantidade e a unidade."
    }
    val farm = FarmContextStore(context)
    val scope = farm.current()
    val management = FarmManagementService(context)
    fun review(description: String, route: String, collectedEggs: Int? = null, save: () -> Unit) =
        QuickActionReview(description, route, farm.farmName(), collectedEggs) {
            check(farm.current() == scope) { "A propriedade mudou. Revise o lançamento novamente." }
            save()
        }

    return when (command) {
        is QuickCommand.Eggs -> review("Aves · Entrada de ${command.count} ovos da coleta", RuralRoutes.BIRDS, collectedEggs = command.count) {
            management.registerEggProduction(scope, command.count)
        }
        is QuickCommand.Milk -> review("Bovinos · Ordenha de ${command.liters.stripTrailingZeros().toPlainString().replace('.', ',')} L", RuralRoutes.CATTLE) {
            val now = LocalDateTime.now()
            val diary = MilkSecretaryService(context)
            diary.records(scope)
            management.registerMilkProduction(scope, command.liters, now.toLocalDate())
            diary.record(scope, command.liters, MilkShift.automatic(now.hour), now)
        }
        is QuickCommand.Stock -> {
            val selectedScope = CashViewScope.SelectedUnit(scope)
            fun matches() = management.stock(selectedScope).filter { item ->
                val name = RuralLanguageNormalizer.normalize(item.productName)
                item.unit.equals(command.unit, ignoreCase = true) &&
                    (name == command.product || command.product == "racao" && name.startsWith("racao "))
            }
            val matches = matches()
            val exact = matches.filter { RuralLanguageNormalizer.normalize(it.productName) == command.product }
            val candidates = exact.ifEmpty { matches }
            require(candidates.size <= 1) { "Há mais de um tipo de ração. Informe o nome completo do item." }
            val item = candidates.singleOrNull()
            require(command.incoming || item != null) { "Item não encontrado no estoque nessa unidade de medida." }
            val productStart = Regex("""\s(?:kg|quilos?|l|litros?|unidades?)\s+de\s+""", RegexOption.IGNORE_CASE)
                .find(input)?.range?.last?.plus(1)
            val originalProduct = productStart?.let { input.substring(it).trim().trimEnd('.', '!') } ?: command.product
            val product = item?.productName ?: if (command.product == "racao") "Ração" else originalProduct.replaceFirstChar { it.uppercase() }
            val unit = item?.unit ?: command.unit
            val direction = if (command.incoming) StockDirection.ENTRADA else StockDirection.SAIDA
            val label = if (command.incoming) "Entrada" else "Saída"
            val amount = command.quantity.stripTrailingZeros().toPlainString().replace('.', ',')
            review("Estoque · $label de $amount $unit de $product", RuralRoutes.stockDetail(product)) {
                val current = management.stock(selectedScope).firstOrNull { it.productName == product && it.unit == unit }
                require(command.incoming || current != null && current.quantity >= command.quantity) { "Saldo insuficiente para esta saída." }
                management.adjustInventory(
                    scope, product, command.quantity, unit, direction,
                    description = "$label de estoque: $product",
                    inventoryCategory = current?.inventoryCategory ?: if (command.product.startsWith("racao")) "Alimentação" else "Outros"
                )
            }
        }
        is QuickCommand.Vaccine -> {
            val animals = AnimalRecordsService(context)
            val target = requireNotNull(animals.findTarget(scope, command.species, command.target)) {
                "Cadastre o animal ou lote ${command.target} antes de registrar a vacina."
            }
            review("Saúde · Vacina contra ${command.vaccine} em ${command.target}", RuralRoutes.HEALTH) {
                animals.registerHealth(scope, target, command.vaccine)
            }
        }
        is QuickCommand.SwineBirth -> review(
            "Suínos · Parto com ${command.alive} leitões vivos e ${command.dead} mortos", RuralRoutes.SWINE_BREEDING
        ) {
            management.registerSwineFarrowing(scope, command.alive, command.dead)
        }
        is QuickCommand.Expense -> {
            val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            val description = command.description.replaceFirstChar { it.uppercase() }
            review("Gasto com $description\nSai do caixa: ${currency.format(command.amount)}", RuralRoutes.FINANCE) {
                management.registerExpense(scope, br.com.simplificarural.domain.management.FinancialCategory.OUTRA_DESPESA,
                    command.amount, description = description)
            }
        }
        is QuickCommand.Trade -> {
            val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            val matches = management.stock(CashViewScope.SelectedUnit(scope)).filter {
                it.unit.equals(command.unit, true) && RuralLanguageNormalizer.normalize(it.productName) == command.product
            }
            require(matches.size <= 1) { "Encontrei mais de um item. Escolha o produto no estoque." }
            require(command.buying || matches.isNotEmpty()) { "Produto não encontrado no estoque. Confira o nome e a unidade." }
            val product = matches.singleOrNull()?.productName
                ?: command.product.replace(Regex("""\bracao\b"""), "ração").replaceFirstChar { it.uppercase() }
            val quantity = command.quantity.stripTrailingZeros().toPlainString().replace('.', ',')
            val title = if (command.buying) "Compra" else "Venda"
            val movement = if (command.buying) "Sai do caixa" else "Total recebido no caixa"
            val perUnit = command.unitPrice?.let { "\nPreço por unidade: ${currency.format(it)}" }.orEmpty()
            review("$title de $quantity ${command.unit} de $product$perUnit\n$movement: ${currency.format(command.total)}",
                if (command.buying) RuralRoutes.PURCHASES else RuralRoutes.SALES) {
                if (command.buying) management.registerPurchaseTotal(scope, product, command.quantity, command.unit, command.total, command.unitPrice)
                else management.registerSaleFromStock(scope, product, command.unit, command.quantity, command.total, command.unitPrice)
            }
        }
        is QuickCommand.LooseEggSale -> {
            val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            val total = requireNotNull(command.total)
            review("Venda de ${command.count} ovos\nTotal recebido no caixa: ${currency.format(total)}", RuralRoutes.SALES) {
                management.registerLooseEggSale(scope, command.count, total, command.unitPrice)
            }
        }
        is QuickCommand.MilkSale -> {
            requireMilkSaleProduct(context, command)
            val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            val total = requireNotNull(command.total)
            val amount = command.liters.stripTrailingZeros().toPlainString().replace('.', ',')
            review("Venda de $amount litros de leite\nTotal recebido no caixa: ${currency.format(total)}", RuralRoutes.SALES) {
                management.registerMilkSale(scope, command.liters, total, command.unitPrice)
            }
        }
        is QuickCommand.EggTraySale -> {
            val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            val total = command.lines.sumOf { it.subtotal }
            val detail = command.lines.joinToString("\n") {
                "${it.trays.toPlainString()} × ${currency.format(it.unitPrice)} = ${currency.format(it.subtotal)}"
            }
            review("Venda de ${command.product.lowercase()}\n$detail\nTotal recebido no caixa: ${currency.format(total)}", RuralRoutes.SALES) {
                val unit = if (command.product.startsWith("Cartelas")) "cartelas" else "bandejas"
                management.registerEggTraySale(scope, command.product, command.lines, unit)
            }
        }
    }
}
