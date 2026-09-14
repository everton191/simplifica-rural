package br.com.simplificarural.ui.inventory

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import br.com.simplificarural.domain.inventory.PackagingConversionService
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.theme.RuralSecondaryText

@Composable internal fun PackagingScreen(back: () -> Unit, message: (String) -> Unit) = Page("Embalagens de ovos", "Defina quantos ovos entram em cada embalagem.", back) { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { PackagingConversionService(context) }; var name by remember { mutableStateOf("Bandeja padrão") }; var eggs by remember { mutableStateOf(service.eggsPerPackage(scope, name).toString()) }; OutlinedTextField(name, { name = it; eggs = service.eggsPerPackage(scope, it).toString() }, Modifier.fillMaxWidth(), label = { Text("Embalagem") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(eggs, { eggs = it }, Modifier.fillMaxWidth(), label = { Text("Ovos por embalagem") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { service.setEggsPerPackage(scope, name, eggs.toInt()) }.onSuccess { message("Conversão salva para $name.") }.onFailure { message("Informe uma embalagem e quantidade válida.") } }, Modifier.fillMaxWidth().height(48.dp)) { Text("Salvar conversão") }; listOf("Meia dúzia" to 6, "Dúzia" to 12, "Bandeja pequena" to 20, "Bandeja padrão" to 30).forEach { (label, amount) -> PressCard({ name = label; eggs = service.eggsPerPackage(scope, label).toString() }) { Text(label, fontWeight = FontWeight.Bold); Text("${service.eggsPerPackage(scope, label)} ovos", color = RuralSecondaryText) } } }
