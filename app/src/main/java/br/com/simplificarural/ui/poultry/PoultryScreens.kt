package br.com.simplificarural.ui.poultry

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.QuickGrid
import java.math.BigDecimal
import java.time.LocalDate

@Composable internal fun BirdsScreen(open: (String) -> Unit) = Page("Aves") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val animalRecords = remember { AnimalRecordsService(context) }; val management = remember { FarmManagementService(context) }
    val birds = remember { animalRecords.batches(scope, AnimalSpecies.AVE).sumOf { it.currentQuantity } }; val todayEggs = remember { management.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS && it.date == LocalDate.now() }.fold(BigDecimal.ZERO) { total, record -> total + (record.quantity ?: BigDecimal.ZERO) } }
    MetricGrid(listOf("Total de aves" to birds.toString(), "Ovos hoje" to "${todayEggs.stripTrailingZeros().toPlainString()}", "Postura" to if (birds > 0) "${todayEggs.multiply(BigDecimal(100)).divide(BigDecimal(birds), 0, java.math.RoundingMode.HALF_UP)}%" else "—", "Ração hoje" to "A registrar"))
    Text("Ações rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    QuickGrid(listOf("Registrar ovos" to RuralRoutes.BIRD_EGGS, "Lotes" to RuralRoutes.BIRD_LOTS, "Alimentação" to RuralRoutes.feature("Alimentação aves"), "Saúde" to RuralRoutes.HEALTH, "Ocorrências" to RuralRoutes.feature("Ocorrências aves"), "Histórico" to RuralRoutes.HISTORY, "Relatórios" to RuralRoutes.feature("Relatórios aves")), open)
}
@Composable internal fun EggRegistrationScreen(back: () -> Unit, message: (String) -> Unit) = Page("Registrar ovos", "Data e hora são incluídas automaticamente.", back) {
    val context = LocalContext.current; var eggs by remember { mutableStateOf("") }
    OutlinedTextField(eggs, { eggs = it }, Modifier.fillMaxWidth(), label = { Text("Ovos aproveitáveis") }, suffix = { Text("unidades") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { FarmManagementService(context).registerEggProduction(FarmContextStore(context).current(), eggs.toInt()) }.onSuccess { message("Produção salva com data e horário atuais."); back() }.onFailure { message("Informe uma quantidade válida.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar produção") }
}
