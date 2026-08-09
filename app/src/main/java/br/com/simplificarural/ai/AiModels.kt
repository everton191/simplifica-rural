package br.com.simplificarural.ai

enum class RuralActionType {
    REGISTRAR_OVOS,
    REGISTRAR_LEITE,
    REGISTRAR_RACAO,
    REGISTRAR_DESPESA,
    INFORMAR_PRECO_COMPRA,
    REGISTRAR_COMPRA_ESTOQUE,
    INFORMAR_VENCIMENTO_VENDA,
    REGISTRAR_VENDA_ESTOQUE,
    REGISTRAR_VACINA,
    REGISTRAR_AGENDA,
    CONSULTAR_RESUMO,
    CONSULTAR_HISTORICO,
    DESCONHECIDA
}

data class AiDraft(
    val action: RuralActionType,
    val parameters: Map<String, String>,
    val summary: String,
    val requiresConfirmation: Boolean = action !in setOf(RuralActionType.CONSULTAR_RESUMO, RuralActionType.CONSULTAR_HISTORICO, RuralActionType.DESCONHECIDA)
)

sealed interface AssistantResult {
    data class Reply(val text: String, val draft: AiDraft? = null) : AssistantResult
    data class Failure(val text: String) : AssistantResult
}
