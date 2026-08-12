package br.com.simplificarural.domain.orders

import android.content.Context
import br.com.simplificarural.domain.property.FarmScope
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.inventory.PackagingConversionService

enum class RuralOrderStatus { RASCUNHO, AGENDADO, PARCIAL, PRONTO_PARA_SEPARAR, ENTREGUE, CANCELADO }
data class RuralOrder(val id: String, val scope: FarmScope, val customer: String, val product: String, val quantity: BigDecimal, val unit: String, val unitPrice: BigDecimal, val total: BigDecimal, val status: RuralOrderStatus, val deliveryDate: LocalDate?, val note: String?)

/** Pedido não é venda: só vira caixa e baixa estoque quando for efetivamente concluído. */
class RuralOrderService(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("rural_orders", Context.MODE_PRIVATE)
    fun schedule(scope: FarmScope, customer: String, product: String, quantity: BigDecimal, unit: String, unitPrice: BigDecimal, deliveryDate: LocalDate, note: String): RuralOrder {
        require(quantity > BigDecimal.ZERO && unitPrice > BigDecimal.ZERO)
        val order = RuralOrder(UUID.randomUUID().toString(), scope, customer, product, quantity, unit, unitPrice, quantity * unitPrice, RuralOrderStatus.AGENDADO, deliveryDate, note)
        val values = JSONArray(prefs.getString("orders", "[]")); values.put(order.json()); prefs.edit().putString("orders", values.toString()).apply(); RuralOrderReviewScheduler.schedule(appContext); return order
    }
    fun list(scope: FarmScope): List<RuralOrder> = array().filter { it.scope == scope }.sortedBy { it.deliveryDate }
    fun reviewDueToday() { array().filter { it.status == RuralOrderStatus.AGENDADO && it.deliveryDate == LocalDate.now() }.forEach(::review) }
    fun review(order: RuralOrder): RuralOrder {
        if (!order.product.contains("bandeja", true) && !order.product.contains("cartela", true)) return order
        val stock = FarmManagementService(appContext).stock(CashViewScope.SelectedUnit(order.scope))
        val eggs = stock.firstOrNull { it.productName.equals("ovos", true) }?.quantity ?: BigDecimal.ZERO
        val packs = stock.filter { it.productName.contains("bandeja", true) || it.productName.contains("cartela", true) }.fold(BigDecimal.ZERO) { sum, item -> sum + item.quantity }
        val eggsPerPack = PackagingConversionService(appContext).eggsPerPackage(order.scope, order.product)
        val available = minOf(eggs.divide(BigDecimal(eggsPerPack), 0, java.math.RoundingMode.DOWN), packs)
        return update(order, if (available >= order.quantity) RuralOrderStatus.PRONTO_PARA_SEPARAR else RuralOrderStatus.AGENDADO, "Disponível para ${available.stripTrailingZeros().toPlainString()} ${order.unit}.")
    }
    fun deliverPartial(order: RuralOrder, delivered: BigDecimal): RuralOrder {
        require(delivered > BigDecimal.ZERO && delivered < order.quantity) { "A entrega parcial deve ser maior que zero e menor que o pedido." }
        registerDelivery(order, delivered)
        update(order, RuralOrderStatus.PARCIAL, "Entregues ${delivered.stripTrailingZeros().toPlainString()} ${order.unit}; saldo pendente ${order.quantity.subtract(delivered).stripTrailingZeros().toPlainString()}.")
        return schedule(order.scope, order.customer, order.product, order.quantity.subtract(delivered), order.unit, order.unitPrice, order.deliveryDate ?: LocalDate.now(), "Saldo pendente do pedido ${order.id}.")
    }
    fun delivered(order: RuralOrder): RuralOrder { registerDelivery(order, order.quantity); return update(order, RuralOrderStatus.ENTREGUE, "Pedido entregue e lançado no caixa.") }
    private fun registerDelivery(order: RuralOrder, quantity: BigDecimal) { val management = FarmManagementService(appContext); if (order.product.contains("bandeja", true) || order.product.contains("cartela", true)) management.registerEggTraySale(order.scope, order.product, quantity, order.unit, order.unitPrice, receivedAmount = quantity * order.unitPrice) else management.registerSale(order.scope, order.product, quantity, order.unit, order.unitPrice, receivedAmount = quantity * order.unitPrice) }
    private fun update(order: RuralOrder, status: RuralOrderStatus, note: String): RuralOrder { val updated = order.copy(status = status, note = note); val values = JSONArray(prefs.getString("orders", "[]")); for (i in 0 until values.length()) if (values.getJSONObject(i).getString("id") == order.id) values.put(i, updated.json()); prefs.edit().putString("orders", values.toString()).apply(); return updated }
    private fun array(): List<RuralOrder> = JSONArray(prefs.getString("orders", "[]")).let { values -> (0 until values.length()).map { i -> values.getJSONObject(i).let { j -> RuralOrder(j.getString("id"), FarmScope(j.getString("organizationId"), j.getString("farmId"), j.optString("unitId").ifBlank { null }), j.getString("customer"), j.getString("product"), j.getString("quantity").toBigDecimal(), j.getString("unit"), j.getString("unitPrice").toBigDecimal(), j.getString("total").toBigDecimal(), RuralOrderStatus.valueOf(j.getString("status")), j.optString("deliveryDate").ifBlank { null }?.let(LocalDate::parse), j.optString("note").ifBlank { null }) } } }
    private fun RuralOrder.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("customer", customer); put("product", product); put("quantity", quantity.toPlainString()); put("unit", unit); put("unitPrice", unitPrice.toPlainString()); put("total", total.toPlainString()); put("status", status.name); put("deliveryDate", deliveryDate?.toString()); put("note", note) }
}
