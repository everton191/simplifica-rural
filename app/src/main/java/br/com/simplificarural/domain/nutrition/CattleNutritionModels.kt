package br.com.simplificarural.domain.nutrition

import br.com.simplificarural.domain.property.FarmScope
import java.math.BigDecimal
import java.time.LocalDate

data class CattleProfile(
    val id: String,
    val scope: FarmScope,
    val name: String,
    val earTag: String,
    val breed: String? = null,
    val bodyWeightKg: BigDecimal,
    val isLactating: Boolean,
    val lactationStage: LactationStage = LactationStage.MEDIA
)

enum class LactationStage { INICIAL, MEDIA, FINAL, SECA }

data class MilkProductionRecord(
    val id: String,
    val cattleId: String,
    val date: LocalDate,
    val morningLiters: BigDecimal,
    val afternoonLiters: BigDecimal,
    val nightLiters: BigDecimal = BigDecimal.ZERO,
    val notes: String? = null
) { val totalLiters: BigDecimal get() = morningLiters + afternoonLiters + nightLiters }

/** All values are configurable. Lab analysis must replace any default estimate. */
data class FeedIngredient(
    val id: String,
    val name: String,
    val dryMatterPercent: BigDecimal,
    val crudeProteinPercentOfDm: BigDecimal,
    val totalDigestibleNutrientsPercentOfDm: BigDecimal,
    val netEnergyLactationMcalPerKgDm: BigDecimal? = null,
    val defaultPricePerKg: BigDecimal? = null
)

data class FeedMixItem(val ingredientId: String, val asFedKg: BigDecimal)
data class FeedMix(val id: String, val scope: FarmScope, val name: String, val targetSpecies: NutritionSpecies, val items: List<FeedMixItem>, val createdAt: LocalDate)

data class DietConfiguration(
    val estimatedDryMatterIntakePercentOfBodyWeight: BigDecimal = BigDecimal("3.0"),
    val forageShareOfDryMatterPercent: BigDecimal = BigDecimal("55"),
    val kilogramsMilkPerKilogramConcentrate: BigDecimal = BigDecimal("2.5"),
    val targetProteinMinPercentOfDm: BigDecimal = BigDecimal("16"),
    val targetProteinMaxPercentOfDm: BigDecimal = BigDecimal("18")
)

data class FeedMixAnalysis(
    val totalAsFedKg: BigDecimal,
    val totalDryMatterKg: BigDecimal,
    val dryMatterPercent: BigDecimal,
    val crudeProteinPercentOfDm: BigDecimal,
    val totalDigestibleNutrientsPercentOfDm: BigDecimal,
    val estimatedNetEnergyMcal: BigDecimal?,
    val warnings: List<String>
)

data class CowDietEstimate(
    val cowName: String,
    val averageMilkLitersPerDay: BigDecimal,
    val estimatedDryMatterIntakeKg: BigDecimal,
    val estimatedConcentrateAsFedKg: BigDecimal,
    val estimatedForageDryMatterKg: BigDecimal,
    val estimatedFreshSilageKg: BigDecimal?,
    val notices: List<String>
)
