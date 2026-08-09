package br.com.simplificarural.domain.health

import br.com.simplificarural.domain.property.FarmScope
import java.time.LocalDate

enum class HealthEventType { VACINA, VERMIFUGO, MEDICAMENTO, DOENCA, TRATAMENTO, VISITA_VETERINARIA }

data class HealthEvent(
    val id: String,
    val scope: FarmScope,
    val animalOrBatchId: String,
    val type: HealthEventType,
    val date: LocalDate,
    val productOrCondition: String,
    val withdrawalEndDate: LocalDate? = null,
    val nextDueDate: LocalDate? = null,
    val veterinarian: String? = null,
    val notes: String? = null
)
