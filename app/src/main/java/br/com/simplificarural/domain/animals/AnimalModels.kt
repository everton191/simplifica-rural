package br.com.simplificarural.domain.animals

import br.com.simplificarural.domain.property.FarmScope
import java.time.LocalDate

enum class AnimalSpecies { AVE, BOVINO, SUINO, CAPRINO, OVINO, PEIXE, EQUINO }
enum class AnimalSex { FEMEA, MACHO, NAO_INFORMADO }
enum class AnimalStatus { ATIVO, VENDIDO, MORTO, DESCARTADO }

/** Individual animals are used for bovines and breeding stock; batches for poultry and fattening. */
data class Animal(
    val id: String,
    val scope: FarmScope,
    val species: AnimalSpecies,
    val identification: String,
    val sex: AnimalSex,
    val birthDate: LocalDate? = null,
    val status: AnimalStatus = AnimalStatus.ATIVO,
    val motherId: String? = null,
    val notes: String? = null
)

data class AnimalBatch(
    val id: String,
    val scope: FarmScope,
    val species: AnimalSpecies,
    val name: String,
    val startedAt: LocalDate,
    val initialQuantity: Int,
    val currentQuantity: Int,
    val status: AnimalStatus = AnimalStatus.ATIVO
)
