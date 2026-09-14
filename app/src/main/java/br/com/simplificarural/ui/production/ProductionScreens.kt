package br.com.simplificarural.ui.production

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.data.local.CattleManagementService
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.ProductionChart
import br.com.simplificarural.ui.components.Segment
import br.com.simplificarural.ui.theme.RuralSecondaryText
import java.math.BigDecimal
import java.time.LocalDate

@Composable internal fun ProductionScreen(back: () -> Unit) = Page("Produção", "Dados confirmados da propriedade", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)) }; var period by remember { mutableIntStateOf(0) }
    Segment(listOf("Hoje", "Semana", "Mês")) { period = it }; val days = if (period == 0) 1 else if (period == 1) 7 else LocalDate.now().lengthOfMonth(); val dates = (days - 1 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val eggs = dates.map { date -> records.filter { it.date == date && it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS }.sumOf { it.quantity?.toInt() ?: 0 }.toBigDecimal() }
    val milk = dates.map { date -> records.filter { it.date == date && it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE }.fold(BigDecimal.ZERO) { total, item -> total + (item.quantity ?: BigDecimal.ZERO) } }
    ProductionChart("Ovos", eggs, "unidades", dates); ProductionChart("Leite", milk, "litros", dates)
    val weights = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PESAGEM_SUINOS }.take(6).reversed().map { it.quantity ?: BigDecimal.ZERO }
    if (weights.isNotEmpty()) ProductionChart("Peso dos suínos", weights, "kg", emptyList()) else EmptyState("Sem pesagens de suínos", "Registre uma pesagem no módulo de engorda.")
}
@Composable internal fun AreaReportScreen(area: String, back: () -> Unit) = Page(area, "Indicadores calculados somente com lançamentos confirmados.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)) }; val dates = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    when {
        area.contains("Aves", true) -> { val eggs = dates.map { day -> records.filter { it.date == day && it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS }.fold(BigDecimal.ZERO) { sum, record -> sum + (record.quantity ?: BigDecimal.ZERO) } }; ProductionChart("Produção de ovos", eggs, "ovos", dates); val birds = AnimalRecordsService(context).batches(scope, AnimalSpecies.AVE).sumOf { it.currentQuantity }; MetricGrid(listOf("Aves em lotes" to birds.toString(), "Ovos/ave no período" to if (birds > 0) eggs.fold(BigDecimal.ZERO, BigDecimal::plus).divide(BigDecimal(birds), 2, java.math.RoundingMode.HALF_UP).toPlainString() else "—")); Text("Registre ovos íntegros, quebrados, mortalidade e consumo de ração por lote para ampliar estes indicadores.", color = RuralSecondaryText) }
        area.contains("Bovinos", true) -> { val milk = dates.map { day -> records.filter { it.date == day && it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE }.fold(BigDecimal.ZERO) { sum, record -> sum + (record.quantity ?: BigDecimal.ZERO) } }; ProductionChart("Produção de leite", milk, "litros", dates); val dashboard = CattleManagementService(context).dashboard(scope); MetricGrid(listOf("Vacas em lactação" to dashboard.lactatingCattle.toString(), "Média por vaca" to "${dashboard.averageMilkPerLactatingCow.stripTrailingZeros().toPlainString()} L", "Total semanal" to "${milk.fold(BigDecimal.ZERO, BigDecimal::plus).stripTrailingZeros().toPlainString()} L")); Text("Controle de reprodução, saúde, alimentação e produção individual complementa o diagnóstico do rebanho.", color = RuralSecondaryText) }
        else -> { val weights = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PESAGEM_SUINOS }.take(7).reversed().mapNotNull { it.quantity }; if (weights.isEmpty()) EmptyState("Sem pesagens confirmadas", "Registre pesos por lote para acompanhar ganho médio diário.") else ProductionChart("Pesagens recentes", weights, "kg", emptyList()); val performance = FarmManagementService(context).swinePerformance(CashViewScope.SelectedUnit(scope)); MetricGrid(listOf("Ganho médio" to performance.averageDailyGainKg?.multiply(BigDecimal(1000))?.toPlainString().orEmpty().ifBlank { "—" } + " g/dia", "Desmamados/lote" to performance.weanedPerLitter?.toPlainString().orEmpty().ifBlank { "—" }, "Mortalidade pré-desmame" to performance.preWeaningMortalityPercent?.toPlainString().orEmpty().ifBlank { "—" } + "%")); Text("Para indicadores completos, registre pesagem, consumo de ração, mortalidade, partos e desmames por lote.", color = RuralSecondaryText) }
    }
}
