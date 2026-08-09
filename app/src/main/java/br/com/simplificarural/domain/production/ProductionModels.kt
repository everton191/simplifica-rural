package br.com.simplificarural.domain.production

import br.com.simplificarural.domain.property.FarmScope
import java.math.BigDecimal
import java.time.LocalDate

enum class ProductionType { OVOS, LEITE, GANHO_PESO, NASCIMENTO, DESMAME, MORTALIDADE }

data class ProductionEntry(
    val id: String,
    val scope: FarmScope,
    val type: ProductionType,
    val date: LocalDate,
    val quantity: BigDecimal,
    val unit: String,
    val animalOrBatchId: String? = null,
    val notes: String? = null
)
