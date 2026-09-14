package br.com.simplificarural.ui.assistant

import br.com.simplificarural.ai.AiDraft
import br.com.simplificarural.ai.RuralActionType
import br.com.simplificarural.navigation.RuralRoutes

internal fun AiDraft.destination(): String = when (action) {
    RuralActionType.REGISTRAR_OVOS -> RuralRoutes.STOCK
    RuralActionType.REGISTRAR_LEITE -> RuralRoutes.CATTLE_MILK
    RuralActionType.REGISTRAR_COMPRA_ESTOQUE -> RuralRoutes.NEW_PURCHASE
    RuralActionType.REGISTRAR_VENDA_ESTOQUE -> RuralRoutes.SALES
    RuralActionType.REGISTRAR_VACINA -> RuralRoutes.HEALTH
    RuralActionType.REGISTRAR_PARTO_BOVINO -> RuralRoutes.CATTLE_REPRODUCTION
    RuralActionType.REGISTRAR_AGENDA -> RuralRoutes.AGENDA
    RuralActionType.REGISTRAR_RACAO -> RuralRoutes.STOCK
    RuralActionType.REGISTRAR_DESPESA -> RuralRoutes.FINANCE
    else -> RuralRoutes.ASSISTANT
}
