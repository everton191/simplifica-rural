package br.com.simplificarural.domain.agenda

import br.com.simplificarural.domain.property.FarmScope
import java.time.LocalDate

enum class AgendaType { SAUDE, REPRODUCAO, FINANCEIRO, ESTOQUE, MANEJO, OUTRO }
enum class AgendaStatus { PENDENTE, CONCLUIDA, CANCELADA }

data class RuralTask(
    val id: String,
    val scope: FarmScope,
    val type: AgendaType,
    val title: String,
    val dueDate: LocalDate,
    val status: AgendaStatus = AgendaStatus.PENDENTE,
    val sourceRecordId: String? = null,
    val notes: String? = null
)
