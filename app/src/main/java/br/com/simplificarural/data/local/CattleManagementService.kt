package br.com.simplificarural.data.local

import android.content.Context
import br.com.simplificarural.domain.nutrition.*
import br.com.simplificarural.domain.property.FarmScope
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.animals.AnimalSex
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CattleDashboard(val totalCattle: Int, val lactatingCattle: Int, val totalMilkToday: BigDecimal, val averageMilkPerLactatingCow: BigDecimal)

/** Persistent local gateway for cattle, milk production and feed mixes. */
class CattleManagementService(private val context: Context) {
    private val store = CattleLocalStore(context)

    fun registerCow(scope: FarmScope, name: String, earTag: String, bodyWeightKg: BigDecimal, isLactating: Boolean, breed: String? = null, stage: LactationStage = LactationStage.MEDIA): CattleProfile {
        require(name.isNotBlank() && earTag.isNotBlank() && bodyWeightKg > BigDecimal.ZERO)
        return CattleProfile(UUID.randomUUID().toString(), scope, name.trim(), earTag.trim(), breed, bodyWeightKg, isLactating, stage).also {
            store.saveCow(it)
            runCatching { AnimalRecordsService(context).registerAnimal(scope, AnimalSpecies.BOVINO, name.trim(), AnimalSex.FEMEA, "Brinco ${earTag.trim()}") }
        }
    }

    fun registerMilk(cattleId: String, morning: BigDecimal, afternoon: BigDecimal, night: BigDecimal = BigDecimal.ZERO, date: LocalDate = LocalDate.now(), notes: String? = null): MilkProductionRecord {
        require(morning >= BigDecimal.ZERO && afternoon >= BigDecimal.ZERO && night >= BigDecimal.ZERO)
        require(store.cows().any { it.id == cattleId }) { "Bovino não encontrado." }
        return MilkProductionRecord(UUID.randomUUID().toString(), cattleId, date, morning, afternoon, night, notes).also(store::saveMilk)
    }

    fun saveIngredient(ingredient: FeedIngredient): FeedIngredient {
        require(ingredient.name.isNotBlank() && ingredient.dryMatterPercent in BigDecimal.ONE..BigDecimal(100))
        require(ingredient.crudeProteinPercentOfDm >= BigDecimal.ZERO && ingredient.totalDigestibleNutrientsPercentOfDm >= BigDecimal.ZERO)
        store.saveIngredient(ingredient); return ingredient
    }

    fun defaultIngredientCatalog(): List<FeedIngredient> = listOf(
        FeedIngredient("silagem_milho", "Silagem de milho", BigDecimal("35"), BigDecimal("8"), BigDecimal("68"), BigDecimal("1.60")),
        FeedIngredient("farelo_soja", "Farelo de soja", BigDecimal("89"), BigDecimal("46"), BigDecimal("84"), BigDecimal("2.10")),
        FeedIngredient("farelo_algodao", "Farelo de algodão", BigDecimal("89"), BigDecimal("38"), BigDecimal("68"), BigDecimal("1.70")),
        FeedIngredient("farelo_trigo", "Farelo de trigo", BigDecimal("89"), BigDecimal("17"), BigDecimal("70"), BigDecimal("1.65"))
    )

    fun saveMix(scope: FarmScope, name: String, targetSpecies: NutritionSpecies, ingredientsKg: Map<String, BigDecimal>, date: LocalDate = LocalDate.now()): FeedMix {
        require(name.isNotBlank() && ingredientsKg.isNotEmpty() && ingredientsKg.values.all { it > BigDecimal.ZERO })
        val available = (defaultIngredientCatalog() + store.ingredients()).associateBy { it.id }
        require(ingredientsKg.keys.all(available::containsKey)) { "Há ingrediente sem composição cadastrada." }
        return FeedMix(UUID.randomUUID().toString(), scope, name, targetSpecies, ingredientsKg.map { FeedMixItem(it.key, it.value) }, date).also(store::saveMix)
    }

    fun cows(scope: FarmScope): List<CattleProfile> = store.cows().filter { it.scope == scope }
    fun milkRecords(cattleId: String, from: LocalDate? = null, to: LocalDate? = null): List<MilkProductionRecord> = store.milk().filter { it.cattleId == cattleId && (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(to)) }
    fun averageMilk(cattleId: String, days: Int = 7): BigDecimal {
        val records = milkRecords(cattleId, LocalDate.now().minusDays(days.toLong() - 1))
        return if (records.isEmpty()) BigDecimal.ZERO else records.fold(BigDecimal.ZERO) { total, record -> total + record.totalLiters } / records.size.toBigDecimal()
    }
    fun dashboard(scope: FarmScope, date: LocalDate = LocalDate.now()): CattleDashboard {
        val cattle = cows(scope); val lactating = cattle.filter(CattleProfile::isLactating); val ids = cattle.map(CattleProfile::id).toSet()
        val milk = store.milk().filter { it.cattleId in ids && it.date == date }.fold(BigDecimal.ZERO) { total, record -> total + record.totalLiters }
        return CattleDashboard(cattle.size, lactating.size, milk, if (lactating.isEmpty()) BigDecimal.ZERO else milk / lactating.size.toBigDecimal())
    }
    fun mixAnalysis(mixId: String, config: DietConfiguration = DietConfiguration()): FeedMixAnalysis {
        val mix = store.mixes().first { it.id == mixId }; val catalog = (defaultIngredientCatalog() + store.ingredients()).associateBy { it.id }
        return CattleNutritionCalculator.evaluateMix(CattleNutritionCalculator.analyze(mix, catalog), config)
    }
    fun assessMixForSpecies(mixId: String, config: DietConfiguration = DietConfiguration()): SpeciesMixAssessment {
        val mix = store.mixes().first { it.id == mixId }
        return SpeciesMixAdvisor.assess(SpeciesNutritionProfiles.defaults(mix.targetSpecies), mixAnalysis(mixId, config))
    }
    fun dietEstimate(cattleId: String, silageIngredientId: String? = "silagem_milho", config: DietConfiguration = DietConfiguration()): CowDietEstimate {
        val cow = store.cows().first { it.id == cattleId }; val ingredients = (defaultIngredientCatalog() + store.ingredients()).associateBy { it.id }
        return CattleNutritionCalculator.estimateCowDiet(cow, averageMilk(cattleId), silageIngredientId?.let(ingredients::get), config)
    }
}

private class CattleLocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("cattle_management", Context.MODE_PRIVATE)
    fun cows(): List<CattleProfile> = array("cows").map { it.toCow() }
    fun milk(): List<MilkProductionRecord> = array("milk").map { it.toMilk() }
    fun ingredients(): List<FeedIngredient> = array("ingredients").map { it.toIngredient() }
    fun mixes(): List<FeedMix> = array("mixes").map { it.toMix() }
    fun saveCow(value: CattleProfile) = append("cows", value.json())
    fun saveMilk(value: MilkProductionRecord) = append("milk", value.json())
    fun saveIngredient(value: FeedIngredient) = append("ingredients", value.json())
    fun saveMix(value: FeedMix) = append("mixes", value.json())
    private fun array(key: String): List<JSONObject> = JSONArray(prefs.getString(key, "[]")).let { json -> (0 until json.length()).map(json::getJSONObject) }
    private fun append(key: String, value: JSONObject) { val all = JSONArray(prefs.getString(key, "[]")); all.put(value); prefs.edit().putString(key, all.toString()).apply() }
    private fun CattleProfile.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("name", name); put("earTag", earTag); put("breed", breed); put("bodyWeightKg", bodyWeightKg.toPlainString()); put("isLactating", isLactating); put("stage", lactationStage.name) }
    private fun MilkProductionRecord.json() = JSONObject().apply { put("id", id); put("cattleId", cattleId); put("date", date.toString()); put("morning", morningLiters.toPlainString()); put("afternoon", afternoonLiters.toPlainString()); put("night", nightLiters.toPlainString()); put("notes", notes) }
    private fun FeedIngredient.json() = JSONObject().apply { put("id", id); put("name", name); put("dryMatter", dryMatterPercent.toPlainString()); put("protein", crudeProteinPercentOfDm.toPlainString()); put("ndt", totalDigestibleNutrientsPercentOfDm.toPlainString()); put("energy", netEnergyLactationMcalPerKgDm?.toPlainString()); put("price", defaultPricePerKg?.toPlainString()) }
    private fun FeedMix.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("name", name); put("species", targetSpecies.name); put("date", createdAt.toString()); put("items", JSONArray(items.map { JSONObject().put("ingredientId", it.ingredientId).put("kg", it.asFedKg.toPlainString()) })) }
    private fun JSONObject.toCow() = CattleProfile(getString("id"), FarmScope(getString("organizationId"), getString("farmId"), optString("unitId").ifBlank { null }), getString("name"), getString("earTag"), optString("breed").ifBlank { null }, getString("bodyWeightKg").toBigDecimal(), getBoolean("isLactating"), LactationStage.valueOf(getString("stage")))
    private fun JSONObject.toMilk() = MilkProductionRecord(getString("id"), getString("cattleId"), LocalDate.parse(getString("date")), getString("morning").toBigDecimal(), getString("afternoon").toBigDecimal(), getString("night").toBigDecimal(), optString("notes").ifBlank { null })
    private fun JSONObject.toIngredient() = FeedIngredient(getString("id"), getString("name"), getString("dryMatter").toBigDecimal(), getString("protein").toBigDecimal(), getString("ndt").toBigDecimal(), optString("energy").ifBlank { null }?.toBigDecimal(), optString("price").ifBlank { null }?.toBigDecimal())
    private fun JSONObject.toMix() = FeedMix(getString("id"), FarmScope(getString("organizationId"), getString("farmId"), optString("unitId").ifBlank { null }), getString("name"), NutritionSpecies.valueOf(optString("species", NutritionSpecies.BOVINOS_LEITE.name)), getJSONArray("items").let { a -> (0 until a.length()).map { index -> a.getJSONObject(index).let { FeedMixItem(it.getString("ingredientId"), it.getString("kg").toBigDecimal()) } } }, LocalDate.parse(getString("date")))
}
