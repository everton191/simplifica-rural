package br.com.simplificarural.domain.property

import android.content.Context

/** Persists only the currently selected Fazenda/Granja. The catalog will move to Room later. */
class FarmContextStore(context: Context) {
    private val preferences = context.getSharedPreferences("active_farm_context", Context.MODE_PRIVATE)

    fun current(): FarmScope = FarmScope(
        organizationId = preferences.getString(KEY_ORGANIZATION_ID, DEFAULT_ORGANIZATION_ID)!!,
        farmId = preferences.getString(KEY_FARM_ID, DEFAULT_FARM_ID)!!,
        unitId = preferences.getString(KEY_UNIT_ID, null)
    )

    fun selectFarm(organizationId: String, farmId: String) {
        require(organizationId.isNotBlank() && farmId.isNotBlank())
        preferences.edit()
            .putString(KEY_ORGANIZATION_ID, organizationId)
            .putString(KEY_FARM_ID, farmId)
            .remove(KEY_UNIT_ID)
            .apply()
    }

    fun isConfigured(): Boolean = preferences.getBoolean(KEY_CONFIGURED, false)
    fun farmName(): String = preferences.getString(KEY_FARM_NAME, "Minha fazenda") ?: "Minha fazenda"
    fun configure(farmName: String) {
        require(farmName.trim().length >= 2)
        val id = farmName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { DEFAULT_FARM_ID }
        preferences.edit().putString(KEY_ORGANIZATION_ID, DEFAULT_ORGANIZATION_ID).putString(KEY_FARM_ID, id).putString(KEY_FARM_NAME, farmName.trim()).putBoolean(KEY_CONFIGURED, true).apply()
    }

    fun selectUnit(unitId: String) {
        require(unitId.isNotBlank())
        preferences.edit().putString(KEY_UNIT_ID, unitId).apply()
    }

    fun clearUnit() = preferences.edit().remove(KEY_UNIT_ID).apply()

    companion object {
        private const val KEY_ORGANIZATION_ID = "organizationId"
        private const val KEY_FARM_ID = "farmId"
        private const val KEY_UNIT_ID = "unitId"
        private const val KEY_FARM_NAME = "farmName"
        private const val KEY_CONFIGURED = "configured"
        private const val DEFAULT_ORGANIZATION_ID = "local-organization"
        private const val DEFAULT_FARM_ID = "default-farm"
    }
}
