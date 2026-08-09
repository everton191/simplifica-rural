package br.com.simplificarural.domain.nutrition

import java.math.BigDecimal

enum class NutritionSpecies { AVES_POSTURA, BOVINOS_LEITE, SUINOS_CRESCIMENTO, SUINOS_TERMINACAO, SUINOS_MATRIZES, CAPRINOS, OVINOS, EQUINOS, PISCICULTURA }

/** Editable operating targets. They are starting points for a simulation, not a diet prescription. */
data class SpeciesNutritionProfile(
    val species: NutritionSpecies,
    val displayName: String,
    val proteinMinPercentOfDryMatter: BigDecimal? = null,
    val proteinMaxPercentOfDryMatter: BigDecimal? = null,
    val recommendedForageShareOfDryMatterPercent: BigDecimal? = null,
    val requiresPremixOrSpeciesFormula: Boolean = false,
    val adjustmentNotes: List<String>
)

data class SpeciesMixAssessment(
    val profile: SpeciesNutritionProfile,
    val analysis: FeedMixAnalysis,
    val suggestedAdjustments: List<String>
)

object SpeciesNutritionProfiles {
    fun defaults(species: NutritionSpecies): SpeciesNutritionProfile = when (species) {
        NutritionSpecies.BOVINOS_LEITE -> SpeciesNutritionProfile(species, "Bovinos de leite", BigDecimal("16"), BigDecimal("18"), BigDecimal("55"), adjustmentNotes = listOf("Use a composição real da silagem para converter matéria seca em kg fresco."))
        NutritionSpecies.AVES_POSTURA -> SpeciesNutritionProfile(species, "Aves de postura", BigDecimal("16"), BigDecimal("18"), requiresPremixOrSpeciesFormula = true, adjustmentNotes = listOf("A mistura deve incluir núcleo ou premix próprio para postura conforme rótulo.", "Não use uma mistura de ruminantes para aves."))
        NutritionSpecies.SUINOS_CRESCIMENTO -> SpeciesNutritionProfile(species, "Suínos — crescimento", BigDecimal("16"), BigDecimal("18"), requiresPremixOrSpeciesFormula = true, adjustmentNotes = listOf("Use núcleo/premix próprio para a fase de crescimento.", "A proporção deve ser revisada ao mudar peso e genética do lote."))
        NutritionSpecies.SUINOS_TERMINACAO -> SpeciesNutritionProfile(species, "Suínos — terminação", BigDecimal("14"), BigDecimal("16"), requiresPremixOrSpeciesFormula = true, adjustmentNotes = listOf("Use núcleo/premix próprio para terminação.", "Não reutilize automaticamente a fórmula de crescimento."))
        NutritionSpecies.SUINOS_MATRIZES -> SpeciesNutritionProfile(species, "Suínos — matrizes", BigDecimal("15"), BigDecimal("17"), requiresPremixOrSpeciesFormula = true, adjustmentNotes = listOf("Gestação e lactação exigem perfis diferentes; cadastre uma fórmula para cada fase."))
        NutritionSpecies.CAPRINOS -> SpeciesNutritionProfile(species, "Caprinos", BigDecimal("12"), BigDecimal("16"), BigDecimal("60"), adjustmentNotes = listOf("Ajuste por idade, ganho, gestação ou lactação e disponibilidade de pasto."))
        NutritionSpecies.OVINOS -> SpeciesNutritionProfile(species, "Ovinos", BigDecimal("12"), BigDecimal("16"), BigDecimal("60"), adjustmentNotes = listOf("Ajuste por categoria e evite usar suplemento mineral inadequado para ovinos."))
        NutritionSpecies.EQUINOS -> SpeciesNutritionProfile(species, "Equinos", requiresPremixOrSpeciesFormula = true, adjustmentNotes = listOf("Equinos não usam o mesmo balanço de ruminantes; configure a fórmula com responsável técnico."))
        NutritionSpecies.PISCICULTURA -> SpeciesNutritionProfile(species, "Piscicultura", requiresPremixOrSpeciesFormula = true, adjustmentNotes = listOf("Use ração específica para espécie, fase e sistema de criação; não misture ração terrestre."))
    }
}

object SpeciesMixAdvisor {
    fun assess(profile: SpeciesNutritionProfile, analysis: FeedMixAnalysis): SpeciesMixAssessment {
        val suggestions = buildList {
            profile.proteinMinPercentOfDryMatter?.let { min -> if (analysis.crudeProteinPercentOfDm < min) add("Proteína abaixo da faixa de ${profile.displayName}: simule maior participação de fonte proteica cadastrada e revise com técnico.") }
            profile.proteinMaxPercentOfDryMatter?.let { max -> if (analysis.crudeProteinPercentOfDm > max) add("Proteína acima da faixa de ${profile.displayName}: simule reduzir fonte proteica e conferir energia, fibra e custo.") }
            profile.recommendedForageShareOfDryMatterPercent?.let { add("Proporção inicial configurável: cerca de $it% da matéria seca como volumoso; ajuste conforme categoria e análise do alimento.") }
            if (profile.requiresPremixOrSpeciesFormula) add("Esta espécie/fase exige núcleo, premix ou formulação específica; a simulação não confirma vitaminas, minerais ou aminoácidos.")
            addAll(profile.adjustmentNotes)
        }
        return SpeciesMixAssessment(profile, analysis, suggestions)
    }
}
