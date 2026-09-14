package br.com.simplificarural.ui.financial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.money
import br.com.simplificarural.ui.theme.RuralDanger
import br.com.simplificarural.ui.theme.RuralSecondaryText
import br.com.simplificarural.ui.theme.RuralSuccess

@Composable internal fun FinancialFormScreen(title: String, button: String, back: () -> Unit, message: (String) -> Unit) = Page(title, "O lançamento será incluído no caixa da unidade.", back) { val context = LocalContext.current; var description by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; val isIncome = title.contains("entrada", true); OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descrição") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Valor (R$)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { val service = FarmManagementService(context); val scope = FarmContextStore(context).current(); val value = amount.replace(',', '.').toBigDecimal(); if (isIncome) service.registerOtherIncome(scope, value, description = description) else service.registerExpense(scope, br.com.simplificarural.domain.management.FinancialCategory.OUTRA_DESPESA, value, description = description) }.onSuccess { message("Lançamento salvo no caixa."); back() }.onFailure { message("Informe descrição e valor válido.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text(button) } }
@Composable internal fun FinancialHistoryRow(entry: br.com.simplificarural.domain.financial.CashEntry) { var expanded by remember { mutableStateOf(false) }; PressCard { Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(entry.description, fontWeight = FontWeight.Medium); Text(entry.date.toString(), style = MaterialTheme.typography.bodySmall, color = RuralSecondaryText) }; Text(money(entry.amount), color = if (entry.kind == br.com.simplificarural.domain.financial.CashEntryKind.ENTRADA) RuralSuccess else RuralDanger, fontWeight = FontWeight.Bold); Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Recolher" else "Detalhes", tint = RuralSecondaryText) }; if (expanded) Text("${if (entry.kind == br.com.simplificarural.domain.financial.CashEntryKind.ENTRADA) "Entrada" else "Saída"} registrada no caixa em ${entry.date}.", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
