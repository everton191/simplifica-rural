package br.com.simplificarural.ui.animals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.ActivityCard
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.QuickGrid

@Composable internal fun AnimalsScreen(open: (String) -> Unit) = Page("Animais", "Gerencie as criações da propriedade.") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { AnimalRecordsService(context) }; val aves = remember { records.batches(scope, AnimalSpecies.AVE).sumOf { it.currentQuantity } }; val bovinos = remember { records.animals(scope, AnimalSpecies.BOVINO).count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO } }; val suinos = remember { records.batches(scope, AnimalSpecies.SUINO).sumOf { it.currentQuantity } + records.animals(scope, AnimalSpecies.SUINO).count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO } }
    ActivityCard("Aves", "$aves animais", "Lotes, ovos, ração e saúde", null, Icons.Default.Egg, { open(RuralRoutes.BIRDS) }, "Abrir")
    ActivityCard("Bovinos", "$bovinos animais", "Animais, leite, ração e saúde", null, Icons.Default.Pets, { open(RuralRoutes.CATTLE) }, "Abrir")
    ActivityCard("Suínos", "$suinos animais", "Engorda, matrizes, ração e saúde", null, Icons.Default.Pets, { open(RuralRoutes.SWINE) }, "Abrir")
    Text("Adicionar criação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    QuickGrid(listOf("Lote de aves" to RuralRoutes.BIRD_LOTS, "Lote suíno" to RuralRoutes.SWINE_LOTS, "Novo bovino" to RuralRoutes.CATTLE_NEW), open)
}
