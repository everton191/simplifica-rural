package br.com.simplificarural.domain.financial

import android.content.Context
import br.com.simplificarural.domain.property.FarmScope
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class Receivable(val id: String, val scope: FarmScope, val customer: String, val amount: BigDecimal, val dueDate: LocalDate, val description: String, val settled: Boolean = false)

/** Open customer balances remain separate from cash until the payment is confirmed. */
class ReceivableService(context: Context) {
    private val preferences = context.getSharedPreferences("rural_receivables", Context.MODE_PRIVATE)
    fun create(scope: FarmScope, customer: String, amount: BigDecimal, dueDate: LocalDate, description: String) {
        require(amount > BigDecimal.ZERO)
        val values = JSONArray(preferences.getString("items", "[]"))
        values.put(JSONObject().apply { put("id", UUID.randomUUID().toString()); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("customer", customer); put("amount", amount.toPlainString()); put("dueDate", dueDate.toString()); put("description", description); put("settled", false) })
        preferences.edit().putString("items", values.toString()).apply()
    }
}
