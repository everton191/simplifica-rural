package br.com.simplificarural.ui.cattle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.data.local.CattleManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.ExpandableDetail
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.StatusChip
import br.com.simplificarural.ui.theme.RuralGreen
import br.com.simplificarural.ui.theme.RuralInactive
import br.com.simplificarural.ui.theme.RuralSecondaryText
import br.com.simplificarural.ui.theme.RuralSuccess
import java.math.BigDecimal
import java.time.LocalDate

@Composable internal fun CattleListScreen(open: (String) -> Unit, back: () -> Unit) = Page("Bovinos", onBack = back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val cattle = remember { CattleManagementService(context).cows(scope) }
    if (cattle.isEmpty()) EmptyState("Nenhum bovino cadastrado", "Cadastre nome, brinco e peso para acompanhar a ficha individual.") else cattle.forEach { cow -> PressCard({ open(RuralRoutes.cattleDetail(cow.id)) }) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(cow.name, fontWeight = FontWeight.Bold); Text("Brinco ${cow.earTag} • ${if (cow.isLactating) "Em lactação" else "Fora da lactação"}", color = RuralSecondaryText) }; Icon(Icons.Default.ChevronRight, "Abrir ficha", tint = RuralGreen) } } }
    Button(onClick = { open(RuralRoutes.CATTLE_NEW) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Novo animal") }
}
@Composable internal fun CattleProfileDetailScreen(cattleId: String, back: () -> Unit) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { CattleManagementService(context) }; val cow = remember { service.cows(scope).firstOrNull { it.id == cattleId } }
    if (cow == null) { Page("Bovino não encontrado", onBack = back) { EmptyState("Registro indisponível", "Volte à lista de bovinos e selecione outro animal.") }; return }
    val milk = remember { service.milkRecords(cow.id) }; val today = milk.filter { it.date == LocalDate.now() }.fold(BigDecimal.ZERO) { total, record -> total + record.totalLiters }; val average = remember { service.averageMilk(cow.id) }
    Page(cow.name, "Brinco ${cow.earTag}", back) { StatusChip(if (cow.isLactating) "Em lactação" else "Fora da lactação", if (cow.isLactating) RuralSuccess else RuralInactive); MetricGrid(listOf("Peso" to "${cow.bodyWeightKg.stripTrailingZeros().toPlainString()} kg", "Leite hoje" to "${today.stripTrailingZeros().toPlainString()} L", "Média 7 dias" to "${average.stripTrailingZeros().toPlainString()} L")); ExpandableDetail("Resumo", "Raça: ${cow.breed ?: "Não informada"}\nFase: ${cow.lactationStage.name.lowercase().replaceFirstChar { it.uppercase() }}"); ExpandableDetail("Lançamentos", if (milk.isEmpty()) "Ainda não há ordenhas individuais." else milk.takeLast(8).joinToString("\n") { "${it.date}: ${it.totalLiters.stripTrailingZeros().toPlainString()} L" }); ExpandableDetail("Reprodução", "Registre cio, cobertura e prenhez no menu Reprodução para manter este histórico."); ExpandableDetail("Saúde", "Vacinas e tratamentos confirmados aparecem no histórico de Saúde.") }
}
@Composable internal fun NewCattleScreen(back: () -> Unit, message: (String) -> Unit) = Page("Novo bovino", "Cadastre a vaca para acompanhar leite e alimentação.", back) {
    val context = LocalContext.current; var name by remember { mutableStateOf("") }; var tag by remember { mutableStateOf("") }; var weight by remember { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(tag, { tag = it }, Modifier.fillMaxWidth(), label = { Text("Brinco") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text("Peso estimado (kg)") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { CattleManagementService(context).registerCow(FarmContextStore(context).current(), name, tag, weight.replace(',', '.').toBigDecimal(), true) }.onSuccess { message("Bovino cadastrado com sucesso."); back() }.onFailure { message("Informe nome, brinco e peso válido.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar bovino") }
}
