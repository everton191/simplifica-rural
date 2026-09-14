package br.com.simplificarural.ui.financial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.QuickGrid
import br.com.simplificarural.ui.components.Segment
import br.com.simplificarural.ui.components.money
import br.com.simplificarural.ui.theme.RuralDanger
import br.com.simplificarural.ui.theme.RuralSecondaryText
import java.math.BigDecimal

@Composable internal fun FinanceScreen(open: (String) -> Unit) = Page("Financeiro") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; var general by remember { mutableStateOf(false) }; val viewScope: CashViewScope = if (general) CashViewScope.General(scope.organizationId) else CashViewScope.SelectedUnit(scope); val result = remember(general) { service.financialResult(viewScope) }; val entries = remember(general) { service.cashEntries(viewScope) }
    Segment(listOf("Esta unidade", "Caixa geral")) { general = it == 1 }
    MetricGrid(listOf("Resultado" to money(result.netProfit), "Entradas" to money(result.revenue), "Custos" to money(result.totalCost), "Caixa" to money(result.cashGeneration)))
    QuickGrid(listOf("Nova entrada" to RuralRoutes.ENTRY, "Nova despesa" to RuralRoutes.EXPENSE, "Compras" to RuralRoutes.PURCHASES, "Vendas" to RuralRoutes.SALES), open)
    Text("Histórico financeiro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (entries.isEmpty()) EmptyState("Sem lançamentos financeiros", "Compras, vendas e despesas confirmadas aparecerão aqui.") else entries.take(8).forEach { entry -> FinancialHistoryRow(entry) }
}
@Composable internal fun PurchasesScreen(open: (String) -> Unit, message: (String) -> Unit) = Page("Compras") { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; var revision by remember { mutableIntStateOf(0) }; val records = remember(revision) { service.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.COMPRA } }; MetricGrid(listOf("Compras" to records.count { !service.isCancelled(it) }.toString(), "Total gasto" to money(records.filterNot(service::isCancelled).fold(BigDecimal.ZERO) { total, record -> total + (record.totalAmount ?: BigDecimal.ZERO) }))); Button({ open(RuralRoutes.NEW_PURCHASE) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova compra") }; if (records.isEmpty()) EmptyState("Sem compras", "Cadastre a primeira compra para atualizar estoque e caixa.") else records.forEach { record -> AuditableRecordRow(record, service.isCancelled(record), { reason -> runCatching { service.cancel(scope, record.id, reason); revision++ }.onSuccess { message("Compra cancelada com estorno registrado.") }.onFailure { message(it.message ?: "Não foi possível cancelar.") } }) } }
@Composable internal fun SalesScreen(open: (String) -> Unit, message: (String) -> Unit) = Page("Vendas") { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; var revision by remember { mutableIntStateOf(0) }; val records = remember(revision) { service.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.VENDA && it.productName != null } }; MetricGrid(listOf("Vendas" to records.count { !service.isCancelled(it) }.toString(), "Recebido" to money(records.filterNot(service::isCancelled).fold(BigDecimal.ZERO) { total, record -> total + (record.totalAmount ?: BigDecimal.ZERO) }))); Button({ open(RuralRoutes.NEW_SALE) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova venda") }; if (records.isEmpty()) EmptyState("Sem vendas", "Registre uma venda para atualizar o estoque e caixa.") else records.forEach { record -> AuditableRecordRow(record, service.isCancelled(record), { reason -> runCatching { service.cancel(scope, record.id, reason); revision++ }.onSuccess { message("Venda cancelada com estorno registrado.") }.onFailure { message(it.message ?: "Não foi possível cancelar.") } }) } }
@Composable private fun AuditableRecordRow(record: br.com.simplificarural.domain.management.ManagementRecord, cancelled: Boolean, cancel: (String) -> Unit) { var menu by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }; PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(record.description, fontWeight = FontWeight.Bold); Text("${record.quantity?.stripTrailingZeros()?.toPlainString().orEmpty()} ${record.unit.orEmpty()} • ${money(record.totalAmount ?: BigDecimal.ZERO)}", color = RuralSecondaryText); if (cancelled) Text("Cancelado — mantido para auditoria", color = RuralDanger, style = MaterialTheme.typography.bodySmall) }; if (!cancelled) Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mais ações") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Cancelar lançamento") }, { menu = false; confirm = true }) } } } }; if (confirm) AlertDialog({ confirm = false }, title = { Text("Cancelar lançamento?") }, text = { Text("O lançamento ficará no histórico e deixará de contar no caixa e estoque.") }, confirmButton = { TextButton({ cancel("Cancelado pelo usuário"); confirm = false }) { Text("Confirmar") } }, dismissButton = { TextButton({ confirm = false }) { Text("Voltar") } }) }
