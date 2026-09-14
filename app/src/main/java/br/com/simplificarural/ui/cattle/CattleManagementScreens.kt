package br.com.simplificarural.ui.cattle

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.data.local.CattleManagementService
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.nutrition.*
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.NutritionNumberField
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.Segment
import br.com.simplificarural.ui.components.SimpleText
import br.com.simplificarural.ui.components.decimal
import br.com.simplificarural.ui.theme.RuralSecondaryText
import br.com.simplificarural.ui.theme.RuralSuccess
import br.com.simplificarural.ui.theme.RuralWarning
import java.math.BigDecimal
import java.time.LocalDate

@Composable internal fun CattleFeedScreen(back: () -> Unit, message: (String) -> Unit) = Page("Alimentação bovina", "Misture ingredientes e confira uma estimativa antes de fornecer.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; var silage by remember { mutableStateOf("0") }; var soy by remember { mutableStateOf("0") }; var cotton by remember { mutableStateOf("0") }; var wheat by remember { mutableStateOf("0") }; var result by remember { mutableStateOf<FeedMixAnalysis?>(null) }
    Text("Quantidade em kg por mistura", fontWeight = FontWeight.Bold); NutritionNumberField("Silagem de milho", silage) { silage = it }; NutritionNumberField("Farelo de soja", soy) { soy = it }; NutritionNumberField("Farelo de algodão", cotton) { cotton = it }; NutritionNumberField("Farelo de trigo", wheat) { wheat = it }
    Button({ runCatching { val service = CattleManagementService(context); val mix = FeedMix("simulacao", scope, "Simulação", NutritionSpecies.BOVINOS_LEITE, listOf(FeedMixItem("silagem_milho", silage.decimal()), FeedMixItem("farelo_soja", soy.decimal()), FeedMixItem("farelo_algodao", cotton.decimal()), FeedMixItem("farelo_trigo", wheat.decimal())).filter { it.asFedKg > BigDecimal.ZERO }, LocalDate.now()); result = CattleNutritionCalculator.evaluateMix(CattleNutritionCalculator.analyze(mix, service.defaultIngredientCatalog().associateBy { it.id }), DietConfiguration()) }.onFailure { message("Informe pelo menos um ingrediente com quantidade válida.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Calcular mistura") }
    result?.let { analysis -> PressCard { Text("Média nutricional estimada", fontWeight = FontWeight.Bold); SimpleText("Matéria seca", "${analysis.dryMatterPercent}%"); SimpleText("Proteína bruta", "${analysis.crudeProteinPercentOfDm}% da MS"); SimpleText("NDT", "${analysis.totalDigestibleNutrientsPercentOfDm}% da MS"); analysis.estimatedNetEnergyMcal?.let { SimpleText("Energia", "$it Mcal estimadas") }; analysis.warnings.forEach { Text("• $it", color = RuralWarning) } }; Button({ message("Simulação concluída. Revise com análise dos alimentos antes de usar.") }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar mistura bovina") } }
}
@Composable internal fun CattleReproductionScreen(back: () -> Unit, message: (String) -> Unit) = Page("Reprodução bovina", "Registre cio, inseminação, prenhez e parto na ficha da vaca.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { AnimalRecordsService(context) }
    var cow by remember { mutableStateOf("") }; var stageIndex by remember { mutableIntStateOf(0) }; var alive by remember { mutableStateOf("") }; var dead by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var revision by remember { mutableIntStateOf(0) }
    val stages = listOf(br.com.simplificarural.domain.reproduction.ReproductionStage.CIO, br.com.simplificarural.domain.reproduction.ReproductionStage.COBERTURA_OU_INSEMINACAO, br.com.simplificarural.domain.reproduction.ReproductionStage.PRENHEZ_CONFIRMADA, br.com.simplificarural.domain.reproduction.ReproductionStage.PARTO)
    OutlinedTextField(cow, { cow = it }, Modifier.fillMaxWidth(), label = { Text("Nome ou brinco da vaca") }, shape = RoundedCornerShape(14.dp)); Segment(listOf("Cio", "Cobertura", "Prenhez", "Parto")) { stageIndex = it }
    if (stages[stageIndex] == br.com.simplificarural.domain.reproduction.ReproductionStage.PARTO) { OutlinedTextField(alive, { alive = it }, Modifier.fillMaxWidth(), label = { Text("Bezerros nascidos vivos (opcional)") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(dead, { dead = it }, Modifier.fillMaxWidth(), label = { Text("Nascidos mortos (opcional)") }, shape = RoundedCornerShape(14.dp)) }
    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Observação (opcional)") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { val target = records.findTarget(scope, AnimalSpecies.BOVINO, cow) ?: error("Vaca não cadastrada. Cadastre nome e brinco primeiro."); records.registerReproduction(scope, target, stages[stageIndex], bornAlive = alive.ifBlank { null }?.toInt(), bornDead = dead.ifBlank { null }?.toInt(), notes = notes) ; revision++ }.onSuccess { message("Registro reprodutivo salvo no histórico da vaca."); cow = ""; alive = ""; dead = ""; notes = "" }.onFailure { message(it.message ?: "Revise os dados da reprodução.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar reprodução") }
    val events = remember(revision) { records.animals(scope, AnimalSpecies.BOVINO).flatMap { animal -> records.reproductionHistory(animal.id).map { animal.identification to it } } }
    if (events.isEmpty()) EmptyState("Sem registros reprodutivos", "Use o formulário acima para lançar o primeiro evento.") else events.forEach { (name, event) -> PressCard { Text(name, fontWeight = FontWeight.Bold); Text("${event.stage.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} • ${event.date}", color = RuralSecondaryText); event.bornAlive?.let { Text("Nascidos vivos: $it", color = RuralSuccess, style = MaterialTheme.typography.bodySmall) } } }
}
