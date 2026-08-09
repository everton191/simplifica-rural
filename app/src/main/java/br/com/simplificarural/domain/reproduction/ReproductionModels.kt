package br.com.simplificarural.domain.reproduction

import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.property.FarmScope
import java.time.LocalDate

enum class ReproductionStage { CIO, COBERTURA_OU_INSEMINACAO, PRENHEZ_CONFIRMADA, PARTO, LACTACAO, DESMAME, SECAGEM }

data class ReproductionEvent(
    val id: String,
    val scope: FarmScope,
    val animalId: String,
    val species: AnimalSpecies,
    val stage: ReproductionStage,
    val date: LocalDate,
    val expectedBirthDate: LocalDate? = null,
    val bornAlive: Int? = null,
    val bornDead: Int? = null,
    val weaned: Int? = null,
    val notes: String? = null
)
