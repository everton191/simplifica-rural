package br.com.simplificarural.domain.actions

import br.com.simplificarural.domain.property.FarmScope

/** A parsed request is data only; the executor validates it before saving. */
sealed interface RuralAction {
    val scope: FarmScope
    data class Purchase(override val scope: FarmScope, val product: String, val quantity: String, val unit: String, val unitPrice: String, val category: String) : RuralAction
    data class Sale(override val scope: FarmScope, val product: String, val quantity: String, val unit: String, val unitPrice: String, val receivedAmount: String) : RuralAction
    data class StockConsumption(override val scope: FarmScope, val product: String, val quantity: String, val unit: String) : RuralAction
    data class EggProduction(override val scope: FarmScope, val quantity: String) : RuralAction
    data class MilkProduction(override val scope: FarmScope, val liters: String) : RuralAction
    data class Expense(override val scope: FarmScope, val amount: String, val category: String, val description: String) : RuralAction
}

sealed interface ActionExecution {
    data class ReadyForConfirmation(val summary: String) : ActionExecution
    data class Completed(val recordId: String, val summary: String) : ActionExecution
    data class Rejected(val reason: String) : ActionExecution
}
