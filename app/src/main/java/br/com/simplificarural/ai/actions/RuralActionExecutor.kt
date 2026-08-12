package br.com.simplificarural.ai.actions

import android.content.Context
import br.com.simplificarural.domain.actions.ActionExecution
import br.com.simplificarural.domain.actions.RuralAction
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.management.FinancialCategory

/** The only bridge allowed to turn a confirmed AI command into a local record. */
class RuralActionExecutor(context: Context) {
    private val management = FarmManagementService(context)

    fun preview(action: RuralAction): ActionExecution = runCatching {
        ActionExecution.ReadyForConfirmation(summary(action))
    }.getOrElse { ActionExecution.Rejected(it.message ?: "Dados inválidos.") }

    fun executeConfirmed(action: RuralAction): ActionExecution = runCatching {
        val record = when (action) {
            is RuralAction.Purchase -> management.registerPurchase(action.scope, action.product, action.quantity.decimal(), action.unit, action.unitPrice.decimal(), action.category.category())
            is RuralAction.Sale -> {
                if (action.product.contains("bandeja", true) || action.product.contains("cartela", true)) management.registerEggTraySale(action.scope, action.product, action.quantity.decimal(), action.unit, action.unitPrice.decimal(), receivedAmount = action.receivedAmount.decimal())
                else management.registerSale(action.scope, action.product, action.quantity.decimal(), action.unit, action.unitPrice.decimal(), receivedAmount = action.receivedAmount.decimal())
            }
            is RuralAction.StockConsumption -> management.registerStockConsumption(action.scope, action.product, action.quantity.decimal(), action.unit)
            is RuralAction.EggProduction -> management.registerEggProduction(action.scope, action.quantity.toInt())
            is RuralAction.MilkProduction -> management.registerMilkProduction(action.scope, action.liters.decimal())
            is RuralAction.Expense -> management.registerExpense(action.scope, action.category.category(), action.amount.decimal(), description = action.description)
        }
        ActionExecution.Completed(record.id, summary(action))
    }.getOrElse { ActionExecution.Rejected(it.message ?: "Não foi possível registrar a ação.") }

    private fun summary(action: RuralAction): String = when (action) {
        is RuralAction.Purchase -> "Compra: ${action.quantity} ${action.unit} de ${action.product} por R$ ${action.unitPrice} cada."
        is RuralAction.Sale -> "Venda: ${action.quantity} ${action.unit} de ${action.product} por R$ ${action.unitPrice} cada; recebido R$ ${action.receivedAmount}."
        is RuralAction.StockConsumption -> "Consumo: ${action.quantity} ${action.unit} de ${action.product}."
        is RuralAction.EggProduction -> "Produção: ${action.quantity} ovos."
        is RuralAction.MilkProduction -> "Produção: ${action.liters} litros de leite."
        is RuralAction.Expense -> "Despesa: ${action.description}, R$ ${action.amount}."
    }

    private fun String.decimal() = replace(',', '.').toBigDecimal()
    private fun String.category() = FinancialCategory.entries.firstOrNull { it.name == uppercase() }
        ?: throw IllegalArgumentException("Categoria financeira inválida.")
}
