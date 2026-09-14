package br.com.simplificarural.ui.animals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.StatusChip
import br.com.simplificarural.ui.theme.RuralSecondaryText
import br.com.simplificarural.ui.theme.RuralSuccess

@Composable internal fun LotsScreen(title: String, back: () -> Unit, message: (String) -> Unit) = Page(title, onBack = back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val species = if (title.contains("aves", true)) AnimalSpecies.AVE else AnimalSpecies.SUINO
    var lotName by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var revision by remember { mutableIntStateOf(0) }; var mortalityLotId by remember { mutableStateOf<String?>(null) }; var mortality by remember { mutableStateOf("") }; var cause by remember { mutableStateOf("") }; val lots = remember(revision) { AnimalRecordsService(context).batches(scope, species) }
    OutlinedTextField(lotName, { lotName = it }, Modifier.fillMaxWidth(), label = { Text("Nome ou identificação do lote") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade de animais") }, shape = RoundedCornerShape(14.dp))
    Button(onClick = { runCatching { AnimalRecordsService(context).registerBatch(scope, species, lotName, quantity.toInt()); lotName = ""; quantity = ""; revision++ }.onFailure { message("Informe nome e quantidade válida.") } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Adicionar lote") }
    if (lots.isEmpty()) EmptyState("Nenhum lote cadastrado", "Adicione o primeiro lote para acompanhar quantidade, saúde e histórico.") else lots.forEach { lot -> var menu by remember(lot.id) { mutableStateOf(false) }; PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(lot.name, fontWeight = FontWeight.Bold); Text("${lot.currentQuantity} de ${lot.initialQuantity} animais • Desde ${lot.startedAt}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall); StatusChip("Ativo", RuralSuccess) }; Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mais ações") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Registrar mortalidade") }, { menu = false; mortalityLotId = lot.id }) } } } } }
    mortalityLotId?.let { lotId -> AlertDialog(onDismissRequest = { mortalityLotId = null }, title = { Text("Registrar mortalidade") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(mortality, { mortality = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") }, singleLine = true); OutlinedTextField(cause, { cause = it }, Modifier.fillMaxWidth(), label = { Text("Causa ou observação") }) } }, confirmButton = { TextButton({ runCatching { AnimalRecordsService(context).registerMortality(scope, lotId, mortality.toInt(), cause); mortality = ""; cause = ""; mortalityLotId = null; revision++ }.onFailure { message(it.message ?: "Confira a mortalidade.") } }) { Text("Salvar") } }, dismissButton = { TextButton({ mortalityLotId = null }) { Text("Cancelar") } }) }
}
