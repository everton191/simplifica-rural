package br.com.simplificarural.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import br.com.simplificarural.R
import br.com.simplificarural.domain.property.FarmContextStore
import kotlinx.coroutines.delay

/** A short welcoming cover while the local assistant warms up in the background. */
@androidx.compose.runtime.Composable
fun RuralLaunchScreen() {
    val context = LocalContext.current
    var opening by remember { mutableStateOf(true) }
    var configured by remember { mutableStateOf(FarmContextStore(context).isConfigured()) }
    LaunchedEffect(Unit) { delay(1100); opening = false }
    if (!opening && configured) {
        SimplificaRuralApp()
        return
    }
    if (!opening) {
        FirstUseScreen { name -> FarmContextStore(context).configure(name); configured = true }
        return
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF1F5137)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.ic_simplifica_rural_logo), contentDescription = "Simplifica Rural", modifier = Modifier.height(190.dp))
        Spacer(Modifier.height(22.dp))
        Text("Simplifica Rural", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text("Sua propriedade organizada, dia após dia", color = Color(0xFFE1F0E5))
    }
}

@androidx.compose.runtime.Composable
private fun FirstUseScreen(onFinished: (String) -> Unit) {
    var name by remember { mutableStateOf("") }; var step by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        when (step) {
            0 -> { Text("Vamos organizar sua propriedade", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Comece pelo nome da fazenda. Você poderá criar granjas e setores depois."); Spacer(Modifier.height(16.dp)); OutlinedTextField(name, { name = it }, label = { Text("Nome da fazenda") }); Spacer(Modifier.height(12.dp)); Button({ if (name.trim().length >= 2) step = 1 }) { Text("Continuar") } }
            1 -> { Text("Use a secretária no dia a dia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Fale ou escreva o que aconteceu. Ela mostra um resumo; só confirme quando estiver certo."); Spacer(Modifier.height(12.dp)); Button({ step = 2 }) { Text("Entendi") } }
            else -> { Text("Você também pode registrar manualmente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Use Animais, Estoque, Financeiro e Agenda quando preferir preencher os dados por tela."); Spacer(Modifier.height(12.dp)); Button({ onFinished(name) }) { Text("Abrir minha fazenda") } }
        }
    }
}
