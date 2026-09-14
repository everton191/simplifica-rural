package br.com.simplificarural.ui.agenda

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.domain.agenda.AgendaType
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.AgendaRow
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.theme.RuralInfo
import br.com.simplificarural.ui.theme.RuralWarning
import java.time.LocalDate

@Composable internal fun AgendaScreen(back: () -> Unit) = Page("Agenda", "Marque manejo, saúde e compromissos.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { AnimalRecordsService(context) }
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var revision by remember { mutableIntStateOf(0) }; val tasks = remember(revision) { records.tasks(scope) }
    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("O que precisa ser lembrado?") }, shape = RoundedCornerShape(14.dp))
    OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("Data (AAAA-MM-DD)") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { records.schedule(scope, title, LocalDate.parse(date), AgendaType.MANEJO); title = ""; revision++ } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Adicionar à agenda") }
    if (tasks.isEmpty()) EmptyState("Nenhum agendamento", "Crie um lembrete ou confirme um evento de saúde.") else tasks.forEach { task -> AgendaRow(task.title, task.type.name.lowercase().replaceFirstChar { it.uppercase() }, task.dueDate.toString(), if (task.dueDate <= LocalDate.now()) RuralWarning else RuralInfo) }
}
