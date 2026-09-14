package br.com.simplificarural.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.ActivityCard
import br.com.simplificarural.ui.components.FinancialCard
import br.com.simplificarural.ui.components.NoticeCard
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.RecentProductionHistory
import br.com.simplificarural.ui.components.Segment
import java.math.BigDecimal
import java.time.LocalDate

@Composable internal fun HomeScreen(open: (String) -> Unit) = Page("Simplifica Rural", FarmContextStore(LocalContext.current).farmName(), actions = { IconButton({ open(RuralRoutes.AGENDA) }) { Icon(Icons.Default.Notifications, "Avisos") }; IconButton({ open(RuralRoutes.SETTINGS) }) { Icon(Icons.Default.Settings, "Configurações") } }) {
    val context = LocalContext.current
    val farmScope = remember { FarmContextStore(context).current() }; var period by remember { mutableIntStateOf(0) }
    val records = remember(period) { FarmManagementService(context).records(CashViewScope.SelectedUnit(farmScope)) }
    val financial = remember { FarmManagementService(context).financialResult(CashViewScope.SelectedUnit(farmScope)) }
    val start = when (period) { 1 -> LocalDate.now().minusDays(6); 2 -> LocalDate.now().withDayOfMonth(1); else -> LocalDate.now() }
    val eggs = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS && !it.date.isBefore(start) }.sumOf { it.quantity?.toInt() ?: 0 }
    val milk = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE && !it.date.isBefore(start) }.fold(BigDecimal.ZERO) { total, item -> total + (item.quantity ?: BigDecimal.ZERO) }
    val latestBird = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS }.maxByOrNull { it.createdAt }
    val latestCattle = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE }.maxByOrNull { it.createdAt }
    val latestSwine = records.filter { it.type in setOf(br.com.simplificarural.domain.management.ManagementRecordType.PESAGEM_SUINOS, br.com.simplificarural.domain.management.ManagementRecordType.PARTO_SUINOS) }.maxByOrNull { it.createdAt }
    fun latestText(record: br.com.simplificarural.domain.management.ManagementRecord?, empty: String) = record?.let { "Último: ${it.date} • ${it.description}" } ?: empty
    val periodName = listOf("hoje", "na semana", "no mês")[period]
    Segment(listOf("Hoje", "Semana", "Mês")) { period = it }
    Text("Produção $periodName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    ActivityCard("Aves", "$eggs ovos $periodName", latestText(latestBird, "Sem produção confirmada ainda"), null, Icons.Default.Egg, { open(RuralRoutes.BIRDS) })
    ActivityCard("Bovinos", "${milk.stripTrailingZeros().toPlainString()} L $periodName", latestText(latestCattle, "Sem ordenha confirmada ainda"), null, Icons.Default.Pets, { open(RuralRoutes.CATTLE) })
    ActivityCard("Suínos", "Lotes e desempenho", latestText(latestSwine, "Sem pesagem ou parto confirmado"), null, Icons.Default.Pets, { open(RuralRoutes.SWINE) })
    RecentProductionHistory(records, open)
    Text("Gestão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    FinancialCard(financial) { open(RuralRoutes.FINANCE) }
    Text("Atalhos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Button({ open(RuralRoutes.ASSISTANT) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Falar com a secretária") }
    NoticeCard({ open(RuralRoutes.AGENDA) })
}
