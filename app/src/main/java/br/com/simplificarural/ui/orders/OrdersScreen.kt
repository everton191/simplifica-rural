package br.com.simplificarural.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.domain.orders.RuralOrderService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.money
import br.com.simplificarural.ui.theme.RuralSecondaryText

@Composable internal fun OrdersScreen(back: () -> Unit, message: (String) -> Unit) = Page("Pedidos", "Pedidos só entram no caixa quando são entregues.", back) { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { RuralOrderService(context) }; var revision by remember { mutableIntStateOf(0) }; val orders = remember(revision) { service.list(scope) }; if (orders.isEmpty()) EmptyState("Sem pedidos", "A secretária cria pedidos quando uma venda precisa ser reagendada.") else orders.forEach { order -> var partial by remember(order.id) { mutableStateOf("") }; PressCard { Text("${order.customer} • ${order.product}", fontWeight = FontWeight.Bold); Text("${order.quantity.stripTrailingZeros().toPlainString()} ${order.unit} • ${money(order.total)} • ${order.status.name.lowercase().replace('_', ' ')}", color = RuralSecondaryText); order.note?.let { Text(it, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton({ service.review(order); revision++ }) { Text("Rever estoque") }; if (order.status == br.com.simplificarural.domain.orders.RuralOrderStatus.PRONTO_PARA_SEPARAR) TextButton({ runCatching { service.delivered(order); revision++ }.onFailure { message(it.message ?: "Não foi possível entregar.") } }) { Text("Entregar") } }; if (order.status in setOf(br.com.simplificarural.domain.orders.RuralOrderStatus.AGENDADO, br.com.simplificarural.domain.orders.RuralOrderStatus.PRONTO_PARA_SEPARAR)) { OutlinedTextField(partial, { partial = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade para entrega parcial") }, shape = RoundedCornerShape(14.dp)); TextButton({ runCatching { service.deliverPartial(order, partial.replace(',', '.').toBigDecimal()); revision++ }.onSuccess { message("Parte entregue; saldo virou novo pedido pendente.") }.onFailure { message(it.message ?: "Quantidade parcial inválida ou estoque insuficiente.") } }) { Text("Entregar parcial") } } } } }
