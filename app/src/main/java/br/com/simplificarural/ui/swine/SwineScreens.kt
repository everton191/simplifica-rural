package br.com.simplificarural.ui.swine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

@Composable internal fun SwineScreen(open: (String) -> Unit) = Page("Suínos") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { AnimalRecordsService(context) }; val batches = remember { records.batches(scope, AnimalSpecies.SUINO) }; val animals = remember { records.animals(scope, AnimalSpecies.SUINO) }; val total = batches.sumOf { it.currentQuantity } + animals.count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO }
    MetricGrid(listOf("Total" to total.toString(), "Lotes" to batches.size.toString(), "Matrizes" to animals.count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO }.toString(), "Leitões" to "A registrar"))
    QuickGrid(listOf("Engorda" to RuralRoutes.SWINE_FATTENING, "Matrizes" to RuralRoutes.SWINE_BREEDING), open)
}
@Composable internal fun FatteningScreen(open: (String) -> Unit) = Page("Suínos — Engorda") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val lots = remember { AnimalRecordsService(context).batches(scope, AnimalSpecies.SUINO) }; val performance = remember { FarmManagementService(context).swinePerformance(CashViewScope.SelectedUnit(scope)) }
    val gain = performance.averageDailyGainKg?.multiply(BigDecimal(1000))?.stripTrailingZeros()?.toPlainString() ?: "—"
    MetricGrid(listOf("Animais" to lots.sumOf { it.currentQuantity }.toString(), "Ganho médio" to "$gain g/dia", "Ração/dia" to "A registrar", "Desmame/lote" to (performance.weanedPerLitter?.stripTrailingZeros()?.toPlainString() ?: "—")))
    QuickGrid(listOf("Lotes" to RuralRoutes.SWINE_LOTS, "Registrar peso" to RuralRoutes.SWINE_WEIGHT, "Alimentação" to RuralRoutes.feature("Alimentação suínos"), "Saúde" to RuralRoutes.HEALTH, "Mortalidade" to RuralRoutes.feature("Mortalidade suínos"), "Histórico" to RuralRoutes.HISTORY, "Relatórios" to RuralRoutes.feature("Relatórios suínos")), open)
}
@Composable internal fun BreedingScreen(open: (String) -> Unit) = Page("Suínos — Matrizes") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val animals = remember { AnimalRecordsService(context).animals(scope, AnimalSpecies.SUINO) }; val farrowings = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PARTO_SUINOS } }; val piglets = farrowings.fold(BigDecimal.ZERO) { total, record -> total + (record.quantity ?: BigDecimal.ZERO) }
    MetricGrid(listOf("Matrizes" to animals.count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO }.toString(), "Prenhas" to "A registrar", "Partos" to farrowings.size.toString(), "Leitões" to piglets.stripTrailingZeros().toPlainString()))
    QuickGrid(listOf("Matrizes" to RuralRoutes.SOW_DETAIL, "Cio" to RuralRoutes.feature("Cio suínos"), "Cobertura" to RuralRoutes.feature("Cobertura suínos"), "Inseminação" to RuralRoutes.feature("Inseminação suínos"), "Prenhez" to RuralRoutes.feature("Prenhez suínos"), "Partos" to RuralRoutes.SWINE_FARROWING, "Leitões" to RuralRoutes.feature("Leitões suínos"), "Desmame" to RuralRoutes.SWINE_FARROWING, "Histórico" to RuralRoutes.HISTORY), open)
}
