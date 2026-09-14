package br.com.simplificarural.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
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
import br.com.simplificarural.data.local.ActivityLogService
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.theme.RuralSecondaryText

@Composable internal fun HistoryScreen(back: () -> Unit) = Page("Histórico geral", "Lançamentos confirmados da fazenda selecionada.", back) {
    val context = LocalContext.current
    val scope = remember { FarmContextStore(context).current() }
    val management = remember { FarmManagementService(context) }
    val activities = remember { ActivityLogService(context) }
    val records = remember { management.records(CashViewScope.SelectedUnit(scope)) }
    val notes = remember { activities.listAll(scope) }
    MetricGrid(listOf("Operações" to records.size.toString(), "Anotações" to notes.size.toString()))
    if (records.isEmpty() && notes.isEmpty()) EmptyState("Nenhum histórico ainda", "Confirme um lançamento ou salve uma anotação para começar.")
    records.forEach { record -> PressCard { Text(record.description, fontWeight = FontWeight.Bold); Text("${record.date} • ${record.quantity?.stripTrailingZeros()?.toPlainString().orEmpty()} ${record.unit.orEmpty()}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
    notes.forEach { note -> PressCard { Text(note.area, fontWeight = FontWeight.Bold); Text(note.description, color = RuralSecondaryText); Text("${note.createdAt.toLocalDate()} ${note.createdAt.toLocalTime().withSecond(0).withNano(0)}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
}
@Composable internal fun FeatureScreen(feature: String, back: () -> Unit, message: (String) -> Unit) = Page(feature, "Registro operacional", back) { val context = LocalContext.current; val farm = remember { FarmContextStore(context).current() }; val store = remember { ActivityLogService(context) }; var target by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var revision by remember { mutableIntStateOf(0) }; val entries = remember(revision) { store.list(farm, feature) }; val quantityNeeded = feature.contains("Alimentação", true) || feature.contains("Mortalidade", true) || feature.contains("Ocorrências", true) || feature.contains("Leitões", true); val description = when { feature.contains("Reprodução", true) || feature in listOf("Cio", "Cobertura", "Inseminação", "Prenhez") -> "Registre o evento reprodutivo para o animal ou lote."; feature.contains("Alimentação", true) -> "Registre alimento e quantidade fornecida ao animal ou lote."; feature.contains("Mortalidade", true) || feature.contains("Ocorrências", true) -> "Registre quantidade, causa e identificação do lote."; else -> "Registre a ocorrência para manter o histórico da propriedade." }; PressCard { Text(description, color = RuralSecondaryText) }; OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("Animal ou lote") }, shape = RoundedCornerShape(14.dp)); if (quantityNeeded) OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Descrição / observação") }, minLines = 2, shape = RoundedCornerShape(14.dp)); Button({ runCatching { require(target.isNotBlank() && note.isNotBlank()); store.add(farm, feature, buildString { append("Alvo: $target"); if (quantity.isNotBlank()) append(" • Quantidade: $quantity"); append(" • $note") }); target = ""; quantity = ""; note = ""; revision++ }.onSuccess { message("Registro salvo no histórico de $feature.") }.onFailure { message("Informe animal/lote e descrição do registro.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar registro") }; Text("Histórico", fontWeight = FontWeight.Bold); if (entries.isEmpty()) EmptyState("Nenhum registro de $feature", "Adicione o primeiro registro para começar.") else entries.forEach { entry -> PressCard { Text(entry.description, fontWeight = FontWeight.Medium); Text(entry.createdAt.toLocalDate().toString() + " • " + entry.createdAt.toLocalTime().withSecond(0).withNano(0), color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } } }
@Composable internal fun PlaceholderScreen(title: String, back: () -> Unit) = Page(title, "Esta tela será conectada ao módulo correspondente.", back) { EmptyState("Nenhum registro disponível", "Cadastre o primeiro registro para começar.") }
