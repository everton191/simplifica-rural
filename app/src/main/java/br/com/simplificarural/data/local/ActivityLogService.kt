package br.com.simplificarural.data.local

import android.content.Context
import br.com.simplificarural.domain.property.FarmScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.UUID

/** Persistent log for operational activities that do not alter stock or cash directly. */
data class ActivityLog(val id: String, val scope: FarmScope, val area: String, val description: String, val createdAt: LocalDateTime)

class ActivityLogService(context: Context) {
    private val prefs = context.getSharedPreferences("rural_activity_log", Context.MODE_PRIVATE)
    fun list(scope: FarmScope, area: String): List<ActivityLog> = array().map(::entry).filter { it.scope == scope && it.area == area }.sortedByDescending { it.createdAt }
    fun add(scope: FarmScope, area: String, description: String) {
        require(description.isNotBlank()) { "Descreva o registro." }
        val values = JSONArray(prefs.getString("entries", "[]"))
        values.put(JSONObject().apply { put("id", UUID.randomUUID().toString()); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("area", area); put("description", description.trim()); put("createdAt", LocalDateTime.now().toString()) })
        prefs.edit().putString("entries", values.toString()).apply()
    }
    private fun array(): List<JSONObject> = JSONArray(prefs.getString("entries", "[]")).let { json -> (0 until json.length()).map(json::getJSONObject) }
    private fun entry(json: JSONObject) = ActivityLog(json.getString("id"), FarmScope(json.getString("organizationId"), json.getString("farmId"), json.optString("unitId").ifBlank { null }), json.getString("area"), json.getString("description"), LocalDateTime.parse(json.getString("createdAt")))
}
