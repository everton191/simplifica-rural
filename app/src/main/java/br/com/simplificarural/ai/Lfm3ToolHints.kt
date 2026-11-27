package br.com.simplificarural.ai

import br.com.simplificarural.domain.actions.QuickCommand
import br.com.simplificarural.domain.actions.expressesFinance

internal object Lfm3ToolHints {
    private val topics = mapOf(
        "registrar_ovos" to Regex("""\b(?:ovos?|ninho)\b"""),
        "registrar_leite" to Regex("""\b(?:leite|ordenha|ordenhei|ordenhar)\b"""),
        "movimentar_estoque" to Regex("""\b(?:racao|estoque|deposito|dei baixa|de baixa|consumi|sa[ií]da|entrada fisica)\b"""),
        "registrar_vacina" to Regex("""\b(?:vacinei|vacina|vacinacao|aftosa|vermifugo)\b"""),
        "registrar_parto_suino" to Regex("""\b(?:pariu|leit[aã]o|leitoes|natimortos|matriz suina)\b""")
    )
    private val units = Regex("""\b(?:kg|l|litros|unidades?)\b""")
    private val missingAnimal = Regex("""\b(?:vaca|boi|bovino|porco|suino|ave|galinha|lote)\s+(?:contra|com)\b""")
    private val collection = Regex("""\b(?:apanhei|colhi|coletei|recolhi|produzi|coleta)\b""")
    private val incoming = Regex("""\b(?:chegaram|recebi|adicionei|registrei entrada|entrada fisica)\b""")
    private val outgoing = Regex("""\b(?:usei|consumi|retirei|de baixa|dei baixa|registrei baixa|registrei saida)\b""")

    fun validate(input: String, proposals: List<QuickCommand>): List<QuickCommand> {
        require(!expressesFinance(input)) { "Informe os dados de cada compra, venda ou gasto antes de confirmar." }
        val normalized = RuralLanguageNormalizer.normalize(input)
        val collected = proposals.map { command ->
            if (command is QuickCommand.Stock && command.incoming && command.unit == "unidades" &&
                RuralLanguageNormalizer.normalize(command.product) == "ovos" && collection.containsMatchIn(normalized)
            ) QuickCommand.Eggs(command.quantity.intValueExact()) else command
        }
        val singleStock = collected.count { it is QuickCommand.Stock } == 1
        val hasEntry = incoming.containsMatchIn(normalized)
        val hasExit = outgoing.containsMatchIn(normalized)
        val checked = collected.map { command ->
            if (command !is QuickCommand.Stock) return@map command
            val unit = when (command.unit) {
                "kg" -> Regex("""(?:\b|(?<=\d))kg\b""")
                "litros" -> Regex("""(?:\b|(?<=\d))(?:l|litros)\b""")
                else -> Regex("""\bunidades?\b""")
            }
            require(unit.containsMatchIn(normalized)) { "Qual é a unidade? Diga se foi em quilos, litros ou unidades." }
            require(!singleStock || !hasEntry || !hasExit) { "Você mencionou entrada e saída. Informe cada movimento separadamente." }
            if (singleStock && (hasEntry || hasExit)) command.copy(incoming = hasEntry) else command
        }
        require(checked.distinct().size == checked.size) { "O modelo repetiu um lançamento. Diga uma ação por vez." }
        return checked
    }

    fun tools(normalized: String): Set<String> = topics.filterValues { it.containsMatchIn(normalized) }.keys

    fun requestMissingData(normalized: String, tools: Set<String>): String? = when {
        tools == setOf("movimentar_estoque") && !units.containsMatchIn(normalized) ->
            "Qual é a unidade? Diga, por exemplo, 5 quilos ou 5 unidades."
        tools == setOf("registrar_vacina") && missingAnimal.containsMatchIn(normalized) ->
            "Qual é o nome ou o número do brinco do animal?"
        else -> null
    }
}
