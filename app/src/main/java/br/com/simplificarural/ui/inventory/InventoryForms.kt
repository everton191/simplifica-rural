package br.com.simplificarural.ui.inventory

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.Segment

@Composable internal fun StockAddScreen(back: () -> Unit, message: (String) -> Unit, initialCategory: String? = null) = Page("Adicionar ao estoque", "A data e a hora serão registradas automaticamente.", back) {
    val context = LocalContext.current; var product by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var unit by remember { mutableStateOf("kg") }; var price by remember { mutableStateOf("") }; val categories = listOf("Alimentação", "Saúde", "Ferramentas", "Outros"); var categoryIndex by remember(initialCategory) { mutableIntStateOf(categories.indexOf(initialCategory).takeIf { it >= 0 } ?: 0) }
    Text("Categoria", fontWeight = FontWeight.Bold); Segment(categories) { categoryIndex = it }
    OutlinedTextField(product, { product = it }, Modifier.fillMaxWidth(), label = { Text("Produto") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(unit, { unit = it }, Modifier.fillMaxWidth(), label = { Text("Unidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Preço por unidade (R$)") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { val selectedCategory = categories[categoryIndex]; val financialCategory = when (selectedCategory) { "Alimentação" -> br.com.simplificarural.domain.management.FinancialCategory.RACAO; "Saúde" -> br.com.simplificarural.domain.management.FinancialCategory.SANIDADE; "Ferramentas" -> br.com.simplificarural.domain.management.FinancialCategory.MANUTENCAO; else -> br.com.simplificarural.domain.management.FinancialCategory.OUTRA_DESPESA }; FarmManagementService(context).registerPurchase(FarmContextStore(context).current(), product, quantity.replace(',', '.').toBigDecimal(), unit, price.replace(',', '.').toBigDecimal(), financialCategory, inventoryCategory = selectedCategory) }.onSuccess { message("Item adicionado com data e horário atuais."); back() }.onFailure { message("Informe produto, quantidade e preço válidos.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Adicionar ao estoque") }
}
@Composable internal fun PurchaseFormScreen(back: () -> Unit, message: (String) -> Unit) = StockAddScreen(back, message)
@Composable internal fun SaleFormScreen(back: () -> Unit, message: (String) -> Unit) = Page("Nova venda", "Atualiza estoque e caixa ao salvar.", back) { val context = LocalContext.current; var product by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var unit by remember { mutableStateOf("unidades") }; var price by remember { mutableStateOf("") }; OutlinedTextField(product, { product = it }, Modifier.fillMaxWidth(), label = { Text("Produto") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(unit, { unit = it }, Modifier.fillMaxWidth(), label = { Text("Unidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Preço por unidade (R$)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { val service = FarmManagementService(context); val amount = quantity.replace(',', '.').toBigDecimal(); val unitPrice = price.replace(',', '.').toBigDecimal(); if (product.contains("bandeja", true) || product.contains("cartela", true)) service.registerEggTraySale(FarmContextStore(context).current(), product, amount, unit, unitPrice) else service.registerSale(FarmContextStore(context).current(), product, amount, unit, unitPrice) }.onSuccess { message("Venda salva no estoque e caixa."); back() }.onFailure { message(it.message ?: "Informe produto, quantidade e preço válidos.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar venda") } }
