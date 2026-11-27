package br.com.simplificarural.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.R
import br.com.simplificarural.data.local.ActivityLogService
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.domain.agenda.AgendaStatus
import br.com.simplificarural.domain.financial.CashAggregator
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.DailyManagementTotals
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DailySummary() {
    val context = LocalContext.current
    val farm = remember(context) { FarmContextStore(context).current() }
    val scope = remember(farm) { CashViewScope.SelectedUnit(farm) }
    val today = LocalDate.now()
    var day by remember { mutableStateOf(today) }
    val audio = rememberRuralAudioGuide()
    val management = remember(context) { FarmManagementService(context) }
    val animals = remember(context) { AnimalRecordsService(context) }
    val recordsByDate = remember(scope) { management.confirmedRecords(scope).groupBy { it.date } }
    val cashByDate = remember(scope) { management.cashEntries(scope).groupBy { it.date } }
    val healthByDate = remember(farm) { animals.healthHistory(farm).groupBy { it.date } }
    val reproductionByDate = remember(farm) { animals.reproductionHistory(farm).groupBy { it.date } }
    val tasksByDate = remember(farm) {
        animals.tasks(farm).filter { it.status != AgendaStatus.CANCELADA }.groupBy { it.dueDate }
    }
    val notesByDate = remember(farm) { ActivityLogService(context).listAll(farm).groupBy { it.createdAt.toLocalDate() } }
    val records = recordsByDate[day].orEmpty()
    val production = remember(records) { DailyManagementTotals.from(records) }
    val cash = remember(day, cashByDate, scope) { CashAggregator.summarize(cashByDate[day].orEmpty(), scope) }
    val health = healthByDate[day].orEmpty()
    val reproduction = reproductionByDate[day].orEmpty()
    val tasks = tasksByDate[day].orEmpty()
    val notes = notesByDate[day].orEmpty()
    val locale = remember { Locale.forLanguageTag("pt-BR") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy", locale) }
    val clockFormat = remember { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val currency = remember { NumberFormat.getCurrencyInstance(locale) }
    val label = when (day) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> day.format(dateFormat)
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ day = day.minusDays(1) }) { Icon(Icons.Default.ChevronLeft, "Dia anterior") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            if (day != today) TextButton({ day = today }) { Text("Voltar para hoje") }
        }
        IconButton({ day = day.plusDays(1) }, enabled = day.isBefore(today)) {
            Icon(Icons.Default.ChevronRight, "Dia seguinte")
        }
    }
    TextButton(onClick = {
        audio.speak(
            "Resumo de ${day.format(dateFormat)}. ${production.eggs.amount()} ovos e ${production.milk.amount()} litros de leite. " +
                "Entrou no caixa ${currency.format(cash.entries)}. Saiu ${currency.format(cash.exits)}. " +
                "${production.stock.size} itens com movimento de estoque."
        )
    }) {
        Icon(painterResource(R.drawable.ic_rural_audio), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Ouvir resumo")
    }

    SummarySection("Produção") {
        SummaryLine("Ovos", "${production.eggs.amount()} ovos")
        SummaryLine("Leite", "${production.milk.amount()} litros")
        if (production.swineBirths > 0 || production.swineWeighings > 0) {
            SummaryLine("Partos de suínos", production.swineBirths.toString())
            SummaryLine("Leitões vivos", production.livePiglets.amount())
            SummaryLine("Pesagens", production.swineWeighings.toString())
        }
    }
    SummarySection("Caixa") {
        SummaryLine("Entrou", currency.format(cash.entries))
        SummaryLine("Saiu", currency.format(cash.exits))
        SummaryLine("Resultado do dia", currency.format(cash.balance))
    }
    SummarySection("Estoque") {
        if (production.stock.isEmpty()) Text("Sem movimentação neste dia.")
        production.stock.forEach { item ->
            Text(item.product, fontWeight = FontWeight.Medium)
            SummaryLine("Entrou", "${item.incoming.amount()} ${item.unit}")
            SummaryLine("Saiu", "${item.outgoing.amount()} ${item.unit}")
        }
    }
    if (health.isNotEmpty() || reproduction.isNotEmpty()) {
        SummarySection("Cuidados com os animais") {
            SummaryLine("Registros de saúde", health.size.toString())
            SummaryLine("Registros de reprodução", reproduction.size.toString())
            health.forEach { Text(it.productOrCondition) }
            reproduction.forEach { Text(it.stage.name.lowercase().replace('_', ' ')) }
        }
    }
    if (tasks.isNotEmpty()) SummarySection("Agenda do dia") { tasks.forEach { Text(it.title) } }
    if (records.isNotEmpty() || notes.isNotEmpty()) {
        SummarySection("Lançamentos do dia") {
            records.forEach { record -> SummaryLine(record.description, record.createdAt.format(clockFormat)) }
            notes.forEach { note -> SummaryLine(note.description, note.createdAt.format(clockFormat)) }
        }
    }
}

@Composable
private fun SummarySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun BigDecimal.amount(): String = stripTrailingZeros().toPlainString().replace('.', ',')
