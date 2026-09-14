@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package br.com.simplificarural.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.simplificarural.R
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.theme.*
import java.math.BigDecimal
import java.time.LocalDate

@Composable fun Page(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); subtitle?.let { Text(it, color = RuralSecondaryText) } }
                actions()
            }
        }
        item { GuideTip(title) }
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}
@Composable fun GuideTip(title: String) { val text = when (title) { "Simplifica Rural" -> "Aqui você acompanha produção, saldo e atalhos. Toque nos cards para abrir cada área."; "Animais" -> "Cadastre animais e lotes aqui. Toque em uma criação para ver seus registros."; "Estoque" -> "Registre compras por categoria e consulte cada item para ver entradas e saídas."; "Financeiro" -> "Aqui você adiciona entradas, despesas, compras e vendas. Use Caixa geral para juntar as unidades."; "Agenda" -> "Crie lembretes para vacina, manutenção, pagamentos e tarefas da propriedade."; "Produção" -> "Os gráficos mostram somente lançamentos confirmados de ovos, leite e pesagens."; else -> null }; if (text != null) { val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("guide_tips", android.content.Context.MODE_PRIVATE) }; val key = "opens_${title}"; val count = remember(title) { prefs.getInt(key, 0) }; LaunchedEffect(title) { prefs.edit().putInt(key, count + 1).apply() }; if (count < 10) PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lightbulb, null, tint = RuralGreen); Spacer(Modifier.width(10.dp)); Text(text, Modifier.weight(1f), color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } } } }

@Composable fun PressCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState(); val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(120), label = "card"); var expanded by remember { mutableStateOf(false) }; val action = onClick ?: { expanded = !expanded }
    Card(Modifier.fillMaxWidth().scale(scale).clickable(interactionSource = interaction, indication = null, onClick = action), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE8EEE9)), elevation = CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { content(); if (onClick == null && expanded) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, Modifier.size(15.dp), tint = RuralGreen); Spacer(Modifier.width(6.dp)); Text("Detalhes exibidos acima. Toque novamente para recolher.", color = RuralSecondaryText, style = MaterialTheme.typography.labelSmall) } } }
}
@Composable fun RecentProductionHistory(records: List<br.com.simplificarural.domain.management.ManagementRecord>, open: (String) -> Unit) {
    val production = records.filter { it.type in setOf(
        br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS,
        br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE,
        br.com.simplificarural.domain.management.ManagementRecordType.PESAGEM_SUINOS,
        br.com.simplificarural.domain.management.ManagementRecordType.PARTO_SUINOS
    ) }.sortedByDescending { it.createdAt }.take(5)
    Text("Últimas alterações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (production.isEmpty()) EmptyState("Sem produção registrada", "Os lançamentos confirmados de ovos, leite, pesagens e partos aparecerão aqui.") else production.forEach { record ->
        val route = when (record.type) {
            br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS -> RuralRoutes.BIRDS
            br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE -> RuralRoutes.CATTLE
            else -> RuralRoutes.SWINE
        }
        PressCard({ open(route) }) { Text(record.description, fontWeight = FontWeight.Bold); Text("${record.date} • ${record.quantity?.stripTrailingZeros()?.toPlainString().orEmpty()} ${record.unit.orEmpty()}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable fun ActivityCard(title: String, headline: String, detail: String, value: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit, action: String = "Ver") = PressCard(click) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(10.dp), color = RuralLightGreen) { RuralAnimalIllustration(title, icon, Modifier.padding(3.dp).size(44.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Text(headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(detail, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ArrowForward, action, tint = RuralGreen, modifier = Modifier.size(22.dp)) }; value?.let { Text("Valor estimado: $it", color = RuralSuccess, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall) } }
@Composable fun RuralAnimalIllustration(title: String, fallback: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val resource = when (title) { "Aves" -> R.drawable.illust_aves; "Bovinos" -> R.drawable.illust_bovinos; "Suínos" -> R.drawable.illust_suinos; else -> null }
    if (resource == null) Icon(fallback, title, modifier, tint = RuralGreen) else androidx.compose.foundation.Image(painterResource(resource), title, modifier)
}
@Composable fun FinancialCard(result: br.com.simplificarural.domain.management.FinancialResult, click: () -> Unit) = PressCard(click) { Text("Financeiro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MiniMetric("Entradas", money(result.revenue), RuralSuccess); MiniMetric("Saídas", money(result.totalCost), RuralDanger); MiniMetric("Resultado", money(result.cashGeneration), RuralDarkGreen) } }
@Composable fun NoticeCard(open: () -> Unit) = PressCard(open) { Row { Text("Avisos e agenda", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Abrir agenda", color = RuralGreen) }; Text("Os retornos de saúde e lembretes confirmados aparecem aqui conforme forem registrados.", color = RuralSecondaryText) }
@Composable fun ProductionChart(title: String, values: List<BigDecimal>, unit: String, dates: List<LocalDate>) { val total = values.fold(BigDecimal.ZERO, BigDecimal::plus); val max = values.maxOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE; PressCard { Text(title, fontWeight = FontWeight.Bold); Text("${total.stripTrailingZeros().toPlainString()} $unit no período", color = RuralDarkGreen, fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) { values.forEachIndexed { index, value -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) { Box(Modifier.fillMaxWidth().height((72f * value.divide(max, 3, java.math.RoundingMode.HALF_UP).toFloat()).coerceAtLeast(3f).dp), contentAlignment = Alignment.TopCenter) { Surface(Modifier.fillMaxSize(), color = RuralGreen, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) {} }; if (dates.size <= 7) Text(dates.getOrNull(index)?.dayOfMonth?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall, color = RuralSecondaryText) } } } } }
@Composable fun MetricGrid(metrics: List<Pair<String, String>>) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { metrics.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { (label, value) -> var expanded by remember(label, value) { mutableStateOf(false) }; Card(Modifier.weight(1f).clickable { expanded = !expanded }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE8EEE9))) { Column(Modifier.padding(10.dp)) { Text(label, color = RuralSecondaryText, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (expanded) Text("Indicador calculado a partir dos registros desta área.", color = RuralSecondaryText, style = MaterialTheme.typography.labelSmall) } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } } }
@Composable fun QuickGrid(items: List<Pair<String, String>>, open: (String) -> Unit) { var moreOpen by remember { mutableStateOf(false) }; val primary = if (items.size > 4) items.take(3) + ("Mais" to "") else items.take(4); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { primary.forEach { (name, route) -> if (name == "Mais") Box(Modifier.weight(1f)) { ActionCard(name, null, Icons.Default.MoreHoriz, Modifier.fillMaxWidth()) { moreOpen = true }; DropdownMenu(moreOpen, { moreOpen = false }) { items.drop(3).forEach { (extraName, extraRoute) -> val target = if (extraRoute == RuralRoutes.GENERIC) RuralRoutes.feature(extraName) else extraRoute; DropdownMenuItem({ Text(extraName) }, { moreOpen = false; open(target) }) } } } else { val target = if (route == RuralRoutes.GENERIC) RuralRoutes.feature(name) else route; ActionCard(name, target, Icons.Default.AddCircle, Modifier.weight(1f)) { open(target) } } }; repeat(4 - primary.size) { Spacer(Modifier.weight(1f)) } } }
@Composable fun ActionCard(title: String, route: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, click: () -> Unit) { val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState(); val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(120), label = "action"); Card(modifier.heightIn(min = 58.dp).scale(scale).clickable(interactionSource = interaction, indication = null, onClick = click), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE8EEE9))) { Column(Modifier.fillMaxSize().padding(vertical = 5.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { RuralVectorIcon(title, route, icon, Modifier.size(19.dp)); Spacer(Modifier.height(2.dp)); Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) } } }
@Composable fun RuralVectorIcon(label: String, route: String? = null, fallback: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val resource = when {
        route == RuralRoutes.BIRD_LOTS -> R.drawable.ic_rural_birds
        route == RuralRoutes.SWINE_LOTS || route == RuralRoutes.SWINE_FATTENING || route == RuralRoutes.SWINE_BREEDING || route == RuralRoutes.SOW_DETAIL -> R.drawable.ic_rural_swine
        route == RuralRoutes.ASSISTANT -> R.drawable.ic_rural_assistant
        route == RuralRoutes.AGENDA -> R.drawable.ic_rural_agenda
        route == RuralRoutes.HEALTH -> R.drawable.ic_rural_health
        route == RuralRoutes.STOCK || route == RuralRoutes.STOCK_DETAIL -> R.drawable.ic_rural_stock
        route == RuralRoutes.PURCHASES || route == RuralRoutes.NEW_PURCHASE -> R.drawable.ic_rural_stock
        route == RuralRoutes.SALES -> R.drawable.ic_rural_sale
        route == RuralRoutes.PRODUCTION -> R.drawable.ic_rural_production
        label.contains("ovos", true) -> R.drawable.ic_rural_egg
        label.contains("Aves", true) -> R.drawable.ic_rural_birds
        label.contains("Lotes", true) || label.contains("Animais", true) || label.contains("Leitões", true) -> R.drawable.ic_rural_lot
        label.contains("Alimentação", true) || label.contains("ração", true) -> R.drawable.ic_rural_feed
        label.contains("leite", true) -> R.drawable.ic_rural_milk
        label.contains("Bov", true) || label.contains("vaca", true) -> R.drawable.ic_rural_cattle
        label.contains("Suín", true) || label.contains("Matriz", true) || label.contains("peso", true) || label.contains("Partos", true) -> R.drawable.ic_rural_swine
        label.contains("Reprodução", true) || label.contains("Cio", true) || label.contains("Cobertura", true) || label.contains("Inseminação", true) || label.contains("Prenhez", true) || label.contains("Desmame", true) -> R.drawable.ic_rural_reproduction
        label.contains("Histórico", true) -> R.drawable.ic_rural_history
        label.contains("Ocorrências", true) || label.contains("Mortalidade", true) -> R.drawable.ic_rural_alert
        label.contains("Contas", true) -> R.drawable.ic_rural_bill
        label.contains("Entrada", true) || label.contains("receber", true) -> R.drawable.ic_rural_stock_in
        label.contains("Saída", true) || label.contains("pagar", true) -> R.drawable.ic_rural_stock_out
        label.contains("Backup", true) -> R.drawable.ic_rural_backup
        label.contains("Assistente", true) -> R.drawable.ic_rural_assistant
        label.contains("Vendas", true) || label.contains("venda", true) -> R.drawable.ic_rural_sale
        label.contains("Estoque", true) || label.contains("compra", true) || label.contains("Inventário", true) -> R.drawable.ic_rural_stock
        label.contains("Saúde", true) || label.contains("vacina", true) -> R.drawable.ic_rural_health
        label.contains("Produção", true) || label.contains("Relatórios", true) || label.contains("Indicadores", true) -> R.drawable.ic_rural_production
        label.contains("Agenda", true) || label.contains("Avisos", true) -> R.drawable.ic_rural_agenda
        label.contains("Financeiro", true) || label.contains("entrada", true) || label.contains("despesa", true) || label.contains("Vendas", true) -> R.drawable.ic_rural_finance
        else -> null
    }
    if (resource != null) androidx.compose.foundation.Image(painterResource(resource), label, modifier) else Icon(fallback, label, modifier, tint = RuralGreen)
}
@Composable fun Segment(labels: List<String>, onSelected: (Int) -> Unit = {}) { var selected by remember { mutableIntStateOf(0) }; SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { labels.forEachIndexed { index, label -> SegmentedButton(selected = selected == index, onClick = { selected = index; onSelected(index) }, shape = SegmentedButtonDefaults.itemShape(index, labels.size)) { Text(label) } } } }
@Composable fun SearchField(placeholder: String, onQueryChanged: (String) -> Unit = {}) { var text by remember { mutableStateOf("") }; OutlinedTextField(text, { text = it; onQueryChanged(it) }, Modifier.fillMaxWidth(), placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp)) }

@Composable fun NutritionNumberField(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, suffix = { Text("kg") }, singleLine = true, shape = RoundedCornerShape(14.dp))
@Composable fun SimpleText(label: String, value: String) = Row { Text(label, Modifier.weight(1f), color = RuralSecondaryText); Text(value, fontWeight = FontWeight.Medium) }
@Composable fun StatusChip(text: String, color: Color) = Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(20.dp)) { Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium) }
@Composable fun StockRow(name: String, quantity: String, status: String, warning: Boolean, click: () -> Unit) = PressCard(click) { Row(verticalAlignment = Alignment.CenterVertically) { RuralVectorIcon(name, RuralRoutes.STOCK_DETAIL, Icons.Default.Inventory2, Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(quantity, color = RuralSecondaryText) }; StatusChip(status, if (warning) RuralWarning else RuralSuccess) } }
@Composable fun AgendaRow(title: String, subtitle: String, date: String, color: Color) = PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Event, null, tint = color); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = RuralSecondaryText) }; StatusChip(date, color) } }
@Composable fun ExpandableDetail(title: String, detail: String) { var expanded by remember { mutableStateOf(false) }; PressCard { Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Recolher" else "Expandir", tint = RuralGreen) }; if (expanded) Text(detail, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
@Composable fun MoreGroup(title: String, entries: List<Pair<String, String>>, open: (String) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); entries.forEach { (name, route) -> val target = if (route == RuralRoutes.GENERIC) RuralRoutes.feature(name) else route; PressCard({ open(target) }) { Row(verticalAlignment = Alignment.CenterVertically) { RuralVectorIcon(name, target, Icons.Default.GridView, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(name, Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = RuralSecondaryText) } } } }
@Composable fun EmptyState(title: String, detail: String) = PressCard { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, color = RuralSecondaryText) }

@Composable fun MiniMetric(label: String, value: String, color: Color) = Column { Text(label, color = RuralSecondaryText, style = MaterialTheme.typography.labelSmall); Text(value, color = color, fontWeight = FontWeight.Bold) }
