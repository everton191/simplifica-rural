package br.com.simplificarural.ui.inventory

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
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.inventory.StockAlertService
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.management.operationFlows
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.QuickGrid
import br.com.simplificarural.ui.components.SearchField
import br.com.simplificarural.ui.components.StockRow
import br.com.simplificarural.ui.components.itemCategory
import br.com.simplificarural.ui.theme.RuralSecondaryText
import br.com.simplificarural.ui.theme.RuralWarning
import java.math.BigDecimal

@Composable internal fun StockScreen(open: (String) -> Unit) = Page("Estoque") {
    val context = LocalContext.current
    val scope = remember { FarmContextStore(context).current() }
    val stock = FarmManagementService(context).stock(CashViewScope.SelectedUnit(scope)); val alerts = remember(stock) { StockAlertService(context).alerts(scope, stock) }
    var search by remember { mutableStateOf("") }; SearchField("Buscar item") { search = it }
    MetricGrid(listOf("Itens cadastrados" to stock.size.toString(), "Itens baixos" to alerts.size.toString(), "Valor estimado" to "A informar"))
    if (alerts.isNotEmpty()) PressCard({ open(RuralRoutes.stockDetail(alerts.first().product)) }) { Text("Reposição necessária", fontWeight = FontWeight.Bold, color = RuralWarning); alerts.take(3).forEach { Text("${it.product}: ${it.available.stripTrailingZeros().toPlainString()} ${it.unit} (mínimo ${it.minimum.stripTrailingZeros().toPlainString()})", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
    Text("Adicionar por categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    QuickGrid(listOf("Alimentação" to RuralRoutes.stockAdd("Alimentação"), "Saúde" to RuralRoutes.stockAdd("Saúde"), "Ferramentas" to RuralRoutes.stockAdd("Ferramentas"), "Outros" to RuralRoutes.stockAdd("Outros"), "Saída / venda" to RuralRoutes.NEW_SALE), open)
    Text("Itens cadastrados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (stock.isEmpty()) Text("Ainda não há itens no estoque desta unidade.", color = RuralSecondaryText)
    val filtered = stock.filter { it.productName.contains(search, true) }
    if (stock.isNotEmpty() && filtered.isEmpty()) EmptyState("Nenhum item encontrado", "Ajuste a busca ou selecione outra categoria.") else filtered.forEach { item -> StockRow(item.productName, "${item.quantity.stripTrailingZeros().toPlainString()} ${item.unit}", itemCategory(item), item.quantity <= BigDecimal.ZERO) { open(RuralRoutes.stockDetail(item.productName)) } }
}
@Composable internal fun StockDetailScreen(product: String, back: () -> Unit, open: (String) -> Unit) = Page(product, "Histórico do item", back) { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; val records = remember { service.records(CashViewScope.SelectedUnit(scope)).filter { it.productName.equals(product, true) } }; val balance = service.stock(CashViewScope.SelectedUnit(scope)).firstOrNull { it.productName.equals(product, true) }; val alerts = remember { StockAlertService(context) }; var minimum by remember(balance) { mutableStateOf(balance?.let { alerts.minimum(scope, it.productName, it.unit).stripTrailingZeros().toPlainString() }.orEmpty()) }; MetricGrid(listOf("Quantidade atual" to balance?.let { "${it.quantity.stripTrailingZeros().toPlainString()} ${it.unit}" }.orEmpty(), "Categoria" to balance?.let(::itemCategory).orEmpty())); balance?.let { item -> OutlinedTextField(minimum, { minimum = it }, Modifier.fillMaxWidth(), label = { Text("Estoque mínimo (${item.unit})") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { alerts.setMinimum(scope, item.productName, item.unit, minimum.replace(',', '.').toBigDecimal()) } }, Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar mínimo para alerta") } }; Button({ open(RuralRoutes.NEW_PURCHASE) }, Modifier.fillMaxWidth().height(48.dp)) { Text("Adicionar entrada") }; Text("Movimentações", fontWeight = FontWeight.Bold); if (records.isEmpty()) EmptyState("Sem movimentações", "As entradas e saídas confirmadas deste item aparecerão aqui.") else records.forEach { record -> PressCard { Text(record.description, fontWeight = FontWeight.Medium); Text("${record.date} • ${record.operationFlows().joinToString(" + ") { it.name.lowercase().replace('_', ' ') } }", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } } }
