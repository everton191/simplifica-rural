package br.com.simplificarural.domain.nutrition

import java.math.BigDecimal
import java.math.RoundingMode

object CattleNutritionCalculator {
    private val hundred = BigDecimal(100)

    fun analyze(mix: FeedMix, ingredients: Map<String, FeedIngredient>): FeedMixAnalysis {
        val resolved = mix.items.map { item -> item to requireNotNull(ingredients[item.ingredientId]) { "Ingrediente não encontrado na mistura." } }
        val totalAsFed = resolved.sumOf { it.first.asFedKg }
        require(totalAsFed > BigDecimal.ZERO) { "A mistura precisa de pelo menos um ingrediente." }
        val totalDm = resolved.sumOf { (item, ingredient) -> item.asFedKg * ingredient.dryMatterPercent / hundred }
        val proteinKg = resolved.sumOf { (item, ingredient) -> item.asFedKg * ingredient.dryMatterPercent / hundred * ingredient.crudeProteinPercentOfDm / hundred }
        val ndtKg = resolved.sumOf { (item, ingredient) -> item.asFedKg * ingredient.dryMatterPercent / hundred * ingredient.totalDigestibleNutrientsPercentOfDm / hundred }
        val energy = resolved.mapNotNull { (item, ingredient) -> ingredient.netEnergyLactationMcalPerKgDm?.let { item.asFedKg * ingredient.dryMatterPercent / hundred * it } }.takeIf { it.size == resolved.size }?.reduce(BigDecimal::plus)
        val proteinPercent = proteinKg * hundred / totalDm
        val ndtPercent = ndtKg * hundred / totalDm
        return FeedMixAnalysis(totalAsFed, totalDm, totalDm * hundred / totalAsFed, proteinPercent, ndtPercent, energy, emptyList())
    }

    fun estimateCowDiet(cow: CattleProfile, averageMilkLiters: BigDecimal, silage: FeedIngredient?, configuration: DietConfiguration): CowDietEstimate {
        require(cow.bodyWeightKg > BigDecimal.ZERO) { "Informe o peso da vaca para estimar a dieta." }
        val dryMatter = cow.bodyWeightKg * configuration.estimatedDryMatterIntakePercentOfBodyWeight / hundred
        val concentrate = if (cow.isLactating) averageMilkLiters / configuration.kilogramsMilkPerKilogramConcentrate else BigDecimal.ZERO
        val forageDm = dryMatter * configuration.forageShareOfDryMatterPercent / hundred
        val freshSilage = silage?.let { forageDm / (it.dryMatterPercent / hundred) }
        return CowDietEstimate(cow.name, averageMilkLiters, dryMatter, concentrate, forageDm, freshSilage, buildList {
            add("Estimativa de apoio; ajuste usando análise da silagem, condição corporal, estágio de lactação e orientação técnica.")
            if (silage == null) add("Sem silagem selecionada: o valor em kg fresco não foi calculado.")
            if (!cow.isLactating) add("Vaca marcada como seca: concentrado estimado como zero.")
        }).rounded()
    }

    fun evaluateMix(analysis: FeedMixAnalysis, configuration: DietConfiguration): FeedMixAnalysis = analysis.copy(warnings = buildList {
        if (analysis.crudeProteinPercentOfDm < configuration.targetProteinMinPercentOfDm) add("Proteína da mistura abaixo da faixa configurada.")
        if (analysis.crudeProteinPercentOfDm > configuration.targetProteinMaxPercentOfDm) add("Proteína da mistura acima da faixa configurada.")
        add("Valores nutricionais são estimativas: substitua pelos resultados de análise laboratorial quando disponíveis.")
    })

    private fun CowDietEstimate.rounded() = copy(
        averageMilkLitersPerDay = averageMilkLitersPerDay.setScale(2, RoundingMode.HALF_UP), estimatedDryMatterIntakeKg = estimatedDryMatterIntakeKg.setScale(2, RoundingMode.HALF_UP), estimatedConcentrateAsFedKg = estimatedConcentrateAsFedKg.setScale(2, RoundingMode.HALF_UP), estimatedForageDryMatterKg = estimatedForageDryMatterKg.setScale(2, RoundingMode.HALF_UP), estimatedFreshSilageKg = estimatedFreshSilageKg?.setScale(2, RoundingMode.HALF_UP)
    )
}
