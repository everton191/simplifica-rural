package br.com.simplificarural.domain.inventory

import android.content.Context
import br.com.simplificarural.domain.property.FarmScope

class PackagingConversionService(context: Context) {
    private val prefs = context.getSharedPreferences("rural_packaging", Context.MODE_PRIVATE)
    fun eggsPerPackage(scope: FarmScope, packageName: String): Int = prefs.getInt(key(scope, packageName), 30)
    fun setEggsPerPackage(scope: FarmScope, packageName: String, eggs: Int) { require(eggs > 0); prefs.edit().putInt(key(scope, packageName), eggs).apply() }
    private fun key(scope: FarmScope, packageName: String) = "${scope.organizationId}|${scope.farmId}|${scope.unitId.orEmpty()}|${packageName.lowercase()}"
}
