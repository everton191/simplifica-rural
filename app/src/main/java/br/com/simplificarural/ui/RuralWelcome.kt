package br.com.simplificarural.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun RuralWelcome(onInstallModel: (() -> Unit)? = null) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("rural_welcome", Context.MODE_PRIVATE) }
    var visible by remember(preferences) { mutableStateOf(!preferences.getBoolean("seen", false)) }
    fun finish() {
        preferences.edit().putBoolean("seen", true).apply()
        visible = false
    }

    if (visible) AlertDialog(
        onDismissRequest = {},
        title = { Text("Bem-vindo ao Simplifica Rural") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Toque em Lançar para falar ou digitar o que aconteceu.")
                Text("Exemplo: Colhi 30 ovos. Confira os dados e toque em Confirmar para salvar.")
                Text("Na tela Início, veja o resumo do dia e a produção.")
                if (onInstallModel != null) {
                    Text("O LFM 3 ajuda a entender frases mais longas. Use Wi-Fi para baixar cerca de 1,1 GB. Você também pode instalar depois.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                finish()
                onInstallModel?.invoke()
            }) { Text(if (onInstallModel == null) "Começar" else "Instalar LFM 3") }
        },
        dismissButton = {
            if (onInstallModel != null) TextButton(onClick = ::finish) { Text("Agora não") }
        }
    )
}
