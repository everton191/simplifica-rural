package br.com.simplificarural.domain.property

/** A customer account can own many farms, and every farm can contain many units. */
data class RuralOrganization(
    val id: String,
    val name: String
)

data class Farm(
    val id: String,
    val organizationId: String,
    val name: String,
    val city: String? = null,
    val state: String? = null,
    val active: Boolean = true
)

enum class FarmUnitType {
    GRANJA_AVES,
    SETOR_BOVINOS,
    SUINOS_ENGORDA,
    SUINOS_MATRIZES_PARIDEIRAS,
    ARMAZEM,
    OUTRA
}

data class FarmUnit(
    val id: String,
    val farmId: String,
    val name: String,
    val type: FarmUnitType,
    val active: Boolean = true
)

/**
 * Mandatory scope for operational records. Examples: Fazenda A or Fazenda B → Granja A.
 * unitId is null only when the information belongs to the farm as a whole, such as a general expense.
 */
data class FarmScope(
    val organizationId: String,
    val farmId: String,
    val unitId: String? = null
) {
    init {
        require(organizationId.isNotBlank())
        require(farmId.isNotBlank())
    }
}
