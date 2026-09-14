@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package br.com.simplificarural.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.R
import br.com.simplificarural.ai.AiModelRepository
import br.com.simplificarural.ai.AssistantResult
import br.com.simplificarural.ai.GemmaLocalAiEngine
import br.com.simplificarural.ai.RuralAssistant
import br.com.simplificarural.backup.LocalBackupStore
import br.com.simplificarural.data.local.CattleManagementService
import br.com.simplificarural.data.local.AnimalRecordsService
import br.com.simplificarural.data.local.ActivityLogService
import br.com.simplificarural.data.local.MilkSecretaryService
import br.com.simplificarural.data.local.MilkShift
import br.com.simplificarural.domain.agenda.AgendaType
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.management.FarmManagementService
import br.com.simplificarural.domain.management.operationFlows
import br.com.simplificarural.domain.inventory.StockAlertService
import br.com.simplificarural.domain.inventory.PackagingConversionService
import br.com.simplificarural.domain.orders.RuralOrderService
import br.com.simplificarural.domain.nutrition.*
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.ui.agenda.*
import br.com.simplificarural.ui.animals.*
import br.com.simplificarural.ui.backup.*
import br.com.simplificarural.ui.components.*
import br.com.simplificarural.ui.health.*
import br.com.simplificarural.ui.inventory.*
import br.com.simplificarural.ui.navigation.*
import br.com.simplificarural.ui.orders.*
import br.com.simplificarural.ui.poultry.*
import br.com.simplificarural.ui.settings.*
import br.com.simplificarural.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun SimplificaRuralApp() {
    val stack = remember { mutableStateListOf(RuralRoutes.HOME) }
    val route = stack.last()
    var assistantVoiceMode by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    fun open(destination: String) { if (destination != route) stack.add(destination) }
    fun root(destination: String) { stack.clear(); stack.add(destination) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else root(RuralRoutes.HOME) }
    BackHandler {
        when {
            stack.size > 1 -> back()
            route != RuralRoutes.HOME -> root(RuralRoutes.HOME)
            else -> Unit // A Home é a tela inicial do app; o gesto não encerra o aplicativo.
        }
    }
    val bottom = listOf(
        NavItem(RuralRoutes.HOME, "Início", Icons.Default.Home), NavItem(RuralRoutes.ANIMALS, "Animais", Icons.Default.Pets),
        NavItem(RuralRoutes.STOCK, "Estoque", Icons.Default.Inventory2), NavItem(RuralRoutes.FINANCE, "Caixa", Icons.Default.AccountBalanceWallet),
        NavItem(RuralRoutes.MORE, "Mais", Icons.Default.Menu)
    )
    val isRoot = route in bottom.map { it.route }
    Scaffold(
        containerColor = RuralBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomNavigationBar(bottom, route, ::root) },
        floatingActionButton = { if (route !in setOf(RuralRoutes.ASSISTANT, RuralRoutes.HOME)) Surface(Modifier.size(56.dp).combinedClickable(onClick = { assistantVoiceMode = false; open(RuralRoutes.ASSISTANT) }, onLongClick = { assistantVoiceMode = true; open(RuralRoutes.ASSISTANT) }), shape = RoundedCornerShape(50), color = RuralDarkGreen, contentColor = Color.White, shadowElevation = 6.dp) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, "Abrir secretária; segure para falar") } } }
    ) { padding ->
        AnimatedContent(route, transitionSpec = { (fadeIn(tween(210)) togetherWith fadeOut(tween(180))) }, label = "screen") { current ->
            Box(Modifier.padding(padding)) {
                RuralScreen(current, isRoot, ::open, ::back, assistantVoiceMode) { message ->
                    coroutineScope.launch { snackbar.showSnackbar(message) }
                }
            }
        }
    }
}

@Composable
private fun RuralScreen(route: String, root: Boolean, open: (String) -> Unit, back: () -> Unit, assistantVoiceMode: Boolean, message: (String) -> Unit) = when (route) {
    RuralRoutes.HOME -> HomeScreen(open)
    RuralRoutes.ANIMALS -> AnimalsScreen(open)
    RuralRoutes.BIRDS -> BirdsScreen(open)
    RuralRoutes.BIRD_EGGS -> EggRegistrationScreen(back, message)
    RuralRoutes.BIRD_LOTS -> LotsScreen("Lotes de aves", back, message)
    RuralRoutes.CATTLE -> CattleScreen(open)
    RuralRoutes.CATTLE_LIST -> CattleListScreen(open, back)
    RuralRoutes.CATTLE_DETAIL -> CattleListScreen(open, back)
    RuralRoutes.CATTLE_MILK -> MilkRegistrationScreen(back, message)
    RuralRoutes.CATTLE_MILK_CLOSURE -> MilkClosureScreen(back, message)
    RuralRoutes.CATTLE_FEED -> CattleFeedScreen(back, message)
    RuralRoutes.CATTLE_REPRODUCTION -> CattleReproductionScreen(back, message)
    RuralRoutes.CATTLE_NEW -> NewCattleScreen(back, message)
    RuralRoutes.SWINE -> SwineScreen(open)
    RuralRoutes.SWINE_FATTENING -> FatteningScreen(open)
    RuralRoutes.SWINE_LOTS -> LotsScreen("Lotes de engorda", back, message)
    RuralRoutes.SWINE_BREEDING -> BreedingScreen(open)
    RuralRoutes.SOW_DETAIL -> SowDetailScreen(back)
    RuralRoutes.SWINE_WEIGHT -> SwineWeightScreen(back, message)
    RuralRoutes.SWINE_FARROWING -> SwineFarrowingScreen(back, message)
    RuralRoutes.STOCK -> StockScreen(open)
    RuralRoutes.STOCK_ADD -> StockAddScreen(back, message)
    RuralRoutes.STOCK_DETAIL -> StockDetailScreen("Item de estoque", back, open)
    RuralRoutes.FINANCE -> FinanceScreen(open)
    RuralRoutes.ENTRY -> FinancialFormScreen("Nova entrada", "Salvar entrada", back, message)
    RuralRoutes.EXPENSE -> FinancialFormScreen("Nova despesa", "Salvar despesa", back, message)
    RuralRoutes.PURCHASES -> PurchasesScreen(open, message)
    RuralRoutes.NEW_PURCHASE -> PurchaseFormScreen(back, message)
    RuralRoutes.SALES -> SalesScreen(open, message)
    RuralRoutes.NEW_SALE -> SaleFormScreen(back, message)
    RuralRoutes.AGENDA -> AgendaScreen(back)
    RuralRoutes.HEALTH -> HealthScreen(back, message)
    RuralRoutes.PRODUCTION -> ProductionScreen(back)
    RuralRoutes.MORE -> MoreScreen(open)
    RuralRoutes.SETTINGS -> SettingsScreen(open)
    RuralRoutes.ACTIVITIES -> ActivitiesScreen(back, message)
    RuralRoutes.HISTORY -> HistoryScreen(back)
    RuralRoutes.BACKUP -> BackupScreen(back, message)
    RuralRoutes.ABOUT -> AboutScreen(back)
    RuralRoutes.ASSISTANT -> AssistantScreen(back, message, open, assistantVoiceMode)
    RuralRoutes.OPERATIONAL_TEST -> OperationalTestScreen(back)
    RuralRoutes.PACKAGING -> PackagingScreen(back, message)
    RuralRoutes.ORDERS -> OrdersScreen(back, message)
    else -> when { route.startsWith("${RuralRoutes.STOCK_DETAIL}/") -> StockDetailScreen(route.removePrefix("${RuralRoutes.STOCK_DETAIL}/"), back, open); route.startsWith("${RuralRoutes.STOCK_ADD}/") -> StockAddScreen(back, message, route.removePrefix("${RuralRoutes.STOCK_ADD}/")); route.startsWith("${RuralRoutes.CATTLE_DETAIL}/") -> CattleProfileDetailScreen(route.removePrefix("${RuralRoutes.CATTLE_DETAIL}/"), back); route.startsWith(RuralRoutes.FEATURE_PREFIX) && route.removePrefix(RuralRoutes.FEATURE_PREFIX).contains("Relatórios", true) -> AreaReportScreen(route.removePrefix(RuralRoutes.FEATURE_PREFIX), back); route.startsWith(RuralRoutes.FEATURE_PREFIX) -> FeatureScreen(route.removePrefix(RuralRoutes.FEATURE_PREFIX), back, message); else -> PlaceholderScreen("Tela não encontrada", back) }
}




@Composable private fun HomeScreen(open: (String) -> Unit) = Page("Simplifica Rural", FarmContextStore(LocalContext.current).farmName(), actions = { IconButton({ open(RuralRoutes.AGENDA) }) { Icon(Icons.Default.Notifications, "Avisos") }; IconButton({ open(RuralRoutes.SETTINGS) }) { Icon(Icons.Default.Settings, "Configurações") } }) {
    val context = LocalContext.current
    val farmScope = remember { FarmContextStore(context).current() }; var period by remember { mutableIntStateOf(0) }
    val records = remember(period) { FarmManagementService(context).records(CashViewScope.SelectedUnit(farmScope)) }
    val financial = remember { FarmManagementService(context).financialResult(CashViewScope.SelectedUnit(farmScope)) }
    val start = when (period) { 1 -> LocalDate.now().minusDays(6); 2 -> LocalDate.now().withDayOfMonth(1); else -> LocalDate.now() }
    val eggs = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS && !it.date.isBefore(start) }.sumOf { it.quantity?.toInt() ?: 0 }
    val milk = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE && !it.date.isBefore(start) }.fold(BigDecimal.ZERO) { total, item -> total + (item.quantity ?: BigDecimal.ZERO) }
    val latestBird = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS }.maxByOrNull { it.createdAt }
    val latestCattle = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE }.maxByOrNull { it.createdAt }
    val latestSwine = records.filter { it.type in setOf(br.com.simplificarural.domain.management.ManagementRecordType.PESAGEM_SUINOS, br.com.simplificarural.domain.management.ManagementRecordType.PARTO_SUINOS) }.maxByOrNull { it.createdAt }
    fun latestText(record: br.com.simplificarural.domain.management.ManagementRecord?, empty: String) = record?.let { "Último: ${it.date} • ${it.description}" } ?: empty
    val periodName = listOf("hoje", "na semana", "no mês")[period]
    Segment(listOf("Hoje", "Semana", "Mês")) { period = it }
    Text("Produção $periodName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    ActivityCard("Aves", "$eggs ovos $periodName", latestText(latestBird, "Sem produção confirmada ainda"), null, Icons.Default.Egg, { open(RuralRoutes.BIRDS) })
    ActivityCard("Bovinos", "${milk.stripTrailingZeros().toPlainString()} L $periodName", latestText(latestCattle, "Sem ordenha confirmada ainda"), null, Icons.Default.Pets, { open(RuralRoutes.CATTLE) })
    ActivityCard("Suínos", "Lotes e desempenho", latestText(latestSwine, "Sem pesagem ou parto confirmado"), null, Icons.Default.Pets, { open(RuralRoutes.SWINE) })
    RecentProductionHistory(records, open)
    Text("Gestão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    FinancialCard(financial) { open(RuralRoutes.FINANCE) }
    Text("Atalhos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Button({ open(RuralRoutes.ASSISTANT) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Falar com a secretária") }
    NoticeCard({ open(RuralRoutes.AGENDA) })
}

@Composable private fun CattleScreen(open: (String) -> Unit) = Page("Bovinos") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val dashboard = remember { CattleManagementService(context).dashboard(scope) }; val milk = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE && it.date == LocalDate.now() }.fold(BigDecimal.ZERO) { total, record -> total + (record.quantity ?: BigDecimal.ZERO) } }
    MetricGrid(listOf("Total" to dashboard.totalCattle.toString(), "Em lactação" to dashboard.lactatingCattle.toString(), "Leite hoje" to "${milk.stripTrailingZeros().toPlainString()} L", "Média/vaca" to "${dashboard.averageMilkPerLactatingCow.stripTrailingZeros().toPlainString()} L"))
    QuickGrid(listOf("Registrar leite" to RuralRoutes.CATTLE_MILK, "Fechar leite" to RuralRoutes.CATTLE_MILK_CLOSURE, "Animais" to RuralRoutes.CATTLE_LIST, "Alimentação" to RuralRoutes.CATTLE_FEED, "Reprodução" to RuralRoutes.CATTLE_REPRODUCTION, "Saúde" to RuralRoutes.HEALTH, "Histórico" to RuralRoutes.HISTORY, "Relatórios" to RuralRoutes.feature("Relatórios bovinos")), open)
    MilkDiaryTable(MilkSecretaryService(context), scope)
}

@Composable private fun SwineScreen(open: (String) -> Unit) = Page("Suínos") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { AnimalRecordsService(context) }; val batches = remember { records.batches(scope, AnimalSpecies.SUINO) }; val animals = remember { records.animals(scope, AnimalSpecies.SUINO) }; val total = batches.sumOf { it.currentQuantity } + animals.count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO }
    MetricGrid(listOf("Total" to total.toString(), "Lotes" to batches.size.toString(), "Matrizes" to animals.count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO }.toString(), "Leitões" to "A registrar"))
    QuickGrid(listOf("Engorda" to RuralRoutes.SWINE_FATTENING, "Matrizes" to RuralRoutes.SWINE_BREEDING), open)
}

@Composable private fun FatteningScreen(open: (String) -> Unit) = Page("Suínos — Engorda") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val lots = remember { AnimalRecordsService(context).batches(scope, AnimalSpecies.SUINO) }; val performance = remember { FarmManagementService(context).swinePerformance(CashViewScope.SelectedUnit(scope)) }
    val gain = performance.averageDailyGainKg?.multiply(BigDecimal(1000))?.stripTrailingZeros()?.toPlainString() ?: "—"
    MetricGrid(listOf("Animais" to lots.sumOf { it.currentQuantity }.toString(), "Ganho médio" to "$gain g/dia", "Ração/dia" to "A registrar", "Desmame/lote" to (performance.weanedPerLitter?.stripTrailingZeros()?.toPlainString() ?: "—")))
    QuickGrid(listOf("Lotes" to RuralRoutes.SWINE_LOTS, "Registrar peso" to RuralRoutes.SWINE_WEIGHT, "Alimentação" to RuralRoutes.feature("Alimentação suínos"), "Saúde" to RuralRoutes.HEALTH, "Mortalidade" to RuralRoutes.feature("Mortalidade suínos"), "Histórico" to RuralRoutes.HISTORY, "Relatórios" to RuralRoutes.feature("Relatórios suínos")), open)
}

@Composable private fun BreedingScreen(open: (String) -> Unit) = Page("Suínos — Matrizes") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val animals = remember { AnimalRecordsService(context).animals(scope, AnimalSpecies.SUINO) }; val farrowings = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PARTO_SUINOS } }; val piglets = farrowings.fold(BigDecimal.ZERO) { total, record -> total + (record.quantity ?: BigDecimal.ZERO) }
    MetricGrid(listOf("Matrizes" to animals.count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO }.toString(), "Prenhas" to "A registrar", "Partos" to farrowings.size.toString(), "Leitões" to piglets.stripTrailingZeros().toPlainString()))
    QuickGrid(listOf("Matrizes" to RuralRoutes.SOW_DETAIL, "Cio" to RuralRoutes.feature("Cio suínos"), "Cobertura" to RuralRoutes.feature("Cobertura suínos"), "Inseminação" to RuralRoutes.feature("Inseminação suínos"), "Prenhez" to RuralRoutes.feature("Prenhez suínos"), "Partos" to RuralRoutes.SWINE_FARROWING, "Leitões" to RuralRoutes.feature("Leitões suínos"), "Desmame" to RuralRoutes.SWINE_FARROWING, "Histórico" to RuralRoutes.HISTORY), open)
}

@Composable private fun FinanceScreen(open: (String) -> Unit) = Page("Financeiro") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; var general by remember { mutableStateOf(false) }; val viewScope: CashViewScope = if (general) CashViewScope.General(scope.organizationId) else CashViewScope.SelectedUnit(scope); val result = remember(general) { service.financialResult(viewScope) }; val entries = remember(general) { service.cashEntries(viewScope) }
    Segment(listOf("Esta unidade", "Caixa geral")) { general = it == 1 }
    MetricGrid(listOf("Resultado" to money(result.netProfit), "Entradas" to money(result.revenue), "Custos" to money(result.totalCost), "Caixa" to money(result.cashGeneration)))
    QuickGrid(listOf("Nova entrada" to RuralRoutes.ENTRY, "Nova despesa" to RuralRoutes.EXPENSE, "Compras" to RuralRoutes.PURCHASES, "Vendas" to RuralRoutes.SALES), open)
    Text("Histórico financeiro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (entries.isEmpty()) EmptyState("Sem lançamentos financeiros", "Compras, vendas e despesas confirmadas aparecerão aqui.") else entries.take(8).forEach { entry -> FinancialHistoryRow(entry) }
}
@Composable private fun FinancialHistoryRow(entry: br.com.simplificarural.domain.financial.CashEntry) { var expanded by remember { mutableStateOf(false) }; PressCard { Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(entry.description, fontWeight = FontWeight.Medium); Text(entry.date.toString(), style = MaterialTheme.typography.bodySmall, color = RuralSecondaryText) }; Text(money(entry.amount), color = if (entry.kind == br.com.simplificarural.domain.financial.CashEntryKind.ENTRADA) RuralSuccess else RuralDanger, fontWeight = FontWeight.Bold); Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Recolher" else "Detalhes", tint = RuralSecondaryText) }; if (expanded) Text("${if (entry.kind == br.com.simplificarural.domain.financial.CashEntryKind.ENTRADA) "Entrada" else "Saída"} registrada no caixa em ${entry.date}.", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }

@Composable private fun MilkRegistrationScreen(back: () -> Unit, message: (String) -> Unit) = Page("Registrar leite", "Data e hora são registradas automaticamente.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val cattle = remember { CattleManagementService(context) }; val cows = remember { cattle.cows(scope) }; val now = java.time.LocalDateTime.now()
    var selectedCow by remember { mutableStateOf("") }; var liters by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    if (cows.isNotEmpty()) { var expanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(expanded, { expanded = it }) { OutlinedTextField(selectedCow, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Vaca (opcional)") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, shape = RoundedCornerShape(14.dp)); ExposedDropdownMenu(expanded, { expanded = false }) { cows.forEach { cow -> DropdownMenuItem({ Text("${cow.name} • ${cow.earTag}") }, { selectedCow = cow.id; expanded = false }) } } } } else OutlinedTextField(selectedCow, { selectedCow = it }, Modifier.fillMaxWidth(), label = { Text("Identificação da vaca (opcional)") }, shape = RoundedCornerShape(14.dp))
    OutlinedTextField(liters, { liters = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade ordenhada") }, suffix = { Text("litros") }, singleLine = true, shape = RoundedCornerShape(14.dp)); Text("Horário do lançamento: ${now.toLocalTime().withSecond(0).withNano(0)}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall); OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Observação") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { val amount = liters.decimal(); require(amount > BigDecimal.ZERO); FarmManagementService(context).registerMilkProduction(scope, amount); val shift = MilkShift.automatic(now.hour); MilkSecretaryService(context).record(scope, amount, shift, now); cows.firstOrNull { it.id == selectedCow }?.let { cow -> cattle.registerMilk(cow.id, if (shift == MilkShift.MANHA) amount else BigDecimal.ZERO, if (shift == MilkShift.TARDE) amount else BigDecimal.ZERO, if (shift == MilkShift.NOITE) amount else BigDecimal.ZERO, notes = notes.ifBlank { null }) } }.onSuccess { message("Ordenha salva com horário automático."); back() }.onFailure { message("Informe uma quantidade válida.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar ordenha") }
}
@Composable private fun MilkClosureScreen(back: () -> Unit, message: (String) -> Unit) = Page("Fechar caderneta de leite", "Fechamento com a empresa; vendas avulsas ficam fora deste valor.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val management = remember { FarmManagementService(context) }; var period by remember { mutableIntStateOf(1) }; var price by remember { mutableStateOf("") }; var company by remember { mutableStateOf("") }; var confirmed by remember { mutableStateOf(false) }
    Segment(listOf("Semanal", "Quinzenal", "Mensal")) { period = it; confirmed = false }; val start = when (period) { 0 -> LocalDate.now().minusDays(6); 1 -> LocalDate.now().minusDays(14); else -> LocalDate.now().withDayOfMonth(1) }; val produced = management.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE && !it.date.isBefore(start) }.fold(BigDecimal.ZERO) { sum, item -> sum + (item.quantity ?: BigDecimal.ZERO) }; val available = management.stock(CashViewScope.SelectedUnit(scope)).firstOrNull { it.productName.equals("Leite", true) }?.quantity?.coerceAtLeast(BigDecimal.ZERO) ?: BigDecimal.ZERO; val liters = minOf(produced, available); val total = runCatching { liters * price.replace(',', '.').toBigDecimal() }.getOrDefault(BigDecimal.ZERO)
    MetricGrid(listOf("Caderneta" to "${produced.stripTrailingZeros().toPlainString()} L", "Disponível p/ empresa" to "${liters.stripTrailingZeros().toPlainString()} L")); OutlinedTextField(company, { company = it; confirmed = false }, Modifier.fillMaxWidth(), label = { Text("Empresa compradora") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(price, { price = it; confirmed = false }, Modifier.fillMaxWidth(), label = { Text("Preço por litro (R$)") }, shape = RoundedCornerShape(14.dp)); if (price.isNotBlank()) PressCard { Text("${liters.stripTrailingZeros().toPlainString()} L × ${money(price.replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO)}", fontWeight = FontWeight.Bold); Text("Valor a receber da empresa: ${money(total)}", color = RuralSuccess) }; Button({ confirmed = true }, Modifier.fillMaxWidth().height(48.dp)) { Text("Confirmar valor a receber") }; if (confirmed) Button({ runCatching { require(company.isNotBlank()); management.registerSale(scope, "Leite", liters, "litros", price.replace(',', '.').toBigDecimal(), description = "Fechamento ${if (period == 0) "semanal" else if (period == 1) "quinzenal" else "mensal"} • $company") }.onSuccess { message("Fechamento salvo como receita da empresa no caixa."); back() }.onFailure { message(it.message ?: "Confira empresa, litros e preço.") } }, Modifier.fillMaxWidth().height(48.dp)) { Text("Salvar fechamento no caixa") } }
@Composable private fun FinancialFormScreen(title: String, button: String, back: () -> Unit, message: (String) -> Unit) = Page(title, "O lançamento será incluído no caixa da unidade.", back) { val context = LocalContext.current; var description by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; val isIncome = title.contains("entrada", true); OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descrição") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Valor (R$)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { val service = FarmManagementService(context); val scope = FarmContextStore(context).current(); val value = amount.replace(',', '.').toBigDecimal(); if (isIncome) service.registerOtherIncome(scope, value, description = description) else service.registerExpense(scope, br.com.simplificarural.domain.management.FinancialCategory.OUTRA_DESPESA, value, description = description) }.onSuccess { message("Lançamento salvo no caixa."); back() }.onFailure { message("Informe descrição e valor válido.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text(button) } }

@Composable private fun MilkDiaryTable(secretary: MilkSecretaryService, scope: br.com.simplificarural.domain.property.FarmScope) {
    val today = LocalDate.now(); var view by remember { mutableIntStateOf(0) }; var closing by remember { mutableStateOf(secretary.closingDay(scope)) }; var selectedDay by remember { mutableStateOf(today) }; var closingMenu by remember { mutableStateOf(false) }; val records = secretary.records(scope); val start = when (view) { 0 -> today; 1 -> today.minusDays(((today.dayOfWeek.value - closing.value + 7) % 7).toLong()); else -> today.withDayOfMonth(1) }; val days = generateSequence(start) { it.plusDays(1).takeIf { next -> !next.isAfter(today) } }.toList(); val selected = selectedDay.takeIf { it in days } ?: today; val byDay = records.groupBy { it.recordedAt.toLocalDate() }; val periodRecords = records.filter { !it.recordedAt.toLocalDate().isBefore(start) && !it.recordedAt.toLocalDate().isAfter(today) }; val total = periodRecords.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.liters }; fun dayName(day: java.time.DayOfWeek) = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")[day.value - 1]
    Text("Caderneta de ordenhas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Segment(listOf("Hoje", "Semana", "Mês")) { view = it; selectedDay = today }
    if (view == 1) Row(verticalAlignment = Alignment.CenterVertically) { Text("Fechamento: ${dayName(closing)}", Modifier.weight(1f), color = RuralSecondaryText); Box { TextButton({ closingMenu = true }) { Text("Alterar") }; DropdownMenu(closingMenu, { closingMenu = false }) { java.time.DayOfWeek.entries.forEach { day -> DropdownMenuItem({ Text(dayName(day)) }, { closing = day; secretary.setClosingDay(scope, day); closingMenu = false; selectedDay = today }) } } } }
    Text(if (view == 0) "Detalhe de hoje" else "${start.dayOfMonth.toString().padStart(2, '0')}/${start.monthValue.toString().padStart(2, '0')} a ${today.dayOfMonth.toString().padStart(2, '0')}/${today.monthValue.toString().padStart(2, '0')}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall); if (view > 0) MetricGrid(listOf(if (view == 1) "Total da semana" to "${total.stripTrailingZeros().toPlainString()} L" else "Total do mês" to "${total.stripTrailingZeros().toPlainString()} L"))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(days) { day -> val dayTotal = byDay[day].orEmpty().fold(BigDecimal.ZERO) { sum, entry -> sum + entry.liters }; FilterChip(selected = day == selected, onClick = { selectedDay = day }, label = { Text("${dayName(day.dayOfWeek)}\n${day.dayOfMonth.toString().padStart(2, '0')} • ${dayTotal.stripTrailingZeros().toPlainString()}L") }) } }
    val entries = byDay[selected].orEmpty().sortedBy { it.recordedAt }; PressCard { Text("${dayName(selected.dayOfWeek)} ${selected.dayOfMonth.toString().padStart(2, '0')}/${selected.monthValue.toString().padStart(2, '0')}", fontWeight = FontWeight.Bold); if (entries.isEmpty()) Text("Sem ordenha registrada neste dia.", color = RuralSecondaryText) else entries.forEach { entry -> Row { Text(entry.recordedAt.toLocalTime().withSecond(0).withNano(0).toString(), Modifier.weight(1f)); Text("${entry.liters.stripTrailingZeros().toPlainString()} L", fontWeight = FontWeight.Bold) } }; HorizontalDivider(); Row { Text("Total do dia", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("${entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.liters }.stripTrailingZeros().toPlainString()} L", color = RuralDarkGreen, fontWeight = FontWeight.Bold) } }
}

@Composable private fun NewCattleScreen(back: () -> Unit, message: (String) -> Unit) = Page("Novo bovino", "Cadastre a vaca para acompanhar leite e alimentação.", back) {
    val context = LocalContext.current; var name by remember { mutableStateOf("") }; var tag by remember { mutableStateOf("") }; var weight by remember { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(tag, { tag = it }, Modifier.fillMaxWidth(), label = { Text("Brinco") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(weight, { weight = it }, Modifier.fillMaxWidth(), label = { Text("Peso estimado (kg)") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { CattleManagementService(context).registerCow(FarmContextStore(context).current(), name, tag, weight.replace(',', '.').toBigDecimal(), true) }.onSuccess { message("Bovino cadastrado com sucesso."); back() }.onFailure { message("Informe nome, brinco e peso válido.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar bovino") }
}

@Composable private fun CattleFeedScreen(back: () -> Unit, message: (String) -> Unit) = Page("Alimentação bovina", "Misture ingredientes e confira uma estimativa antes de fornecer.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; var silage by remember { mutableStateOf("0") }; var soy by remember { mutableStateOf("0") }; var cotton by remember { mutableStateOf("0") }; var wheat by remember { mutableStateOf("0") }; var result by remember { mutableStateOf<FeedMixAnalysis?>(null) }
    Text("Quantidade em kg por mistura", fontWeight = FontWeight.Bold); NutritionNumberField("Silagem de milho", silage) { silage = it }; NutritionNumberField("Farelo de soja", soy) { soy = it }; NutritionNumberField("Farelo de algodão", cotton) { cotton = it }; NutritionNumberField("Farelo de trigo", wheat) { wheat = it }
    Button({ runCatching { val service = CattleManagementService(context); val mix = FeedMix("simulacao", scope, "Simulação", NutritionSpecies.BOVINOS_LEITE, listOf(FeedMixItem("silagem_milho", silage.decimal()), FeedMixItem("farelo_soja", soy.decimal()), FeedMixItem("farelo_algodao", cotton.decimal()), FeedMixItem("farelo_trigo", wheat.decimal())).filter { it.asFedKg > BigDecimal.ZERO }, LocalDate.now()); result = CattleNutritionCalculator.evaluateMix(CattleNutritionCalculator.analyze(mix, service.defaultIngredientCatalog().associateBy { it.id }), DietConfiguration()) }.onFailure { message("Informe pelo menos um ingrediente com quantidade válida.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Calcular mistura") }
    result?.let { analysis -> PressCard { Text("Média nutricional estimada", fontWeight = FontWeight.Bold); SimpleText("Matéria seca", "${analysis.dryMatterPercent}%"); SimpleText("Proteína bruta", "${analysis.crudeProteinPercentOfDm}% da MS"); SimpleText("NDT", "${analysis.totalDigestibleNutrientsPercentOfDm}% da MS"); analysis.estimatedNetEnergyMcal?.let { SimpleText("Energia", "$it Mcal estimadas") }; analysis.warnings.forEach { Text("• $it", color = RuralWarning) } }; Button({ message("Simulação concluída. Revise com análise dos alimentos antes de usar.") }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar mistura bovina") } }
}
@Composable private fun CattleReproductionScreen(back: () -> Unit, message: (String) -> Unit) = Page("Reprodução bovina", "Registre cio, inseminação, prenhez e parto na ficha da vaca.", back) {
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

@Composable private fun CattleListScreen(open: (String) -> Unit, back: () -> Unit) = Page("Bovinos", onBack = back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val cattle = remember { CattleManagementService(context).cows(scope) }
    if (cattle.isEmpty()) EmptyState("Nenhum bovino cadastrado", "Cadastre nome, brinco e peso para acompanhar a ficha individual.") else cattle.forEach { cow -> PressCard({ open(RuralRoutes.cattleDetail(cow.id)) }) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(cow.name, fontWeight = FontWeight.Bold); Text("Brinco ${cow.earTag} • ${if (cow.isLactating) "Em lactação" else "Fora da lactação"}", color = RuralSecondaryText) }; Icon(Icons.Default.ChevronRight, "Abrir ficha", tint = RuralGreen) } } }
    Button(onClick = { open(RuralRoutes.CATTLE_NEW) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Novo animal") }
}
@Composable private fun CattleProfileDetailScreen(cattleId: String, back: () -> Unit) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { CattleManagementService(context) }; val cow = remember { service.cows(scope).firstOrNull { it.id == cattleId } }
    if (cow == null) { Page("Bovino não encontrado", onBack = back) { EmptyState("Registro indisponível", "Volte à lista de bovinos e selecione outro animal.") }; return }
    val milk = remember { service.milkRecords(cow.id) }; val today = milk.filter { it.date == LocalDate.now() }.fold(BigDecimal.ZERO) { total, record -> total + record.totalLiters }; val average = remember { service.averageMilk(cow.id) }
    Page(cow.name, "Brinco ${cow.earTag}", back) { StatusChip(if (cow.isLactating) "Em lactação" else "Fora da lactação", if (cow.isLactating) RuralSuccess else RuralInactive); MetricGrid(listOf("Peso" to "${cow.bodyWeightKg.stripTrailingZeros().toPlainString()} kg", "Leite hoje" to "${today.stripTrailingZeros().toPlainString()} L", "Média 7 dias" to "${average.stripTrailingZeros().toPlainString()} L")); ExpandableDetail("Resumo", "Raça: ${cow.breed ?: "Não informada"}\nFase: ${cow.lactationStage.name.lowercase().replaceFirstChar { it.uppercase() }}"); ExpandableDetail("Lançamentos", if (milk.isEmpty()) "Ainda não há ordenhas individuais." else milk.takeLast(8).joinToString("\n") { "${it.date}: ${it.totalLiters.stripTrailingZeros().toPlainString()} L" }); ExpandableDetail("Reprodução", "Registre cio, cobertura e prenhez no menu Reprodução para manter este histórico."); ExpandableDetail("Saúde", "Vacinas e tratamentos confirmados aparecem no histórico de Saúde.") }
}
@Composable private fun AnimalRow(name: String, status: String, open: () -> Unit, onSold: () -> Unit, onDelete: () -> Unit) { var menu by remember { mutableStateOf(false) }; PressCard(open) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(status, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) }; Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mais ações") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Abrir") }, { menu = false; open() }); DropdownMenuItem({ Text("Marcar como vendido") }, { menu = false; onSold() }); DropdownMenuItem({ Text("Excluir") }, { menu = false; onDelete() }) } } } } }
@Composable private fun SowDetailScreen(back: () -> Unit) = Page("Matrizes", "Registros de matrizes cadastradas na propriedade.", back) { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val animals = remember { AnimalRecordsService(context).animals(scope, AnimalSpecies.SUINO) }; val farrowings = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PARTO_SUINOS } }; MetricGrid(listOf("Matrizes identificadas" to animals.size.toString(), "Partos registrados" to farrowings.size.toString())); if (animals.isEmpty()) EmptyState("Nenhuma matriz cadastrada", "Cadastre uma identificação de suíno na tela Animais para acompanhar eventos individuais.") else animals.forEach { animal -> PressCard { Text(animal.identification, fontWeight = FontWeight.Bold); Text("${animal.status.name.lowercase().replaceFirstChar { it.uppercase() }} • consulte saúde e reprodução", color = RuralSecondaryText) } }; if (farrowings.isNotEmpty()) ExpandableDetail("Histórico de partos", farrowings.take(8).joinToString("\n") { "${it.date}: ${it.quantity?.stripTrailingZeros()?.toPlainString()} nascidos vivos" }) }

@Composable private fun PurchasesScreen(open: (String) -> Unit, message: (String) -> Unit) = Page("Compras") { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; var revision by remember { mutableIntStateOf(0) }; val records = remember(revision) { service.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.COMPRA } }; MetricGrid(listOf("Compras" to records.count { !service.isCancelled(it) }.toString(), "Total gasto" to money(records.filterNot(service::isCancelled).fold(BigDecimal.ZERO) { total, record -> total + (record.totalAmount ?: BigDecimal.ZERO) }))); Button({ open(RuralRoutes.NEW_PURCHASE) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova compra") }; if (records.isEmpty()) EmptyState("Sem compras", "Cadastre a primeira compra para atualizar estoque e caixa.") else records.forEach { record -> AuditableRecordRow(record, service.isCancelled(record), { reason -> runCatching { service.cancel(scope, record.id, reason); revision++ }.onSuccess { message("Compra cancelada com estorno registrado.") }.onFailure { message(it.message ?: "Não foi possível cancelar.") } }) } }
@Composable private fun SalesScreen(open: (String) -> Unit, message: (String) -> Unit) = Page("Vendas") { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; var revision by remember { mutableIntStateOf(0) }; val records = remember(revision) { service.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.VENDA && it.productName != null } }; MetricGrid(listOf("Vendas" to records.count { !service.isCancelled(it) }.toString(), "Recebido" to money(records.filterNot(service::isCancelled).fold(BigDecimal.ZERO) { total, record -> total + (record.totalAmount ?: BigDecimal.ZERO) }))); Button({ open(RuralRoutes.NEW_SALE) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova venda") }; if (records.isEmpty()) EmptyState("Sem vendas", "Registre uma venda para atualizar o estoque e caixa.") else records.forEach { record -> AuditableRecordRow(record, service.isCancelled(record), { reason -> runCatching { service.cancel(scope, record.id, reason); revision++ }.onSuccess { message("Venda cancelada com estorno registrado.") }.onFailure { message(it.message ?: "Não foi possível cancelar.") } }) } }
@Composable private fun AuditableRecordRow(record: br.com.simplificarural.domain.management.ManagementRecord, cancelled: Boolean, cancel: (String) -> Unit) { var menu by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }; PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(record.description, fontWeight = FontWeight.Bold); Text("${record.quantity?.stripTrailingZeros()?.toPlainString().orEmpty()} ${record.unit.orEmpty()} • ${money(record.totalAmount ?: BigDecimal.ZERO)}", color = RuralSecondaryText); if (cancelled) Text("Cancelado — mantido para auditoria", color = RuralDanger, style = MaterialTheme.typography.bodySmall) }; if (!cancelled) Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mais ações") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Cancelar lançamento") }, { menu = false; confirm = true }) } } } }; if (confirm) AlertDialog({ confirm = false }, title = { Text("Cancelar lançamento?") }, text = { Text("O lançamento ficará no histórico e deixará de contar no caixa e estoque.") }, confirmButton = { TextButton({ cancel("Cancelado pelo usuário"); confirm = false }) { Text("Confirmar") } }, dismissButton = { TextButton({ confirm = false }) { Text("Voltar") } }) }

@Composable private fun SwineWeightScreen(back: () -> Unit, message: (String) -> Unit) = Page("Registrar pesagem", "Use o peso médio do lote; data e hora são registradas automaticamente.", back) { val context = LocalContext.current; var animals by remember { mutableStateOf("") }; var initial by remember { mutableStateOf("") }; var final by remember { mutableStateOf("") }; var days by remember { mutableStateOf("") }; OutlinedTextField(animals, { animals = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade de animais") }, shape = RoundedCornerShape(14.dp)); NutritionNumberField("Peso médio anterior", initial) { initial = it }; NutritionNumberField("Peso médio atual", final) { final = it }; OutlinedTextField(days, { days = it }, Modifier.fillMaxWidth(), label = { Text("Dias desde a pesagem anterior") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { FarmManagementService(context).registerSwineWeight(FarmContextStore(context).current(), animals.toInt(), initial.decimal(), final.decimal(), days.toInt()) }.onSuccess { message("Pesagem salva no desempenho dos suínos."); back() }.onFailure { message("Confira quantidade, pesos e dias informados.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar pesagem") } }
@Composable private fun SwineFarrowingScreen(back: () -> Unit, message: (String) -> Unit) = Page("Parto e desmame", "Registre números por matriz ou lote para gerar indicadores.", back) { val context = LocalContext.current; var alive by remember { mutableStateOf("") }; var dead by remember { mutableStateOf("") }; var weaned by remember { mutableStateOf("") }; OutlinedTextField(alive, { alive = it }, Modifier.fillMaxWidth(), label = { Text("Leitões nascidos vivos") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(dead, { dead = it }, Modifier.fillMaxWidth(), label = { Text("Nascidos mortos") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(weaned, { weaned = it }, Modifier.fillMaxWidth(), label = { Text("Desmamados (opcional)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { FarmManagementService(context).registerSwineFarrowing(FarmContextStore(context).current(), alive.toInt(), dead.ifBlank { "0" }.toInt(), weaned.ifBlank { null }?.toInt()) }.onSuccess { message("Parto salvo no histórico de matrizes."); back() }.onFailure { message("Informe pelo menos os nascidos vivos e revise os números.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar parto/desmame") } }


@Composable private fun ProductionScreen(back: () -> Unit) = Page("Produção", "Dados confirmados da propriedade", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)) }; var period by remember { mutableIntStateOf(0) }
    Segment(listOf("Hoje", "Semana", "Mês")) { period = it }; val days = if (period == 0) 1 else if (period == 1) 7 else LocalDate.now().lengthOfMonth(); val dates = (days - 1 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val eggs = dates.map { date -> records.filter { it.date == date && it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS }.sumOf { it.quantity?.toInt() ?: 0 }.toBigDecimal() }
    val milk = dates.map { date -> records.filter { it.date == date && it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE }.fold(BigDecimal.ZERO) { total, item -> total + (item.quantity ?: BigDecimal.ZERO) } }
    ProductionChart("Ovos", eggs, "unidades", dates); ProductionChart("Leite", milk, "litros", dates)
    val weights = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PESAGEM_SUINOS }.take(6).reversed().map { it.quantity ?: BigDecimal.ZERO }
    if (weights.isNotEmpty()) ProductionChart("Peso dos suínos", weights, "kg", emptyList()) else EmptyState("Sem pesagens de suínos", "Registre uma pesagem no módulo de engorda.")
}
@Composable private fun AreaReportScreen(area: String, back: () -> Unit) = Page(area, "Indicadores calculados somente com lançamentos confirmados.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { FarmManagementService(context).records(CashViewScope.SelectedUnit(scope)) }; val dates = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    when {
        area.contains("Aves", true) -> { val eggs = dates.map { day -> records.filter { it.date == day && it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS }.fold(BigDecimal.ZERO) { sum, record -> sum + (record.quantity ?: BigDecimal.ZERO) } }; ProductionChart("Produção de ovos", eggs, "ovos", dates); val birds = AnimalRecordsService(context).batches(scope, AnimalSpecies.AVE).sumOf { it.currentQuantity }; MetricGrid(listOf("Aves em lotes" to birds.toString(), "Ovos/ave no período" to if (birds > 0) eggs.fold(BigDecimal.ZERO, BigDecimal::plus).divide(BigDecimal(birds), 2, java.math.RoundingMode.HALF_UP).toPlainString() else "—")); Text("Registre ovos íntegros, quebrados, mortalidade e consumo de ração por lote para ampliar estes indicadores.", color = RuralSecondaryText) }
        area.contains("Bovinos", true) -> { val milk = dates.map { day -> records.filter { it.date == day && it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE }.fold(BigDecimal.ZERO) { sum, record -> sum + (record.quantity ?: BigDecimal.ZERO) } }; ProductionChart("Produção de leite", milk, "litros", dates); val dashboard = CattleManagementService(context).dashboard(scope); MetricGrid(listOf("Vacas em lactação" to dashboard.lactatingCattle.toString(), "Média por vaca" to "${dashboard.averageMilkPerLactatingCow.stripTrailingZeros().toPlainString()} L", "Total semanal" to "${milk.fold(BigDecimal.ZERO, BigDecimal::plus).stripTrailingZeros().toPlainString()} L")); Text("Controle de reprodução, saúde, alimentação e produção individual complementa o diagnóstico do rebanho.", color = RuralSecondaryText) }
        else -> { val weights = records.filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PESAGEM_SUINOS }.take(7).reversed().mapNotNull { it.quantity }; if (weights.isEmpty()) EmptyState("Sem pesagens confirmadas", "Registre pesos por lote para acompanhar ganho médio diário.") else ProductionChart("Pesagens recentes", weights, "kg", emptyList()); val performance = FarmManagementService(context).swinePerformance(CashViewScope.SelectedUnit(scope)); MetricGrid(listOf("Ganho médio" to performance.averageDailyGainKg?.multiply(BigDecimal(1000))?.toPlainString().orEmpty().ifBlank { "—" } + " g/dia", "Desmamados/lote" to performance.weanedPerLitter?.toPlainString().orEmpty().ifBlank { "—" }, "Mortalidade pré-desmame" to performance.preWeaningMortalityPercent?.toPlainString().orEmpty().ifBlank { "—" } + "%")); Text("Para indicadores completos, registre pesagem, consumo de ração, mortalidade, partos e desmames por lote.", color = RuralSecondaryText) }
    }
}



@Composable private fun OperationalTestScreen(back: () -> Unit) = Page("Teste da secretária", "Analise, revise e confirme", back) {
    val context = LocalContext.current
    val assistant = remember { RuralAssistant(context) }
    var situation by remember { mutableStateOf("Hoje comprei 200 kg de ração de aves por 500 reais, comprei 300 bandejas vazias por 150 reais, produzi 900 ovos, usei 30 kg de ração, vendi 50 bandejas de ovos por 10 reais cada e gastei 80 reais de combustível.") }
    var proposal by remember { mutableStateOf<br.com.simplificarural.ai.OperationalProposal?>(null) }
    var showProposal by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf<String?>(null) }
    Text("Este cenário simula uma pendência com compras, produção, ração, venda e despesa. Revise antes de confirmar.", color = RuralSecondaryText)
    OutlinedTextField(situation, { situation = it }, Modifier.fillMaxWidth(), label = { Text("Situação para a secretária resolver") }, minLines = 5, shape = RoundedCornerShape(14.dp))
    Button({ proposal = assistant.analyzeOperationalSituation(situation); showProposal = proposal != null; editing = false; if (proposal == null) applied = "Não consegui separar os seis lançamentos. Revise os valores e tente novamente." }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Pedir proposta à IA") }
    applied?.let { PressCard { Text(it, fontWeight = FontWeight.Medium) } }
    if (showProposal) AlertDialog(
        onDismissRequest = { showProposal = false },
        title = { Text(if (editing) "Editar situação" else "Proposta para confirmação") },
        text = { if (editing) OutlinedTextField(situation, { situation = it }, Modifier.fillMaxWidth(), minLines = 6, label = { Text("Corrija a situação") }) else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("A secretária separou a pendência em seis lançamentos:", fontWeight = FontWeight.Medium); Text(proposal?.summary.orEmpty()); Text("Confira os valores. A confirmação aplicará estoque, caixa, produção, consumo e despesa juntos.", color = RuralSecondaryText) } },
        confirmButton = { Button(onClick = { if (editing) { proposal = assistant.analyzeOperationalSituation(situation); editing = proposal == null; if (proposal == null) applied = "Não consegui interpretar a correção." } else { proposal?.let { applied = assistant.applyOperationalProposal(it) }; showProposal = false } }) { Text(if (editing) "Atualizar proposta" else "Salvar lançamentos") } },
        dismissButton = { TextButton(onClick = { if (editing) editing = false else editing = true }) { Text(if (editing) "Cancelar edição" else "Editar manualmente") } }
    )
}


@Composable private fun HistoryScreen(back: () -> Unit) = Page("Histórico geral", "Lançamentos confirmados da fazenda selecionada.", back) {
    val context = LocalContext.current
    val scope = remember { FarmContextStore(context).current() }
    val management = remember { FarmManagementService(context) }
    val activities = remember { ActivityLogService(context) }
    val records = remember { management.records(CashViewScope.SelectedUnit(scope)) }
    val notes = remember { activities.listAll(scope) }
    MetricGrid(listOf("Operações" to records.size.toString(), "Anotações" to notes.size.toString()))
    if (records.isEmpty() && notes.isEmpty()) EmptyState("Nenhum histórico ainda", "Confirme um lançamento ou salve uma anotação para começar.")
    records.forEach { record -> PressCard { Text(record.description, fontWeight = FontWeight.Bold); Text("${record.date} • ${record.quantity?.stripTrailingZeros()?.toPlainString().orEmpty()} ${record.unit.orEmpty()}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
    notes.forEach { note -> PressCard { Text(note.area, fontWeight = FontWeight.Bold); Text(note.description, color = RuralSecondaryText); Text("${note.createdAt.toLocalDate()} ${note.createdAt.toLocalTime().withSecond(0).withNano(0)}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun AssistantScreen(back: () -> Unit, message: (String) -> Unit, open: (String) -> Unit, startVoice: Boolean) = Page("Assistente Rural", "Conversa com contexto da propriedade", back) {
    val context = LocalContext.current; val assistant = remember { RuralAssistant(context) }; val models = remember { AiModelRepository(context) }; val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var installed by remember { mutableStateOf(models.isInstalled()) }; val progress by models.downloadProgress().collectAsState(initial = br.com.simplificarural.ai.ModelDownloadProgress(false, 0, 0, "AGUARDANDO"))
    LaunchedEffect(progress.state, progress.downloadedBytes) { installed = models.isInstalled() }
    var command by remember { mutableStateOf("") }; var reply by remember { mutableStateOf<String?>(null) }; var pending by remember { mutableStateOf(assistant.pendingDraft()) }; var showReview by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("secretary_voice", android.content.Context.MODE_PRIVATE) }; var speechOn by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }; val speaker = remember { TextToSpeech(context) { } }; DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    fun send() { if (command.isNotBlank()) { keyboard?.hide(); scope.launch { val result = assistant.handle(command, pending) as? AssistantResult.Reply; reply = result?.text ?: "Não consegui entender. Informe quantidade, produto e valor."; pending = result?.draft?.takeUnless { it.action == br.com.simplificarural.ai.RuralActionType.DESCONHECIDA }; showReview = pending?.requiresConfirmation == true; if (speechOn) speaker.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "secretary_reply"); command = "" } } }
    Text("Como posso ajudar?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    PressCard { Text(if (installed) "IA da família pronta" else if (progress.state == "INCOMPATIVEL") "IA local não compatível" else "Preparando a IA da família", fontWeight = FontWeight.Bold); Text(progress.message ?: "Um único Gemma 4 E2B é compartilhado com segurança pelos aplicativos Simplifica.", color = RuralSecondaryText); if (!installed && progress.state != "INCOMPATIVEL") { Button({ models.enqueueAutomaticDownload() }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Preparar IA") }; if (progress.downloading || progress.totalBytes > 0) { LinearProgressIndicator({ progress.percent / 100f }, Modifier.fillMaxWidth()); Text("Download: ${progress.percent}%", color = RuralSecondaryText) } }; if (progress.state == "INCOMPATIVEL") Text("As outras funções do Simplifica Rural continuam disponíveis.", color = RuralSecondaryText) }
    PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Resposta falada", fontWeight = FontWeight.Medium); Text("Desligada por padrão", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) }; Switch(speechOn, { speechOn = it; prefs.edit().putBoolean("enabled", it).apply() }) } }
    pending?.takeIf { !it.requiresConfirmation }?.let { PressCard { Text("Pendência em andamento", fontWeight = FontWeight.Bold); Text(it.summary, color = RuralSecondaryText) } }
    OutlinedTextField(command, { command = it }, Modifier.fillMaxWidth(), label = { Text("Digite ou fale o que aconteceu...") }, shape = RoundedCornerShape(14.dp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() })); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { VoiceHoldButton(Modifier.size(52.dp), { command = if (command.isBlank()) it else "$command $it" }, message, startVoice); Button(::send, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Enviar") } }; Text("Segure o microfone para falar.", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall)
    reply?.let { answer -> PressCard { Text(answer, fontWeight = FontWeight.Medium); pending?.takeIf { it.requiresConfirmation }?.let { Button({ showReview = true }, Modifier.fillMaxWidth()) { Text("Revisar lançamento") } } } }
    Text("Sugestões rápidas", fontWeight = FontWeight.Bold); QuickGrid(listOf("Registrar ovos" to RuralRoutes.BIRD_EGGS, "Registrar leite" to RuralRoutes.CATTLE_MILK, "Nova despesa" to RuralRoutes.EXPENSE, "Nova compra" to RuralRoutes.NEW_PURCHASE, "Consultar estoque" to RuralRoutes.STOCK, "Consultar financeiro" to RuralRoutes.FINANCE), open)
    if (showReview) pending?.let { draft -> AlertDialog(onDismissRequest = { showReview = false }, title = { Text("Confira antes de salvar") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(draft.summary); draft.parameters.forEach { (label, value) -> if (value.isNotBlank()) SimpleText(label.replaceFirstChar { it.uppercase() }, value) }; Text("Nada será gravado até você confirmar.", color = RuralSecondaryText) } }, confirmButton = { Button({ reply = assistant.confirm(draft); pending = assistant.pendingDraft(); showReview = false; if (pending == null) open(draft.route()) }) { Text("Confirmar e abrir módulo") } }, dismissButton = { TextButton({ showReview = false }) { Text("Corrigir depois") } }) }
}

private fun br.com.simplificarural.ai.AiDraft.route(): String = when (action) {
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_OVOS -> RuralRoutes.STOCK
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_LEITE -> RuralRoutes.CATTLE_MILK
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_COMPRA_ESTOQUE -> RuralRoutes.NEW_PURCHASE
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_VENDA_ESTOQUE -> RuralRoutes.SALES
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_VACINA -> RuralRoutes.HEALTH
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_PARTO_BOVINO -> RuralRoutes.CATTLE_REPRODUCTION
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_AGENDA -> RuralRoutes.AGENDA
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_RACAO -> RuralRoutes.STOCK
    br.com.simplificarural.ai.RuralActionType.REGISTRAR_DESPESA -> RuralRoutes.FINANCE
    else -> RuralRoutes.ASSISTANT
}

@Composable private fun VoiceHoldButton(modifier: Modifier = Modifier, onRecognized: (String) -> Unit, onMessage: (String) -> Unit, startImmediately: Boolean = false) {
    val context = LocalContext.current
    var pendingStart by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onMessage("Permita o uso do microfone para ditar o registro.")
        pendingStart = granted
    }
    val recognizer = remember { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }
    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle) { results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onRecognized) }
            override fun onError(error: Int) { if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) onMessage("Não consegui ouvir. Tente segurar e falar novamente.") }
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit; override fun onBeginningOfSpeech() = Unit; override fun onRmsChanged(rmsdB: Float) = Unit; override fun onBufferReceived(buffer: ByteArray?) = Unit; override fun onEndOfSpeech() = Unit; override fun onPartialResults(partialResults: android.os.Bundle?) = Unit; override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
    }
    LaunchedEffect(pendingStart, startImmediately) { if (pendingStart || startImmediately) { pendingStart = false; startListening() } }
    Surface(modifier.pointerInput(Unit) { detectTapGestures(onPress = { startListening(); tryAwaitRelease(); recognizer?.stopListening() }) }, color = RuralDarkGreen, contentColor = Color.White, shape = RoundedCornerShape(14.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, "Mantenha pressionado para falar") } }
}
@Composable private fun FeatureScreen(feature: String, back: () -> Unit, message: (String) -> Unit) = Page(feature, "Registro operacional", back) { val context = LocalContext.current; val farm = remember { FarmContextStore(context).current() }; val store = remember { ActivityLogService(context) }; var target by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var revision by remember { mutableIntStateOf(0) }; val entries = remember(revision) { store.list(farm, feature) }; val quantityNeeded = feature.contains("Alimentação", true) || feature.contains("Mortalidade", true) || feature.contains("Ocorrências", true) || feature.contains("Leitões", true); val description = when { feature.contains("Reprodução", true) || feature in listOf("Cio", "Cobertura", "Inseminação", "Prenhez") -> "Registre o evento reprodutivo para o animal ou lote."; feature.contains("Alimentação", true) -> "Registre alimento e quantidade fornecida ao animal ou lote."; feature.contains("Mortalidade", true) || feature.contains("Ocorrências", true) -> "Registre quantidade, causa e identificação do lote."; else -> "Registre a ocorrência para manter o histórico da propriedade." }; PressCard { Text(description, color = RuralSecondaryText) }; OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("Animal ou lote") }, shape = RoundedCornerShape(14.dp)); if (quantityNeeded) OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Descrição / observação") }, minLines = 2, shape = RoundedCornerShape(14.dp)); Button({ runCatching { require(target.isNotBlank() && note.isNotBlank()); store.add(farm, feature, buildString { append("Alvo: $target"); if (quantity.isNotBlank()) append(" • Quantidade: $quantity"); append(" • $note") }); target = ""; quantity = ""; note = ""; revision++ }.onSuccess { message("Registro salvo no histórico de $feature.") }.onFailure { message("Informe animal/lote e descrição do registro.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar registro") }; Text("Histórico", fontWeight = FontWeight.Bold); if (entries.isEmpty()) EmptyState("Nenhum registro de $feature", "Adicione o primeiro registro para começar.") else entries.forEach { entry -> PressCard { Text(entry.description, fontWeight = FontWeight.Medium); Text(entry.createdAt.toLocalDate().toString() + " • " + entry.createdAt.toLocalTime().withSecond(0).withNano(0), color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun PlaceholderScreen(title: String, back: () -> Unit) = Page(title, "Esta tela será conectada ao módulo correspondente.", back) { EmptyState("Nenhum registro disponível", "Cadastre o primeiro registro para começar.") }





