package br.com.simplificarural.ui.swine

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.ExpandableDetail
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.NutritionNumberField
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.decimal
import br.com.simplificarural.ui.theme.RuralSecondaryText

@Composable internal fun SowDetailScreen(back: () -> Unit) = Page("Matrizes", "Registros de matrizes cadastradas na propriedade.", back) { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val animals = remember { AnimalRecordsService(context).animals(scope, AnimalSpecies.SUINO) }; val farrowings = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PARTO_SUINOS } }; MetricGrid(listOf("Matrizes identificadas" to animals.size.toString(), "Partos registrados" to farrowings.size.toString())); if (animals.isEmpty()) EmptyState("Nenhuma matriz cadastrada", "Cadastre uma identificação de suíno na tela Animais para acompanhar eventos individuais.") else animals.forEach { animal -> PressCard { Text(animal.identification, fontWeight = FontWeight.Bold); Text("${animal.status.name.lowercase().replaceFirstChar { it.uppercase() }} • consulte saúde e reprodução", color = RuralSecondaryText) } }; if (farrowings.isNotEmpty()) ExpandableDetail("Histórico de partos", farrowings.take(8).joinToString("\n") { "${it.date}: ${it.quantity?.stripTrailingZeros()?.toPlainString()} nascidos vivos" }) }
@Composable internal fun SwineWeightScreen(back: () -> Unit, message: (String) -> Unit) = Page("Registrar pesagem", "Use o peso médio do lote; data e hora são registradas automaticamente.", back) { val context = LocalContext.current; var animals by remember { mutableStateOf("") }; var initial by remember { mutableStateOf("") }; var final by remember { mutableStateOf("") }; var days by remember { mutableStateOf("") }; OutlinedTextField(animals, { animals = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade de animais") }, shape = RoundedCornerShape(14.dp)); NutritionNumberField("Peso médio anterior", initial) { initial = it }; NutritionNumberField("Peso médio atual", final) { final = it }; OutlinedTextField(days, { days = it }, Modifier.fillMaxWidth(), label = { Text("Dias desde a pesagem anterior") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { FarmManagementService(context).registerSwineWeight(FarmContextStore(context).current(), animals.toInt(), initial.decimal(), final.decimal(), days.toInt()) }.onSuccess { message("Pesagem salva no desempenho dos suínos."); back() }.onFailure { message("Confira quantidade, pesos e dias informados.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar pesagem") } }
@Composable internal fun SwineFarrowingScreen(back: () -> Unit, message: (String) -> Unit) = Page("Parto e desmame", "Registre números por matriz ou lote para gerar indicadores.", back) { val context = LocalContext.current; var alive by remember { mutableStateOf("") }; var dead by remember { mutableStateOf("") }; var weaned by remember { mutableStateOf("") }; OutlinedTextField(alive, { alive = it }, Modifier.fillMaxWidth(), label = { Text("Leitões nascidos vivos") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(dead, { dead = it }, Modifier.fillMaxWidth(), label = { Text("Nascidos mortos") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(weaned, { weaned = it }, Modifier.fillMaxWidth(), label = { Text("Desmamados (opcional)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { FarmManagementService(context).registerSwineFarrowing(FarmContextStore(context).current(), alive.toInt(), dead.ifBlank { "0" }.toInt(), weaned.ifBlank { null }?.toInt()) }.onSuccess { message("Parto salvo no histórico de matrizes."); back() }.onFailure { message("Informe pelo menos os nascidos vivos e revise os números.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar parto/desmame") } }
