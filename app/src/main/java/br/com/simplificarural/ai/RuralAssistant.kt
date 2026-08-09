package br.com.simplificarural.ai

import android.content.Context
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.domain.property.FarmScope
import br.com.simplificarural.domain.actions.ActionExecution
import br.com.simplificarural.domain.actions.RuralAction
import br.com.simplificarural.ai.actions.RuralActionExecutor
import br.com.simplificarural.domain.financial.ReceivableService
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.health.HealthEventType
import br.com.simplificarural.data.local.MilkSecretaryService
import br.com.simplificarural.data.local.MilkShift
import br.com.simplificarural.domain.agenda.AgendaType
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RuralAssistant(private val context: Context) {
    private val modelRepository = AiModelRepository(context)
    private val fallback = DeterministicCommandParser()
    private val store = LocalEventStore(context)
    private val farmContext = FarmContextStore(context)
    private val secretary = SecretaryConversationStore(context)

    suspend fun handle(message: String, pending: AiDraft? = null, scope: FarmScope = farmContext.current()): AssistantResult {
        val understoodMessage = RuralLanguageNormalizer.normalize(message)
        val activePending = pending ?: secretary.pending(scope)
        val deterministicDraft = fallback.continuePurchase(activePending, understoodMessage) ?: fallback.continueSale(activePending, understoodMessage) ?: fallback.parse(understoodMessage)
        val interpretedDraft = if (deterministicDraft.action != RuralActionType.DESCONHECIDA) {
            deterministicDraft
        } else if (modelRepository.isInstalled()) {
            runCatching { GemmaLocalAiEngine(context).interpret(understoodMessage, scope) }
                .getOrElse { deterministicDraft }
        } else deterministicDraft
        val draft = interpretedDraft.withAutomaticDateTime()
        if (draft.action !in setOf(RuralActionType.DESCONHECIDA, RuralActionType.CONSULTAR_RESUMO, RuralActionType.CONSULTAR_HISTORICO)) secretary.save(draft, scope)
        return when (draft.action) {
            RuralActionType.DESCONHECIDA -> AssistantResult.Reply(
                "Posso ajudar a registrar ovos, leite, ração ou despesa. Diga o que aconteceu hoje.", draft
            )
            RuralActionType.CONSULTAR_RESUMO -> AssistantResult.Reply(store.summary(scope), draft)
            RuralActionType.CONSULTAR_HISTORICO -> AssistantResult.Reply(store.history(scope, LocalDate.parse(draft.parameters.getValue("data"))), draft)
            else -> AssistantResult.Reply(draft.summary, draft)
        }
    }

    private fun AiDraft.withAutomaticDateTime(): AiDraft {
        if (!requiresConfirmation) return this
        val now = LocalDateTime.now()
        return copy(parameters = parameters + mapOf(
            "data" to parameters.getOrDefault("data", now.toLocalDate().toString()),
            "hora" to parameters.getOrDefault("hora", now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
        ))
    }

    fun confirm(draft: AiDraft, scope: FarmScope = farmContext.current()): String {
        require(draft.requiresConfirmation) { "Esta ação não precisa de confirmação." }
        val execution = draft.toRuralAction(scope)?.let(RuralActionExecutor(context)::executeConfirmed)
        if (execution is ActionExecution.Rejected) return "Não salvei o registro: ${execution.reason}"
        if (draft.action == RuralActionType.REGISTRAR_VENDA_ESTOQUE) {
            val balance = draft.parameters.getValue("saldoAberto").toBigDecimal()
            if (balance > java.math.BigDecimal.ZERO) ReceivableService(context).create(scope, draft.parameters.getValue("cliente"), balance, LocalDate.parse(draft.parameters.getValue("vencimento")), "Venda de ${draft.parameters.getValue("produto")}")
        }
        if (draft.action == RuralActionType.REGISTRAR_VACINA) {
            val records = AnimalRecordsService(context)
            val species = AnimalSpecies.valueOf(draft.parameters.getValue("especie"))
            val target = records.findTarget(scope, species, draft.parameters.getValue("identificacao"))
                ?: return "Não salvei a vacina: cadastre primeiro ${draft.parameters.getValue("identificacao")} nesta propriedade."
            records.registerHealth(scope, target, draft.parameters.getValue("vacina"), HealthEventType.VACINA)
        }
        if (draft.action == RuralActionType.REGISTRAR_AGENDA) {
            val type = runCatching { AgendaType.valueOf(draft.parameters.getValue("tipo")) }.getOrDefault(AgendaType.OUTRO)
            AnimalRecordsService(context).schedule(scope, draft.parameters.getValue("titulo"), LocalDate.parse(draft.parameters.getValue("data")), type, notes = draft.parameters["observacao"])
        }
        if (draft.action == RuralActionType.REGISTRAR_LEITE) {
            val shift = runCatching { MilkShift.valueOf(draft.parameters["turno"] ?: MilkShift.NAO_INFORMADO.name) }.getOrDefault(MilkShift.NAO_INFORMADO)
            val diary = MilkSecretaryService(context)
            diary.record(scope, draft.parameters.getValue("litros").toBigDecimal(), shift)
            val total = diary.dayTotal(scope).stripTrailingZeros().toPlainString()
            val missing = diary.missingShift(scope)
            secretary.clear(scope)
            return "Registrei ${draft.parameters.getValue("litros")} L no turno ${shift.name.lowercase()}. Total de hoje: $total L." + (missing?.let { " Ainda não tenho o turno ${it.name.lowercase()}; deseja informar?" } ?: "")
        }
        store.record(draft, scope)
        secretary.clear(scope)
        return "Registro confirmado e salvo. O estoque e o financeiro foram atualizados quando aplicável."
    }

    fun confirmedEggs(scope: FarmScope = farmContext.current()): Int = store.eggsTotal(scope)
    fun pendingDraft(scope: FarmScope = farmContext.current()): AiDraft? = secretary.pending(scope)

    fun operationalTestSummary(): String = "Teste integrado (6 lançamentos):\n1. Compra: 200 kg de ração de aves a R$ 2,50/kg (R$ 500 no caixa).\n2. Compra: 300 bandejas vazias a R$ 0,50 (R$ 150 no caixa).\n3. Produção: 900 ovos no estoque.\n4. Consumo: 30 kg de ração de aves no estoque.\n5. Venda: 50 bandejas de ovos a R$ 10 (R$ 500 no caixa).\n6. Despesa: R$ 80 de combustível.\n\nConfirme para gravar os seis lançamentos nas áreas corretas."

    fun analyzeOperationalSituation(message: String, scope: FarmScope = farmContext.current()): OperationalProposal? = runCatching {
        fun number(pattern: String) = Regex(pattern).find(RuralLanguageNormalizer.normalize(message))?.groupValues?.get(1)?.replace(',', '.')?.toBigDecimal()
            ?: throw IllegalArgumentException("Não encontrei todos os valores do cenário.")
        val purchasedFeedKg = number("comprei\\s+(\\d+(?:[,.]\\d+)?)\\s*kg\\s+de\\s+racao")
        val purchasedFeedTotal = number("racao.*?por\\s+(\\d+(?:[,.]\\d+)?)\\s+reais")
        val trays = number("comprei\\s+(\\d+(?:[,.]\\d+)?)\\s*bandejas")
        val traysTotal = number("bandejas.*?por\\s+(\\d+(?:[,.]\\d+)?)\\s+reais")
        val eggs = number("produzi\\s+(\\d+)\\s*ovos")
        val consumedFeed = number("usei\\s+(\\d+(?:[,.]\\d+)?)\\s*kg\\s+de\\s+racao")
        val soldTrays = number("vendi\\s+(\\d+(?:[,.]\\d+)?)\\s*bandejas")
        val saleUnitPrice = number("vendi.*?por\\s+(\\d+(?:[,.]\\d+)?)\\s+reais")
        val fuel = number("gastei\\s+(\\d+(?:[,.]\\d+)?)\\s+reais\\s+de\\s+combustivel")
        val actions = listOf(
            RuralAction.Purchase(scope, "Ração de aves", purchasedFeedKg.toPlainString(), "kg", (purchasedFeedTotal / purchasedFeedKg).toPlainString(), "RACAO"),
            RuralAction.Purchase(scope, "Bandejas vazias", trays.toPlainString(), "unidades", (traysTotal / trays).toPlainString(), "OUTRA_DESPESA"),
            RuralAction.EggProduction(scope, eggs.toPlainString()), RuralAction.StockConsumption(scope, "Ração de aves", consumedFeed.toPlainString(), "kg"),
            RuralAction.Sale(scope, "Bandejas de ovos", soldTrays.toPlainString(), "unidades", saleUnitPrice.toPlainString(), (soldTrays * saleUnitPrice).toPlainString()),
            RuralAction.Expense(scope, fuel.toPlainString(), "ENERGIA_COMBUSTIVEL", "Combustível")
        )
        OperationalProposal(actions, "1. Compra de $purchasedFeedKg kg de ração: R$ $purchasedFeedTotal.\n2. Compra de $trays bandejas: R$ $traysTotal.\n3. Produção de $eggs ovos.\n4. Consumo de $consumedFeed kg de ração.\n5. Venda de $soldTrays bandejas: R$ ${soldTrays * saleUnitPrice}.\n6. Despesa de combustível: R$ $fuel.")
    }.getOrNull()

    fun applyOperationalProposal(proposal: OperationalProposal): String {
        val failed = proposal.actions.map(RuralActionExecutor(context)::executeConfirmed).filterIsInstance<ActionExecution.Rejected>()
        return if (failed.isEmpty()) "Proposta aplicada: compras, produção, consumo, venda e despesa foram registrados." else "A proposta não foi aplicada por completo: ${failed.first().reason}"
    }

    fun applyOperationalTest(scope: FarmScope = farmContext.current()): String {
        val actions = listOf(
            RuralAction.Purchase(scope, "Ração de aves", "200", "kg", "2.50", "RACAO"),
            RuralAction.Purchase(scope, "Bandejas vazias", "300", "unidades", "0.50", "OUTRA_DESPESA"),
            RuralAction.EggProduction(scope, "900"),
            RuralAction.StockConsumption(scope, "Ração de aves", "30", "kg"),
            RuralAction.Sale(scope, "Bandejas de ovos", "50", "unidades", "10", "500"),
            RuralAction.Expense(scope, "80", "ENERGIA_COMBUSTIVEL", "Combustível do teste integrado")
        )
        val executor = RuralActionExecutor(context)
        val failed = actions.map(executor::executeConfirmed).filterIsInstance<ActionExecution.Rejected>()
        return if (failed.isEmpty()) "Teste integrado aplicado: compras, produção, consumo de ração, venda e despesa foram registrados." else "O teste não foi aplicado por completo: ${failed.first().reason}"
    }

    private fun AiDraft.toRuralAction(scope: FarmScope): RuralAction? = when (action) {
        RuralActionType.REGISTRAR_OVOS -> RuralAction.EggProduction(scope, parameters.getValue("ovos"))
        RuralActionType.REGISTRAR_LEITE -> RuralAction.MilkProduction(scope, parameters.getValue("litros"))
        RuralActionType.REGISTRAR_COMPRA_ESTOQUE -> RuralAction.Purchase(
            scope, parameters.getValue("produto"), parameters.getValue("quantidade"), parameters.getValue("unidade"),
            parameters.getValue("precoUnitario"), "OUTRA_DESPESA"
        )
        RuralActionType.REGISTRAR_VENDA_ESTOQUE -> RuralAction.Sale(
            scope, parameters.getValue("produto"), parameters.getValue("quantidade"), parameters.getValue("unidade"),
            parameters.getValue("precoUnitario"), parameters.getValue("valorRecebido")
        )
        else -> null
    }
}

data class OperationalProposal(val actions: List<RuralAction>, val summary: String)

/** Keeps the secretary's open question locally. It never creates an operational record by itself. */
private class SecretaryConversationStore(context: Context) {
    private val preferences = context.getSharedPreferences("rural_secretary", Context.MODE_PRIVATE)

    fun save(draft: AiDraft, scope: FarmScope) {
        preferences.edit().putString(scope.key(), JSONObject().apply {
            put("action", draft.action.name); put("parameters", JSONObject(draft.parameters)); put("summary", draft.summary); put("requiresConfirmation", draft.requiresConfirmation)
        }.toString()).apply()
    }

    fun pending(scope: FarmScope): AiDraft? = preferences.getString(scope.key(), null)?.let { raw -> runCatching {
        val json = JSONObject(raw); val values = json.optJSONObject("parameters") ?: JSONObject()
        val parameters = buildMap { values.keys().forEach { key -> put(key, values.optString(key)) } }
        AiDraft(RuralActionType.valueOf(json.getString("action")), parameters, json.getString("summary"), json.optBoolean("requiresConfirmation"))
    }.getOrNull() }

    fun clear(scope: FarmScope) { preferences.edit().remove(scope.key()).apply() }
    private fun FarmScope.key() = "pending_${organizationId}_${farmId}_${unitId.orEmpty()}"
}

/**
 * Normaliza escrita informal sem adivinhar valores. O usuário ainda vê e confirma o rascunho.
 */
internal object RuralLanguageNormalizer {
    private val replacements = linkedMapOf(
        "\\bovi?s\\b" to "ovos",
        "\\bovo\\b" to "ovos",
        "\\brassao\\b|\\bracao\\b|\\braso\\b" to "racao",
        "\\bleiti\\b|\\bleiteh\\b" to "leite",
        "\\bletero\\b|\\bleitera\\b" to "leite",
        "\\bdespeza\\b|\\bdispesa\\b" to "despesa",
        "\\bresumo\\b|\\bgeral\\b|\\bcomo ta\\b|\\bcomo esta\\b" to "resumo",
        "\\bbotei\\b|\\bcoloquei\\b|\\bdeu\\b|\\bdei\\b" to "registrei",
        "\\bquilos?\\b|\\bquilo\\b" to "kg",
        "\\blitros?\\b" to "litros"
    )

    private val numberWords = linkedMapOf(
        "novecentos" to "900", "oitocentos" to "800", "setecentos" to "700", "seiscentos" to "600",
        "quinhentos" to "500", "quatrocentos" to "400", "trezentos" to "300", "duzentos" to "200",
        "cento e noventa" to "190", "cento e oitenta" to "180", "cento e setenta" to "170",
        "cento e sessenta" to "160", "cento e cinquenta" to "150", "cento e quarenta" to "140",
        "cento e trinta" to "130", "cento e vinte" to "120", "cento e dez" to "110",
        "noventa" to "90", "oitenta" to "80", "setenta" to "70", "sessenta" to "60",
        "zero" to "0", "um" to "1", "uma" to "1", "dois" to "2", "duas" to "2",
        "dezenove" to "19", "dezoito" to "18", "dezessete" to "17", "dezesseis" to "16",
        "quinze" to "15", "quatorze" to "14", "treze" to "13", "doze" to "12", "onze" to "11",
        "tres" to "3", "quatro" to "4", "cinco" to "5", "seis" to "6", "sete" to "7",
        "oito" to "8", "nove" to "9", "dez" to "10", "vinte" to "20", "trinta" to "30",
        "quarenta" to "40", "cinquenta" to "50", "cem" to "100", "cento" to "100"
    )

    fun normalize(message: String): String {
        var normalized = Normalizer.normalize(message.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9,. ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        replacements.forEach { (pattern, replacement) ->
            normalized = normalized.replace(Regex(pattern), replacement)
        }
        numberWords.entries.sortedByDescending { it.key.length }.forEach { (word, digit) ->
            normalized = normalized.replace(Regex("\\b$word\\b"), digit)
        }
        return normalized
    }
}

internal class DeterministicCommandParser {
    fun continueSale(pending: AiDraft?, message: String): AiDraft? {
        if (pending?.action != RuralActionType.INFORMAR_VENCIMENTO_VENDA) return null
        val due = parseDueDate(message) ?: return AiDraft(RuralActionType.INFORMAR_VENCIMENTO_VENDA, pending.parameters, "Qual é a data combinada para o restante? Diga, por exemplo, ‘dia 20/08’.", false)
        val parameters = pending.parameters + mapOf("vencimento" to due.toString())
        return saleConfirmation(parameters)
    }

    fun continuePurchase(pending: AiDraft?, message: String): AiDraft? {
        if (pending?.action != RuralActionType.INFORMAR_PRECO_COMPRA) return null
        val amount = Regex("(\\d+(?:[,.]\\d+)?)").find(message)?.groupValues?.get(1)?.replace(',', '.')?.toBigDecimalOrNull()
            ?: return AiDraft(RuralActionType.INFORMAR_PRECO_COMPRA, pending.parameters, "Não consegui identificar o valor. Informe, por exemplo: ‘R$ 50 por lote’ ou ‘R$ 150 no total’.", false)
        if (amount <= java.math.BigDecimal.ZERO) return AiDraft(RuralActionType.INFORMAR_PRECO_COMPRA, pending.parameters, "O valor precisa ser maior que zero. Informe o preço por lote ou o total da compra.", false)
        val quantity = pending.parameters.getValue("quantidade").toBigDecimal()
        val lots = pending.parameters.getValue("lotes").toBigDecimal()
        val pricePerUnit = when {
            message.contains("total") -> amount.divide(quantity, 4, java.math.RoundingMode.HALF_UP)
            message.contains("lote") -> amount.divide(pending.parameters.getValue("unidadesPorLote").toBigDecimal(), 4, java.math.RoundingMode.HALF_UP)
            else -> amount
        }
        val total = pricePerUnit * quantity
        val parameters = pending.parameters + mapOf("precoUnitario" to pricePerUnit.stripTrailingZeros().toPlainString(), "valorTotal" to total.stripTrailingZeros().toPlainString())
        return AiDraft(RuralActionType.REGISTRAR_COMPRA_ESTOQUE, parameters, "Preparei a compra de ${parameters.getValue("quantidade")} ${parameters.getValue("unidade")} de ${parameters.getValue("produto")} por R$ ${parameters.getValue("precoUnitario")} cada (total R$ ${parameters.getValue("valorTotal")}). Confirme para atualizar estoque e caixa.")
    }

    fun parse(message: String): AiDraft {
        val normalized = message.lowercase()
        val history = parseHistory(normalized)
        val agenda = parseAgenda(normalized)
        val sale = parseSale(normalized)
        val purchase = parsePurchase(normalized)
        val eggs = calculateEggs(normalized)
        val milk = calculateMilk(normalized)
        val vaccine = parseVaccine(normalized)
        val feed = if (normalized.contains("racao")) {
            Regex("(\\d+(?:[,.]\\d+)?)\\s*(?:kg|quilos?)").find(normalized)?.groupValues?.get(1)
        } else null

        return when {
            history != null -> history
            agenda != null -> agenda
            vaccine != null -> vaccine
            sale != null -> sale
            purchase != null -> purchase
            eggs != null -> {
                val recordedAt = LocalDateTime.now()
                val fullTrays = eggs.net / EGGS_PER_TRAY
                val remainingEggs = eggs.net % EGGS_PER_TRAY
                val parameters = buildMap {
                    put("ovos", eggs.net.toString())
                    put("data", recordedAt.toLocalDate().toString())
                    put("hora", recordedAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                    put("bandejasEquivalentes", fullTrays.toString())
                    remainingEggs.takeIf { it > 0 }?.let { put("ovosAvulsos", it.toString()) }
                    put("ovosBrutos", eggs.gross.toString())
                    eggs.trays.takeIf { it > 0 }?.let { put("bandejas", it.toString()) }
                    eggs.lots.takeIf { it > 0 }?.let { put("lotes", it.toString()) }
                    eggs.discarded.takeIf { it > 0 }?.let { put("ovosDescartados", it.toString()) }
                    feed?.let { put("racaoKg", it) }
                }
                val eggDescription = eggs.description()
                val traysDescription = if (remainingEggs == 0) "$fullTrays bandejas de 30 ovos" else "$fullTrays bandejas e $remainingEggs ovos avulsos"
                val summary = if (feed != null) {
                    "Preparei o depósito de $eggDescription, equivalente a $traysDescription, em ${parameters.getValue("data")} às ${parameters.getValue("hora")}, e $feed kg de ração."
                } else "Preparei o depósito de $eggDescription, equivalente a $traysDescription, em ${parameters.getValue("data")} às ${parameters.getValue("hora")}."
                AiDraft(RuralActionType.REGISTRAR_OVOS, parameters, summary)
            }
            milk != null -> {
                val shift = when { normalized.contains("manha") -> MilkShift.MANHA; normalized.contains("tarde") -> MilkShift.TARDE; normalized.contains("noite") -> MilkShift.NOITE; else -> MilkShift.NAO_INFORMADO }
                AiDraft(RuralActionType.REGISTRAR_LEITE, mapOf("litros" to milk.total.toString(), "data" to "hoje", "tambores" to milk.drums.toString(), "litrosPorTambor" to milk.capacity.toString(), "turno" to shift.name), "Preparei o registro de ${milk.description()} de leite para hoje, turno ${shift.name.lowercase()}.")
            }
            feed != null -> AiDraft(RuralActionType.REGISTRAR_RACAO, mapOf("racaoKg" to feed, "data" to "hoje"), "Preparei o registro de $feed kg de ração para hoje.")
            normalized.contains("despesa") || normalized.contains("gastei") -> AiDraft(RuralActionType.REGISTRAR_DESPESA, emptyMap(), "Preparei uma nova despesa. Confira os dados antes de confirmar.")
            normalized.contains("resumo") -> AiDraft(RuralActionType.CONSULTAR_RESUMO, emptyMap(), "Consultando o resumo local.", false)
            else -> AiDraft(RuralActionType.DESCONHECIDA, emptyMap(), "Não reconheci um registro seguro.", false)
        }
    }

    private fun parseHistory(message: String): AiDraft? {
        if (!(message.contains("o que foi feito") || message.contains("historico") || message.contains("aconteceu"))) return null
        val date = parseNaturalDate(message) ?: return AiDraft(RuralActionType.CONSULTAR_HISTORICO, emptyMap(), "Qual dia você quer consultar? Diga, por exemplo, 15 de julho.", false)
        return AiDraft(RuralActionType.CONSULTAR_HISTORICO, mapOf("data" to date.toString()), "Consultando os registros de $date.", false)
    }

    private fun parseAgenda(message: String): AiDraft? {
        val isRequest = listOf("agendar", "lembre", "marcar", "manutencao", "comprar ", "visita").any(message::contains)
        if (!isRequest || message.startsWith("comprei")) return null
        val date = parseNaturalDate(message) ?: return AiDraft(RuralActionType.DESCONHECIDA, emptyMap(), "Para agendar, informe a data. Exemplo: agendar manutenção do trator dia 15/07.", false)
        val type = when {
            message.contains("comprar") || message.contains("compra") -> AgendaType.ESTOQUE
            message.contains("veterin") || message.contains("vacina") || message.contains("saude") -> AgendaType.SAUDE
            message.contains("manutencao") || message.contains("trator") || message.contains("cerca") -> AgendaType.MANEJO
            else -> AgendaType.OUTRO
        }
        val title = message.replace(Regex("(?:agendar|lembre|marcar).*?(?:dia\\s*\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?|\\d{1,2}\\s+de\\s+[a-zç]+).*"), "").trim()
            .ifBlank { message.removePrefix("agendar ").removePrefix("marcar ").trim() }
        return AiDraft(RuralActionType.REGISTRAR_AGENDA, mapOf("titulo" to title.replaceFirstChar { it.uppercase() }, "data" to date.toString(), "tipo" to type.name), "Preparei o agendamento: ${title.replaceFirstChar { it.uppercase() }} em $date. Confirme para colocar na agenda.")
    }

    private fun parseVaccine(message: String): AiDraft? {
        if (!(message.contains("vacinei") || message.contains("apliquei vacina"))) return null
        val species = when {
            message.contains("vaca") || message.contains("boi") || message.contains("bovino") -> AnimalSpecies.BOVINO
            message.contains("porco") || message.contains("matriz") || message.contains("suino") -> AnimalSpecies.SUINO
            message.contains("galinha") || message.contains("ave") || message.contains("lote") -> AnimalSpecies.AVE
            else -> return null
        }
        val target = Regex("(?:vaca|boi|bovino|porco|matriz|suino|galinha|ave|lote)\\s+([a-z0-9-]+)").find(message)?.groupValues?.get(1)
            ?.replaceFirstChar { it.uppercase() } ?: return null
        val vaccine = Regex("(?:de|contra)\\s+([a-z0-9 ]+)").find(message)?.groupValues?.get(1)?.trim()?.ifBlank { null } ?: "Vacina informada"
        return AiDraft(RuralActionType.REGISTRAR_VACINA, mapOf("especie" to species.name, "identificacao" to target, "vacina" to vaccine), "Preparei o histórico: $target recebeu vacina de $vaccine hoje. Confirme para registrar no histórico do animal ou lote.")
    }

    private fun parseSale(message: String): AiDraft? {
        if (!(message.contains("vendi") || message.contains("vende"))) return null
        val match = Regex("(\\d+)\\s*(bandejas?|cartelas?)\\s*(?:de\\s*)?(?:ovos?)?\\s*(?:a|por)\\s*(\\d+(?:[,.]\\d+)?)").find(message) ?: return null
        val quantity = match.groupValues[1].toBigDecimal()
        val product = if (match.groupValues[2].startsWith("cartel")) "Cartelas de ovos" else "Bandejas de ovos"
        val unitPrice = match.groupValues[3].replace(',', '.').toBigDecimal()
        val customer = Regex("(?:a|para)\\s+([a-z]+)").find(message.substring(match.range.last + 1))?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() } ?: "Cliente não informado"
        val total = quantity * unitPrice
        val paid = when {
            message.contains("metade") -> total.divide(java.math.BigDecimal(2))
            else -> total
        }
        val base = mapOf("produto" to product, "quantidade" to quantity.stripTrailingZeros().toPlainString(), "unidade" to "unidades", "precoUnitario" to unitPrice.stripTrailingZeros().toPlainString(), "cliente" to customer, "valorTotal" to total.stripTrailingZeros().toPlainString(), "valorRecebido" to paid.stripTrailingZeros().toPlainString(), "saldoAberto" to (total - paid).stripTrailingZeros().toPlainString())
        return if (total > paid) {
            parseDueDate(message)?.let { saleConfirmation(base + ("vencimento" to it.toString())) }
                ?: AiDraft(RuralActionType.INFORMAR_VENCIMENTO_VENDA, base, "Entendi a venda de $quantity $product por R$ $unitPrice cada para $customer. Total R$ $total; entrou R$ $paid e ficaram R$ ${total - paid} em aberto. Para qual dia ele combinar pagar o restante?", false)
        } else saleConfirmation(base + ("vencimento" to ""))
    }

    private fun saleConfirmation(parameters: Map<String, String>) = AiDraft(RuralActionType.REGISTRAR_VENDA_ESTOQUE, parameters, buildString {
        append("Preparei a venda de ${parameters.getValue("quantidade")} ${parameters.getValue("produto")}. Vou baixar do estoque e lançar R$ ${parameters.getValue("valorRecebido")} no caixa.")
        parameters.getValue("saldoAberto").toBigDecimal().takeIf { it > java.math.BigDecimal.ZERO }?.let { append(" Também vou deixar R$ $it em contas a receber de ${parameters.getValue("cliente")} para ${parameters.getValue("vencimento")}.") }
        append(" Confirme para salvar tudo junto.")
    })

    private fun parseDueDate(message: String): LocalDate? {
        if (message.contains("hoje")) return LocalDate.now()
        val match = Regex("(?:dia\\s*)?(\\d{1,2})(?:[/-]|\\s+)(\\d{1,2})(?:[/-]|\\s+)?(\\d{2,4})?").find(message) ?: return null
        val day = match.groupValues[1].toIntOrNull() ?: return null; val month = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues[3].toIntOrNull()?.let { if (it < 100) it + 2000 else it } ?: LocalDate.now().year
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun parseNaturalDate(message: String): LocalDate? {
        parseDueDate(message)?.let { return it }
        if (message.contains("hoje")) return LocalDate.now()
        val named = Regex("(\\d{1,2})\\s+de\\s+(janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)(?:\\s+de\\s+(\\d{4}))?").find(message) ?: return null
        val month = listOf("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro").indexOf(named.groupValues[2]) + 1
        val year = named.groupValues[3].toIntOrNull() ?: LocalDate.now().year
        return runCatching { LocalDate.of(year, month, named.groupValues[1].toInt()) }.getOrNull()
    }

    private fun parsePurchase(message: String): AiDraft? {
        if (!(message.contains("comprei") || message.contains("compramos") || message.contains("adquiri"))) return null
        val match = Regex("(\\d+)\\s*lotes?\\s*(?:de\\s*)?(bandejas?|cartelas?)\\s*(?:com\\s*)?(\\d+)\\s*(?:unidades?)").find(message) ?: return null
        val lots = match.groupValues[1].toIntOrNull() ?: return null
        val product = if (match.groupValues[2].startsWith("cartel")) "Cartelas" else "Bandejas"
        val unitsPerLot = match.groupValues[3].toIntOrNull() ?: return null
        val quantity = lots * unitsPerLot
        val parameters = mapOf("produto" to product, "quantidade" to quantity.toString(), "unidade" to "unidades", "lotes" to lots.toString(), "unidadesPorLote" to unitsPerLot.toString())
        return AiDraft(RuralActionType.INFORMAR_PRECO_COMPRA, parameters, "Entendi $lots lotes de $unitsPerLot $product cada: $quantity unidades. Quanto custou cada lote ou qual foi o valor total da compra?", false)
    }

    private fun calculateEggs(message: String): EggTotal? {
        if (listOf("ovo", "bandeja", "apanhei", "coletei").none(message::contains)) return null
        val lotPattern = Regex("(\\d+)\\s*lotes?\\s*(?:de|com)?\\s*(\\d+)(?:\\s*bandejas?)?")
        var lots = 0
        var trays = 0
        lotPattern.findAll(message).forEach { match ->
            val lotCount = match.groupValues[1].toIntOrNull() ?: 0
            val traysPerLot = match.groupValues[2].toIntOrNull() ?: 0
            lots += lotCount
            trays += lotCount * traysPerLot
        }
        val withoutLots = message.replace(lotPattern, " ")
        trays += Regex("(\\d+)\\s*bandejas?").findAll(withoutLots).sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        val loose = Regex("(\\d+)\\s*ovos?").findAll(message).sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        val discarded = Regex("(\\d+)\\s*(?:ovos?\\s*)?(?:foram\\s*)?(?:quebrados?|descartados?|perdidos?)")
            .findAll(message).sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        val gross = trays * EGGS_PER_TRAY + loose
        return gross.takeIf { it > 0 }?.let { EggTotal(lots, trays, loose, gross, discarded, (gross - discarded).coerceAtLeast(0)) }
    }

    private fun calculateMilk(message: String): MilkTotal? {
        val drumPattern = Regex("(\\d+)\\s*tambores?(?:\\s*(?:de|com)\\s*(\\d+)\\s*litros?)?")
        var drums = 0
        var capacity = DEFAULT_DRUM_LITERS
        drumPattern.findAll(message).forEach { match ->
            drums += match.groupValues[1].toIntOrNull() ?: 0
            match.groupValues[2].toIntOrNull()?.let { capacity = it }
        }
        val loose = Regex("(\\d+)\\s*(?:l|litros?)").findAll(message.replace(drumPattern, " "))
            .sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        val total = drums * capacity + loose
        return total.takeIf { it > 0 && (drums > 0 || message.contains("leite")) }?.let { MilkTotal(drums, capacity, loose, total) }
    }

    private data class EggTotal(val lots: Int, val trays: Int, val loose: Int, val gross: Int, val discarded: Int, val net: Int) {
        fun description(): String = buildList {
            lots.takeIf { it > 0 }?.let { add("$it lotes") }
            trays.takeIf { it > 0 }?.let { add("$it bandejas (${it * EGGS_PER_TRAY} ovos)") }
            loose.takeIf { it > 0 }?.let { add("$it ovos avulsos") }
        }.joinToString(" + ") + if (discarded > 0) " − $discarded descartados = $net ovos líquidos" else " = $net ovos"
    }

    private data class MilkTotal(val drums: Int, val capacity: Int, val loose: Int, val total: Int) {
        fun description(): String = buildList {
            drums.takeIf { it > 0 }?.let { add("$it tambores de $capacity L (${it * capacity} L)") }
            loose.takeIf { it > 0 }?.let { add("$it L avulsos") }
        }.joinToString(" + ") + " = $total L"
    }

    private companion object {
        const val EGGS_PER_TRAY = 30
        const val DEFAULT_DRUM_LITERS = 50
    }
}

private class LocalEventStore(context: Context) {
    private val preferences = context.getSharedPreferences("rural_events", Context.MODE_PRIVATE)

    fun record(draft: AiDraft, scope: FarmScope) {
        val events = JSONArray(preferences.getString("events", "[]"))
        events.put(JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("organizationId", scope.organizationId)
            put("farmId", scope.farmId)
            put("unitId", scope.unitId)
            put("action", draft.action.name)
            put("parameters", JSONObject(draft.parameters))
            put("summary", draft.summary)
            put("recordedAt", java.time.LocalDateTime.now().toString())
        })
        preferences.edit().putString("events", events.toString()).apply()
    }

    fun summary(scope: FarmScope): String {
        val events = JSONArray(preferences.getString("events", "[]"))
        val scopedCount = (0 until events.length()).count { index ->
            val event = events.getJSONObject(index)
            event.optString("organizationId") == scope.organizationId &&
                event.optString("farmId") == scope.farmId &&
                event.optString("unitId").ifBlank { null } == scope.unitId
        }
        return if (scopedCount == 0) {
            "Ainda não há registros confirmados nesta seleção."
        } else {
            "Você tem $scopedCount registro(s) confirmado(s) nesta seleção."
        }
    }

    fun history(scope: FarmScope, date: LocalDate): String {
        val events = JSONArray(preferences.getString("events", "[]"))
        val items = (0 until events.length()).map(events::getJSONObject).filter { event ->
            event.optString("organizationId") == scope.organizationId && event.optString("farmId") == scope.farmId && event.optString("unitId").ifBlank { null } == scope.unitId && event.optString("recordedAt").take(10) == date.toString()
        }
        return if (items.isEmpty()) "Não encontrei lançamentos confirmados em $date nesta propriedade." else buildString { append("Em $date foram feitos ${items.size} registro(s):"); items.forEach { append("\n• ").append(it.optString("summary")) } }
    }

    fun eggsTotal(scope: FarmScope): Int {
        val events = JSONArray(preferences.getString("events", "[]"))
        return (0 until events.length()).sumOf { index ->
            val event = events.getJSONObject(index)
            if (
                event.optString("organizationId") == scope.organizationId &&
                event.optString("farmId") == scope.farmId &&
                event.optString("unitId").ifBlank { null } == scope.unitId &&
                event.optString("action") == RuralActionType.REGISTRAR_OVOS.name
            ) {
                event.optJSONObject("parameters")?.optString("ovos")?.toIntOrNull() ?: 0
            } else 0
        }
    }
}
