@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package br.com.simplificarural.ui.health

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.theme.RuralSecondaryText
import br.com.simplificarural.ui.theme.RuralWarning
import java.time.LocalDate

@Composable internal fun HealthScreen(back: () -> Unit, message: (String) -> Unit) = Page("Saúde", "Registre vacina, tratamento e retorno do animal ou lote.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val store = remember { AnimalRecordsService(context) }; var revision by remember { mutableIntStateOf(0) }; val targets = remember(revision) { store.animals(scope).map { it.id to it.identification } + store.batches(scope).map { it.id to it.name } }; val events = remember(revision) { store.healthHistory(scope) }; var targetId by remember { mutableStateOf("") }; var product by remember { mutableStateOf("") }; var nextDate by remember { mutableStateOf("") }; var typeIndex by remember { mutableIntStateOf(0) }; val types = br.com.simplificarural.domain.health.HealthEventType.entries
    var expanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(expanded, { expanded = it }) { OutlinedTextField(targets.firstOrNull { it.first == targetId }?.second.orEmpty(), {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Animal ou lote") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, shape = RoundedCornerShape(14.dp)); ExposedDropdownMenu(expanded, { expanded = false }) { targets.forEach { target -> DropdownMenuItem({ Text(target.second) }, { targetId = target.first; expanded = false }) } } }
    var typeExpanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(typeExpanded, { typeExpanded = it }) { OutlinedTextField(types[typeIndex].name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Tipo de registro") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, shape = RoundedCornerShape(14.dp)); ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) { types.forEachIndexed { index, type -> DropdownMenuItem({ Text(type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }, { typeIndex = index; typeExpanded = false }) } } }
    OutlinedTextField(product, { product = it }, Modifier.fillMaxWidth(), label = { Text("Vacina, medicamento ou condição") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(nextDate, { nextDate = it }, Modifier.fillMaxWidth(), label = { Text("Próximo retorno (AAAA-MM-DD, opcional)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { store.registerHealth(scope, targetId, product, types[typeIndex], nextDueDate = nextDate.ifBlank { null }?.let(LocalDate::parse)); product = ""; nextDate = ""; revision++ }.onSuccess { message("Registro de saúde salvo e retorno agendado quando informado.") }.onFailure { message("Selecione o animal/lote e informe o registro.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar registro de saúde") }
    MetricGrid(listOf("Registros" to events.size.toString(), "Vacinas" to events.count { it.type.name == "VACINA" }.toString(), "Retornos" to events.count { it.nextDueDate != null }.toString(), "Último" to events.firstOrNull()?.date?.toString().orEmpty().ifBlank { "—" }))
    if (events.isEmpty()) EmptyState("Sem histórico de saúde", "Selecione um animal ou lote e faça o primeiro lançamento.") else events.forEach { event -> PressCard { Text(event.productOrCondition, fontWeight = FontWeight.Bold); Text("${event.type.name.lowercase().replaceFirstChar { it.uppercase() }} • ${event.date}", color = RuralSecondaryText); event.nextDueDate?.let { Text("Retorno: $it", color = RuralWarning, style = MaterialTheme.typography.bodySmall) } } }
}
