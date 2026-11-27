package br.com.simplificarural.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.simplificarural.R
import br.com.simplificarural.domain.actions.QuickCommand
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.property.FarmContextStore
import java.math.BigDecimal

internal data class SaleAmountRequest(val description: String, val withAmount: (BigDecimal) -> QuickCommand)

internal fun QuickCommand.requestMissingSaleAmount(context: Context): SaleAmountRequest? = when (this) {
    is QuickCommand.LooseEggSale -> if (total != null) null
        else SaleAmountRequest("Venda de $count ovos") { copy(total = it) }
    is QuickCommand.MilkSale -> if (total != null) null
        else {
            requireMilkSaleProduct(context, this)
            SaleAmountRequest("Venda de ${liters.stripTrailingZeros().toPlainString().replace('.', ',')} litros de leite") { copy(total = it) }
        }
    else -> null
}

internal fun requireMilkSaleProduct(context: Context, command: QuickCommand.MilkSale) {
    if (command.hasProductName) return
    val scope = CashViewScope.SelectedUnit(FarmContextStore(context).current())
    val liquids = FarmManagementService(context).stock(scope)
        .filter { it.quantity.signum() > 0 && it.unit.lowercase() in setOf("l", "litro", "litros") }
        .map { it.productName.lowercase() }.distinct()
    require(liquids == listOf("leite")) { "Qual produto foi vendido? Se foi leite, diga “Vendi ${command.liters.toPlainString()} litros de leite”." }
}

@Composable
internal fun SaleAmountDialog(description: String, back: () -> Unit, onReview: (BigDecimal) -> Unit) {
    var input by remember(description) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = if (result.resultCode == Activity.RESULT_OK)
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
        val amount = spoken?.let(::saleAmount)
        if (amount == null) error = "Diga ou digite o valor total em reais." else {
            input = amount.toPlainString().replace('.', ',')
            error = null
        }
    }
    AlertDialog(
        onDismissRequest = back,
        title = { Text("Quanto você recebeu?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description)
                OutlinedTextField(
                    value = input, onValueChange = { input = it.take(24); error = null },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Valor total recebido (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        runCatching {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                        }.onFailure { error = "Microfone indisponível. Digite o valor total." }
                    }
                ) {
                    Icon(painterResource(R.drawable.ic_rural_microphone), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Falar valor")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = saleAmount(input)
                if (amount == null) error = "Informe o valor total em reais." else onReview(amount)
            }, enabled = input.isNotBlank()) { Text("Revisar") }
        },
        dismissButton = { TextButton(back) { Text("Voltar") } }
    )
}

private fun saleAmount(input: String): BigDecimal? {
    val amount = input.trim().lowercase().removePrefix("r\$").replace("\u00A0", " ").trim()
        .removeSuffix(" reais").removeSuffix(" real").trim()
    if (!Regex("""\d+(?:[,.]\d{1,2})?""").matches(amount)) return null
    return amount.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 && it.precision() <= 12 }
}
