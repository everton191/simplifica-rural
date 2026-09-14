@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package br.com.simplificarural.ui.cattle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import br.com.simplificarural.data.local.CattleManagementService
import br.com.simplificarural.data.local.MilkSecretaryService
import br.com.simplificarural.data.local.MilkShift
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.MetricGrid
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.QuickGrid
import br.com.simplificarural.ui.components.Segment
import br.com.simplificarural.ui.components.decimal
import br.com.simplificarural.ui.components.money
import br.com.simplificarural.ui.theme.RuralDarkGreen
import br.com.simplificarural.ui.theme.RuralSecondaryText
import br.com.simplificarural.ui.theme.RuralSuccess
import java.math.BigDecimal
import java.time.LocalDate

@Composable internal fun CattleScreen(open: (String) -> Unit) = Page("Bovinos") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val dashboard = remember { CattleManagementService(context).dashboard(scope) }; val milk = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE && it.date == LocalDate.now() }.fold(BigDecimal.ZERO) { total, record -> total + (record.quantity ?: BigDecimal.ZERO) } }
    MetricGrid(listOf("Total" to dashboard.totalCattle.toString(), "Em lactação" to dashboard.lactatingCattle.toString(), "Leite hoje" to "${milk.stripTrailingZeros().toPlainString()} L", "Média/vaca" to "${dashboard.averageMilkPerLactatingCow.stripTrailingZeros().toPlainString()} L"))
    QuickGrid(listOf("Registrar leite" to RuralRoutes.CATTLE_MILK, "Fechar leite" to RuralRoutes.CATTLE_MILK_CLOSURE, "Animais" to RuralRoutes.CATTLE_LIST, "Alimentação" to RuralRoutes.CATTLE_FEED, "Reprodução" to RuralRoutes.CATTLE_REPRODUCTION, "Saúde" to RuralRoutes.HEALTH, "Histórico" to RuralRoutes.HISTORY, "Relatórios" to RuralRoutes.feature("Relatórios bovinos")), open)
    MilkDiaryTable(MilkSecretaryService(context), scope)
}
@Composable internal fun MilkRegistrationScreen(back: () -> Unit, message: (String) -> Unit) = Page("Registrar leite", "Data e hora são registradas automaticamente.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val cattle = remember { CattleManagementService(context) }; val cows = remember { cattle.cows(scope) }; val now = java.time.LocalDateTime.now()
    var selectedCow by remember { mutableStateOf("") }; var liters by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    if (cows.isNotEmpty()) { var expanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(expanded, { expanded = it }) { OutlinedTextField(selectedCow, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Vaca (opcional)") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, shape = RoundedCornerShape(14.dp)); ExposedDropdownMenu(expanded, { expanded = false }) { cows.forEach { cow -> DropdownMenuItem({ Text("${cow.name} • ${cow.earTag}") }, { selectedCow = cow.id; expanded = false }) } } } } else OutlinedTextField(selectedCow, { selectedCow = it }, Modifier.fillMaxWidth(), label = { Text("Identificação da vaca (opcional)") }, shape = RoundedCornerShape(14.dp))
    OutlinedTextField(liters, { liters = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade ordenhada") }, suffix = { Text("litros") }, singleLine = true, shape = RoundedCornerShape(14.dp)); Text("Horário do lançamento: ${now.toLocalTime().withSecond(0).withNano(0)}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall); OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Observação") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { val amount = liters.decimal(); require(amount > BigDecimal.ZERO); FarmManagementService(context).registerMilkProduction(scope, amount); val shift = MilkShift.automatic(now.hour); MilkSecretaryService(context).record(scope, amount, shift, now); cows.firstOrNull { it.id == selectedCow }?.let { cow -> cattle.registerMilk(cow.id, if (shift == MilkShift.MANHA) amount else BigDecimal.ZERO, if (shift == MilkShift.TARDE) amount else BigDecimal.ZERO, if (shift == MilkShift.NOITE) amount else BigDecimal.ZERO, notes = notes.ifBlank { null }) } }.onSuccess { message("Ordenha salva com horário automático."); back() }.onFailure { message("Informe uma quantidade válida.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar ordenha") }
}
@Composable internal fun MilkClosureScreen(back: () -> Unit, message: (String) -> Unit) = Page("Fechar caderneta de leite", "Fechamento com a empresa; vendas avulsas ficam fora deste valor.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val management = remember { FarmManagementService(context) }; var period by remember { mutableIntStateOf(1) }; var price by remember { mutableStateOf("") }; var company by remember { mutableStateOf("") }; var confirmed by remember { mutableStateOf(false) }
    Segment(listOf("Semanal", "Quinzenal", "Mensal")) { period = it; confirmed = false }; val start = when (period) { 0 -> LocalDate.now().minusDays(6); 1 -> LocalDate.now().minusDays(14); else -> LocalDate.now().withDayOfMonth(1) }; val produced = management.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE && !it.date.isBefore(start) }.fold(BigDecimal.ZERO) { sum, item -> sum + (item.quantity ?: BigDecimal.ZERO) }; val available = management.stock(CashViewScope.SelectedUnit(scope)).firstOrNull { it.productName.equals("Leite", true) }?.quantity?.coerceAtLeast(BigDecimal.ZERO) ?: BigDecimal.ZERO; val liters = minOf(produced, available); val total = runCatching { liters * price.replace(',', '.').toBigDecimal() }.getOrDefault(BigDecimal.ZERO)
    MetricGrid(listOf("Caderneta" to "${produced.stripTrailingZeros().toPlainString()} L", "Disponível p/ empresa" to "${liters.stripTrailingZeros().toPlainString()} L")); OutlinedTextField(company, { company = it; confirmed = false }, Modifier.fillMaxWidth(), label = { Text("Empresa compradora") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(price, { price = it; confirmed = false }, Modifier.fillMaxWidth(), label = { Text("Preço por litro (R$)") }, shape = RoundedCornerShape(14.dp)); if (price.isNotBlank()) PressCard { Text("${liters.stripTrailingZeros().toPlainString()} L × ${money(price.replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO)}", fontWeight = FontWeight.Bold); Text("Valor a receber da empresa: ${money(total)}", color = RuralSuccess) }; Button({ confirmed = true }, Modifier.fillMaxWidth().height(48.dp)) { Text("Confirmar valor a receber") }; if (confirmed) Button({ runCatching { require(company.isNotBlank()); management.registerSale(scope, "Leite", liters, "litros", price.replace(',', '.').toBigDecimal(), description = "Fechamento ${if (period == 0) "semanal" else if (period == 1) "quinzenal" else "mensal"} • $company") }.onSuccess { message("Fechamento salvo como receita da empresa no caixa."); back() }.onFailure { message(it.message ?: "Confira empresa, litros e preço.") } }, Modifier.fillMaxWidth().height(48.dp)) { Text("Salvar fechamento no caixa") } }
@Composable private fun MilkDiaryTable(secretary: MilkSecretaryService, scope: br.com.simplificarural.domain.property.FarmScope) {
    val today = LocalDate.now(); var view by remember { mutableIntStateOf(0) }; var closing by remember { mutableStateOf(secretary.closingDay(scope)) }; var selectedDay by remember { mutableStateOf(today) }; var closingMenu by remember { mutableStateOf(false) }; val records = secretary.records(scope); val start = when (view) { 0 -> today; 1 -> today.minusDays(((today.dayOfWeek.value - closing.value + 7) % 7).toLong()); else -> today.withDayOfMonth(1) }; val days = generateSequence(start) { it.plusDays(1).takeIf { next -> !next.isAfter(today) } }.toList(); val selected = selectedDay.takeIf { it in days } ?: today; val byDay = records.groupBy { it.recordedAt.toLocalDate() }; val periodRecords = records.filter { !it.recordedAt.toLocalDate().isBefore(start) && !it.recordedAt.toLocalDate().isAfter(today) }; val total = periodRecords.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.liters }; fun dayName(day: java.time.DayOfWeek) = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")[day.value - 1]
    Text("Caderneta de ordenhas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Segment(listOf("Hoje", "Semana", "Mês")) { view = it; selectedDay = today }
    if (view == 1) Row(verticalAlignment = Alignment.CenterVertically) { Text("Fechamento: ${dayName(closing)}", Modifier.weight(1f), color = RuralSecondaryText); Box { TextButton({ closingMenu = true }) { Text("Alterar") }; DropdownMenu(closingMenu, { closingMenu = false }) { java.time.DayOfWeek.entries.forEach { day -> DropdownMenuItem({ Text(dayName(day)) }, { closing = day; secretary.setClosingDay(scope, day); closingMenu = false; selectedDay = today }) } } } }
    Text(if (view == 0) "Detalhe de hoje" else "${start.dayOfMonth.toString().padStart(2, '0')}/${start.monthValue.toString().padStart(2, '0')} a ${today.dayOfMonth.toString().padStart(2, '0')}/${today.monthValue.toString().padStart(2, '0')}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall); if (view > 0) MetricGrid(listOf(if (view == 1) "Total da semana" to "${total.stripTrailingZeros().toPlainString()} L" else "Total do mês" to "${total.stripTrailingZeros().toPlainString()} L"))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(days) { day -> val dayTotal = byDay[day].orEmpty().fold(BigDecimal.ZERO) { sum, entry -> sum + entry.liters }; FilterChip(selected = day == selected, onClick = { selectedDay = day }, label = { Text("${dayName(day.dayOfWeek)}\n${day.dayOfMonth.toString().padStart(2, '0')} • ${dayTotal.stripTrailingZeros().toPlainString()}L") }) } }
    val entries = byDay[selected].orEmpty().sortedBy { it.recordedAt }; PressCard { Text("${dayName(selected.dayOfWeek)} ${selected.dayOfMonth.toString().padStart(2, '0')}/${selected.monthValue.toString().padStart(2, '0')}", fontWeight = FontWeight.Bold); if (entries.isEmpty()) Text("Sem ordenha registrada neste dia.", color = RuralSecondaryText) else entries.forEach { entry -> Row { Text(entry.recordedAt.toLocalTime().withSecond(0).withNano(0).toString(), Modifier.weight(1f)); Text("${entry.liters.stripTrailingZeros().toPlainString()} L", fontWeight = FontWeight.Bold) } }; HorizontalDivider(); Row { Text("Total do dia", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("${entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.liters }.stripTrailingZeros().toPlainString()} L", color = RuralDarkGreen, fontWeight = FontWeight.Bold) } }
}
