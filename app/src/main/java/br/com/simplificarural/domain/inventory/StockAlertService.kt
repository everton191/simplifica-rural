package br.com.simplificarural.domain.inventory

import android.content.Context
import br.com.simplificarural.domain.management.StockBalance
import br.com.simplificarural.domain.property.FarmScope
import org.json.JSONObject
import java.math.BigDecimal

data class StockAlert(val product: String, val unit: String, val available: BigDecimal, val minimum: BigDecimal)

/** Limits are local and scoped to the selected farm/unit; they never change the stock balance. */
class StockAlertService(context: Context) {
    private val prefs = context.getSharedPreferences("stock_alert_limits", Context.MODE_PRIVATE)

    fun minimum(scope: FarmScope, product: String, unit: String): BigDecimal = prefs.getString(key(scope, product, unit), null)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    fun setMinimum(scope: FarmScope, product: String, unit: String, value: BigDecimal) {
        require(value >= BigDecimal.ZERO) { "Estoque mínimo não pode ser negativo." }
        prefs.edit().putString(key(scope, product, unit), value.stripTrailingZeros().toPlainString()).apply()
    }
    fun alerts(scope: FarmScope, balances: List<StockBalance>): List<StockAlert> = balances.mapNotNull { balance ->
        val minimum = minimum(scope, balance.productName, balance.unit)
        StockAlert(balance.productName, balance.unit, balance.quantity, minimum).takeIf { minimum > BigDecimal.ZERO && balance.quantity <= minimum }
    }.sortedBy { it.available - it.minimum }

    private fun key(scope: FarmScope, product: String, unit: String) = listOf(scope.organizationId, scope.farmId, scope.unitId.orEmpty(), product.lowercase(), unit.lowercase()).joinToString("|")
}
