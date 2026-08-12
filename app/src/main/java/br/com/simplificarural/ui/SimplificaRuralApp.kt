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
import br.com.simplificarural.ui.theme.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

private data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

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

@Composable private fun BottomNavigationBar(items: List<NavItem>, current: String, onSelect: (String) -> Unit) = NavigationBar(containerColor = Color.White) {
    items.forEach { item ->
        NavigationBarItem(selected = current == item.route, onClick = { onSelect(item.route) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = RuralLightGreen))
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

@Composable private fun Page(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
                Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); subtitle?.let { Text(it, color = RuralSecondaryText) } }
                actions()
            }
        }
        item { GuideTip(title) }
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}
@Composable private fun GuideTip(title: String) { val text = when (title) { "Simplifica Rural" -> "Aqui você acompanha produção, saldo e atalhos. Toque nos cards para abrir cada área."; "Animais" -> "Cadastre animais e lotes aqui. Toque em uma criação para ver seus registros."; "Estoque" -> "Registre compras por categoria e consulte cada item para ver entradas e saídas."; "Financeiro" -> "Aqui você adiciona entradas, despesas, compras e vendas. Use Caixa geral para juntar as unidades."; "Agenda" -> "Crie lembretes para vacina, manutenção, pagamentos e tarefas da propriedade."; "Produção" -> "Os gráficos mostram somente lançamentos confirmados de ovos, leite e pesagens."; else -> null }; if (text != null) { val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("guide_tips", android.content.Context.MODE_PRIVATE) }; val key = "opens_${title}"; val count = remember(title) { prefs.getInt(key, 0) }; LaunchedEffect(title) { prefs.edit().putInt(key, count + 1).apply() }; if (count < 10) PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lightbulb, null, tint = RuralGreen); Spacer(Modifier.width(10.dp)); Text(text, Modifier.weight(1f), color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } } } }

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

@Composable private fun AnimalsScreen(open: (String) -> Unit) = Page("Animais", "Gerencie as criações da propriedade.") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { AnimalRecordsService(context) }; val aves = remember { records.batches(scope, AnimalSpecies.AVE).sumOf { it.currentQuantity } }; val bovinos = remember { records.animals(scope, AnimalSpecies.BOVINO).count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO } }; val suinos = remember { records.batches(scope, AnimalSpecies.SUINO).sumOf { it.currentQuantity } + records.animals(scope, AnimalSpecies.SUINO).count { it.status == br.com.simplificarural.domain.animals.AnimalStatus.ATIVO } }
    ActivityCard("Aves", "$aves animais", "Lotes, ovos, ração e saúde", null, Icons.Default.Egg, { open(RuralRoutes.BIRDS) }, "Abrir")
    ActivityCard("Bovinos", "$bovinos animais", "Animais, leite, ração e saúde", null, Icons.Default.Pets, { open(RuralRoutes.CATTLE) }, "Abrir")
    ActivityCard("Suínos", "$suinos animais", "Engorda, matrizes, ração e saúde", null, Icons.Default.Pets, { open(RuralRoutes.SWINE) }, "Abrir")
    Text("Adicionar criação", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    QuickGrid(listOf("Lote de aves" to RuralRoutes.BIRD_LOTS, "Lote suíno" to RuralRoutes.SWINE_LOTS, "Novo bovino" to RuralRoutes.CATTLE_NEW), open)
}

@Composable private fun BirdsScreen(open: (String) -> Unit) = Page("Aves") {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val animalRecords = remember { AnimalRecordsService(context) }; val management = remember { FarmManagementService(context) }
    val birds = remember { animalRecords.batches(scope, AnimalSpecies.AVE).sumOf { it.currentQuantity } }; val todayEggs = remember { management.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS && it.date == LocalDate.now() }.fold(BigDecimal.ZERO) { total, record -> total + (record.quantity ?: BigDecimal.ZERO) } }
    MetricGrid(listOf("Total de aves" to birds.toString(), "Ovos hoje" to "${todayEggs.stripTrailingZeros().toPlainString()}", "Postura" to if (birds > 0) "${todayEggs.multiply(BigDecimal(100)).divide(BigDecimal(birds), 0, java.math.RoundingMode.HALF_UP)}%" else "—", "Ração hoje" to "A registrar"))
    Text("Ações rápidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    QuickGrid(listOf("Registrar ovos" to RuralRoutes.BIRD_EGGS, "Lotes" to RuralRoutes.BIRD_LOTS, "Alimentação" to RuralRoutes.feature("Alimentação aves"), "Saúde" to RuralRoutes.HEALTH, "Ocorrências" to RuralRoutes.feature("Ocorrências aves"), "Histórico" to RuralRoutes.HISTORY, "Relatórios" to RuralRoutes.feature("Relatórios aves")), open)
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

@Composable private fun StockScreen(open: (String) -> Unit) = Page("Estoque") {
    val context = LocalContext.current
    val scope = remember { FarmContextStore(context).current() }
    val stock = FarmManagementService(context).stock(CashViewScope.SelectedUnit(scope)); val alerts = remember(stock) { StockAlertService(context).alerts(scope, stock) }
    var search by remember { mutableStateOf("") }; SearchField("Buscar item") { search = it }
    MetricGrid(listOf("Itens cadastrados" to stock.size.toString(), "Itens baixos" to alerts.size.toString(), "Valor estimado" to "A informar"))
    if (alerts.isNotEmpty()) PressCard({ open(RuralRoutes.stockDetail(alerts.first().product)) }) { Text("Reposição necessária", fontWeight = FontWeight.Bold, color = RuralWarning); alerts.take(3).forEach { Text("${it.product}: ${it.available.stripTrailingZeros().toPlainString()} ${it.unit} (mínimo ${it.minimum.stripTrailingZeros().toPlainString()})", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
    Text("Adicionar por categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    QuickGrid(listOf("Alimentação" to RuralRoutes.stockAdd("Alimentação"), "Saúde" to RuralRoutes.stockAdd("Saúde"), "Ferramentas" to RuralRoutes.stockAdd("Ferramentas"), "Outros" to RuralRoutes.stockAdd("Outros"), "Saída / venda" to RuralRoutes.NEW_SALE), open)
    Text("Itens cadastrados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (stock.isEmpty()) Text("Ainda não há itens no estoque desta unidade.", color = RuralSecondaryText)
    val filtered = stock.filter { it.productName.contains(search, true) }
    if (stock.isNotEmpty() && filtered.isEmpty()) EmptyState("Nenhum item encontrado", "Ajuste a busca ou selecione outra categoria.") else filtered.forEach { item -> StockRow(item.productName, "${item.quantity.stripTrailingZeros().toPlainString()} ${item.unit}", itemCategory(item), item.quantity <= BigDecimal.ZERO) { open(RuralRoutes.stockDetail(item.productName)) } }
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

@Composable private fun EggRegistrationScreen(back: () -> Unit, message: (String) -> Unit) = Page("Registrar ovos", "Data e hora são incluídas automaticamente.", back) {
    val context = LocalContext.current; var eggs by remember { mutableStateOf("") }
    OutlinedTextField(eggs, { eggs = it }, Modifier.fillMaxWidth(), label = { Text("Ovos aproveitáveis") }, suffix = { Text("unidades") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { FarmManagementService(context).registerEggProduction(FarmContextStore(context).current(), eggs.toInt()) }.onSuccess { message("Produção salva com data e horário atuais."); back() }.onFailure { message("Informe uma quantidade válida.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar produção") }
}
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
@Composable private fun PurchaseFormScreen(back: () -> Unit, message: (String) -> Unit) = StockAddScreen(back, message)
@Composable private fun StockAddScreen(back: () -> Unit, message: (String) -> Unit, initialCategory: String? = null) = Page("Adicionar ao estoque", "A data e a hora serão registradas automaticamente.", back) {
    val context = LocalContext.current; var product by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var unit by remember { mutableStateOf("kg") }; var price by remember { mutableStateOf("") }; val categories = listOf("Alimentação", "Saúde", "Ferramentas", "Outros"); var categoryIndex by remember(initialCategory) { mutableIntStateOf(categories.indexOf(initialCategory).takeIf { it >= 0 } ?: 0) }
    Text("Categoria", fontWeight = FontWeight.Bold); Segment(categories) { categoryIndex = it }
    OutlinedTextField(product, { product = it }, Modifier.fillMaxWidth(), label = { Text("Produto") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(unit, { unit = it }, Modifier.fillMaxWidth(), label = { Text("Unidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Preço por unidade (R$)") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { val selectedCategory = categories[categoryIndex]; val financialCategory = when (selectedCategory) { "Alimentação" -> br.com.simplificarural.domain.management.FinancialCategory.RACAO; "Saúde" -> br.com.simplificarural.domain.management.FinancialCategory.SANIDADE; "Ferramentas" -> br.com.simplificarural.domain.management.FinancialCategory.MANUTENCAO; else -> br.com.simplificarural.domain.management.FinancialCategory.OUTRA_DESPESA }; FarmManagementService(context).registerPurchase(FarmContextStore(context).current(), product, quantity.replace(',', '.').toBigDecimal(), unit, price.replace(',', '.').toBigDecimal(), financialCategory, inventoryCategory = selectedCategory) }.onSuccess { message("Item adicionado com data e horário atuais."); back() }.onFailure { message("Informe produto, quantidade e preço válidos.") } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Adicionar ao estoque") }
}
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
@Composable private fun NutritionNumberField(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, suffix = { Text("kg") }, singleLine = true, shape = RoundedCornerShape(14.dp))
private fun String.decimal(): BigDecimal = replace(',', '.').toBigDecimal()
@Composable private fun SimpleText(label: String, value: String) = Row { Text(label, Modifier.weight(1f), color = RuralSecondaryText); Text(value, fontWeight = FontWeight.Medium) }

@Composable private fun FormPage(title: String, back: () -> Unit, message: (String) -> Unit, button: String, saved: String, fields: List<String>, extra: @Composable ColumnScope.() -> Unit = {}) = Page(title, onBack = back) {
    fields.forEach { FormField(it) }; extra(); Button(onClick = { message(saved); back() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(button) }; OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Cancelar") }
}

@Composable private fun LotsScreen(title: String, back: () -> Unit, message: (String) -> Unit) = Page(title, onBack = back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val species = if (title.contains("aves", true)) AnimalSpecies.AVE else AnimalSpecies.SUINO
    var lotName by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var revision by remember { mutableIntStateOf(0) }; var mortalityLotId by remember { mutableStateOf<String?>(null) }; var mortality by remember { mutableStateOf("") }; var cause by remember { mutableStateOf("") }; val lots = remember(revision) { AnimalRecordsService(context).batches(scope, species) }
    OutlinedTextField(lotName, { lotName = it }, Modifier.fillMaxWidth(), label = { Text("Nome ou identificação do lote") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade de animais") }, shape = RoundedCornerShape(14.dp))
    Button(onClick = { runCatching { AnimalRecordsService(context).registerBatch(scope, species, lotName, quantity.toInt()); lotName = ""; quantity = ""; revision++ }.onFailure { message("Informe nome e quantidade válida.") } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Adicionar lote") }
    if (lots.isEmpty()) EmptyState("Nenhum lote cadastrado", "Adicione o primeiro lote para acompanhar quantidade, saúde e histórico.") else lots.forEach { lot -> var menu by remember(lot.id) { mutableStateOf(false) }; PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(lot.name, fontWeight = FontWeight.Bold); Text("${lot.currentQuantity} de ${lot.initialQuantity} animais • Desde ${lot.startedAt}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall); StatusChip("Ativo", RuralSuccess) }; Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mais ações") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Registrar mortalidade") }, { menu = false; mortalityLotId = lot.id }) } } } } }
    mortalityLotId?.let { lotId -> AlertDialog(onDismissRequest = { mortalityLotId = null }, title = { Text("Registrar mortalidade") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(mortality, { mortality = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") }, singleLine = true); OutlinedTextField(cause, { cause = it }, Modifier.fillMaxWidth(), label = { Text("Causa ou observação") }) } }, confirmButton = { TextButton({ runCatching { AnimalRecordsService(context).registerMortality(scope, lotId, mortality.toInt(), cause); mortality = ""; cause = ""; mortalityLotId = null; revision++ }.onFailure { message(it.message ?: "Confira a mortalidade.") } }) { Text("Salvar") } }, dismissButton = { TextButton({ mortalityLotId = null }) { Text("Cancelar") } }) }
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
@Composable private fun ExpandableDetail(title: String, detail: String) { var expanded by remember { mutableStateOf(false) }; PressCard { Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Recolher" else "Expandir", tint = RuralGreen) }; if (expanded) Text(detail, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun StockDetailScreen(product: String, back: () -> Unit, open: (String) -> Unit) = Page(product, "Histórico do item", back) { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; val records = remember { service.records(CashViewScope.SelectedUnit(scope)).filter { it.productName.equals(product, true) } }; val balance = service.stock(CashViewScope.SelectedUnit(scope)).firstOrNull { it.productName.equals(product, true) }; val alerts = remember { StockAlertService(context) }; var minimum by remember(balance) { mutableStateOf(balance?.let { alerts.minimum(scope, it.productName, it.unit).stripTrailingZeros().toPlainString() }.orEmpty()) }; MetricGrid(listOf("Quantidade atual" to balance?.let { "${it.quantity.stripTrailingZeros().toPlainString()} ${it.unit}" }.orEmpty(), "Categoria" to balance?.let(::itemCategory).orEmpty())); balance?.let { item -> OutlinedTextField(minimum, { minimum = it }, Modifier.fillMaxWidth(), label = { Text("Estoque mínimo (${item.unit})") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { alerts.setMinimum(scope, item.productName, item.unit, minimum.replace(',', '.').toBigDecimal()) } }, Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar mínimo para alerta") } }; Button({ open(RuralRoutes.NEW_PURCHASE) }, Modifier.fillMaxWidth().height(48.dp)) { Text("Adicionar entrada") }; Text("Movimentações", fontWeight = FontWeight.Bold); if (records.isEmpty()) EmptyState("Sem movimentações", "As entradas e saídas confirmadas deste item aparecerão aqui.") else records.forEach { record -> PressCard { Text(record.description, fontWeight = FontWeight.Medium); Text("${record.date} • ${record.operationFlows().joinToString(" + ") { it.name.lowercase().replace('_', ' ') } }", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun PurchasesScreen(open: (String) -> Unit, message: (String) -> Unit) = Page("Compras") { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; var revision by remember { mutableIntStateOf(0) }; val records = remember(revision) { service.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.COMPRA } }; MetricGrid(listOf("Compras" to records.count { !service.isCancelled(it) }.toString(), "Total gasto" to money(records.filterNot(service::isCancelled).fold(BigDecimal.ZERO) { total, record -> total + (record.totalAmount ?: BigDecimal.ZERO) }))); Button({ open(RuralRoutes.NEW_PURCHASE) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova compra") }; if (records.isEmpty()) EmptyState("Sem compras", "Cadastre a primeira compra para atualizar estoque e caixa.") else records.forEach { record -> AuditableRecordRow(record, service.isCancelled(record), { reason -> runCatching { service.cancel(scope, record.id, reason); revision++ }.onSuccess { message("Compra cancelada com estorno registrado.") }.onFailure { message(it.message ?: "Não foi possível cancelar.") } }) } }
@Composable private fun SalesScreen(open: (String) -> Unit, message: (String) -> Unit) = Page("Vendas") { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { FarmManagementService(context) }; var revision by remember { mutableIntStateOf(0) }; val records = remember(revision) { service.records(CashViewScope.SelectedUnit(scope)).filter { it.type == br.com.simplificarural.domain.management.ManagementRecordType.VENDA && it.productName != null } }; MetricGrid(listOf("Vendas" to records.count { !service.isCancelled(it) }.toString(), "Recebido" to money(records.filterNot(service::isCancelled).fold(BigDecimal.ZERO) { total, record -> total + (record.totalAmount ?: BigDecimal.ZERO) }))); Button({ open(RuralRoutes.NEW_SALE) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova venda") }; if (records.isEmpty()) EmptyState("Sem vendas", "Registre uma venda para atualizar o estoque e caixa.") else records.forEach { record -> AuditableRecordRow(record, service.isCancelled(record), { reason -> runCatching { service.cancel(scope, record.id, reason); revision++ }.onSuccess { message("Venda cancelada com estorno registrado.") }.onFailure { message(it.message ?: "Não foi possível cancelar.") } }) } }
@Composable private fun AuditableRecordRow(record: br.com.simplificarural.domain.management.ManagementRecord, cancelled: Boolean, cancel: (String) -> Unit) { var menu by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }; PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(record.description, fontWeight = FontWeight.Bold); Text("${record.quantity?.stripTrailingZeros()?.toPlainString().orEmpty()} ${record.unit.orEmpty()} • ${money(record.totalAmount ?: BigDecimal.ZERO)}", color = RuralSecondaryText); if (cancelled) Text("Cancelado — mantido para auditoria", color = RuralDanger, style = MaterialTheme.typography.bodySmall) }; if (!cancelled) Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Mais ações") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Cancelar lançamento") }, { menu = false; confirm = true }) } } } }; if (confirm) AlertDialog({ confirm = false }, title = { Text("Cancelar lançamento?") }, text = { Text("O lançamento ficará no histórico e deixará de contar no caixa e estoque.") }, confirmButton = { TextButton({ cancel("Cancelado pelo usuário"); confirm = false }) { Text("Confirmar") } }, dismissButton = { TextButton({ confirm = false }) { Text("Voltar") } }) }
@Composable private fun SaleFormScreen(back: () -> Unit, message: (String) -> Unit) = Page("Nova venda", "Atualiza estoque e caixa ao salvar.", back) { val context = LocalContext.current; var product by remember { mutableStateOf("") }; var quantity by remember { mutableStateOf("") }; var unit by remember { mutableStateOf("unidades") }; var price by remember { mutableStateOf("") }; OutlinedTextField(product, { product = it }, Modifier.fillMaxWidth(), label = { Text("Produto") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(unit, { unit = it }, Modifier.fillMaxWidth(), label = { Text("Unidade") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Preço por unidade (R$)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { val service = FarmManagementService(context); val amount = quantity.replace(',', '.').toBigDecimal(); val unitPrice = price.replace(',', '.').toBigDecimal(); if (product.contains("bandeja", true) || product.contains("cartela", true)) service.registerEggTraySale(FarmContextStore(context).current(), product, amount, unit, unitPrice) else service.registerSale(FarmContextStore(context).current(), product, amount, unit, unitPrice) }.onSuccess { message("Venda salva no estoque e caixa."); back() }.onFailure { message(it.message ?: "Informe produto, quantidade e preço válidos.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar venda") } }
@Composable private fun SwineWeightScreen(back: () -> Unit, message: (String) -> Unit) = Page("Registrar pesagem", "Use o peso médio do lote; data e hora são registradas automaticamente.", back) { val context = LocalContext.current; var animals by remember { mutableStateOf("") }; var initial by remember { mutableStateOf("") }; var final by remember { mutableStateOf("") }; var days by remember { mutableStateOf("") }; OutlinedTextField(animals, { animals = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade de animais") }, shape = RoundedCornerShape(14.dp)); NutritionNumberField("Peso médio anterior", initial) { initial = it }; NutritionNumberField("Peso médio atual", final) { final = it }; OutlinedTextField(days, { days = it }, Modifier.fillMaxWidth(), label = { Text("Dias desde a pesagem anterior") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { FarmManagementService(context).registerSwineWeight(FarmContextStore(context).current(), animals.toInt(), initial.decimal(), final.decimal(), days.toInt()) }.onSuccess { message("Pesagem salva no desempenho dos suínos."); back() }.onFailure { message("Confira quantidade, pesos e dias informados.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar pesagem") } }
@Composable private fun SwineFarrowingScreen(back: () -> Unit, message: (String) -> Unit) = Page("Parto e desmame", "Registre números por matriz ou lote para gerar indicadores.", back) { val context = LocalContext.current; var alive by remember { mutableStateOf("") }; var dead by remember { mutableStateOf("") }; var weaned by remember { mutableStateOf("") }; OutlinedTextField(alive, { alive = it }, Modifier.fillMaxWidth(), label = { Text("Leitões nascidos vivos") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(dead, { dead = it }, Modifier.fillMaxWidth(), label = { Text("Nascidos mortos") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(weaned, { weaned = it }, Modifier.fillMaxWidth(), label = { Text("Desmamados (opcional)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { FarmManagementService(context).registerSwineFarrowing(FarmContextStore(context).current(), alive.toInt(), dead.ifBlank { "0" }.toInt(), weaned.ifBlank { null }?.toInt()) }.onSuccess { message("Parto salvo no histórico de matrizes."); back() }.onFailure { message("Informe pelo menos os nascidos vivos e revise os números.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar parto/desmame") } }
@Composable private fun AgendaScreen(back: () -> Unit) = Page("Agenda", "Marque manejo, saúde e compromissos.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val records = remember { AnimalRecordsService(context) }
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var revision by remember { mutableIntStateOf(0) }; val tasks = remember(revision) { records.tasks(scope) }
    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("O que precisa ser lembrado?") }, shape = RoundedCornerShape(14.dp))
    OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("Data (AAAA-MM-DD)") }, shape = RoundedCornerShape(14.dp))
    Button({ runCatching { records.schedule(scope, title, LocalDate.parse(date), AgendaType.MANEJO); title = ""; revision++ } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Adicionar à agenda") }
    if (tasks.isEmpty()) EmptyState("Nenhum agendamento", "Crie um lembrete ou confirme um evento de saúde.") else tasks.forEach { task -> AgendaRow(task.title, task.type.name.lowercase().replaceFirstChar { it.uppercase() }, task.dueDate.toString(), if (task.dueDate <= LocalDate.now()) RuralWarning else RuralInfo) }
}
@Composable private fun HealthScreen(back: () -> Unit, message: (String) -> Unit) = Page("Saúde", "Registre vacina, tratamento e retorno do animal ou lote.", back) {
    val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val store = remember { AnimalRecordsService(context) }; var revision by remember { mutableIntStateOf(0) }; val targets = remember(revision) { store.animals(scope).map { it.id to it.identification } + store.batches(scope).map { it.id to it.name } }; val events = remember(revision) { store.healthHistory(scope) }; var targetId by remember { mutableStateOf("") }; var product by remember { mutableStateOf("") }; var nextDate by remember { mutableStateOf("") }; var typeIndex by remember { mutableIntStateOf(0) }; val types = br.com.simplificarural.domain.health.HealthEventType.entries
    var expanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(expanded, { expanded = it }) { OutlinedTextField(targets.firstOrNull { it.first == targetId }?.second.orEmpty(), {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Animal ou lote") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, shape = RoundedCornerShape(14.dp)); ExposedDropdownMenu(expanded, { expanded = false }) { targets.forEach { target -> DropdownMenuItem({ Text(target.second) }, { targetId = target.first; expanded = false }) } } }
    var typeExpanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(typeExpanded, { typeExpanded = it }) { OutlinedTextField(types[typeIndex].name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Tipo de registro") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) }, shape = RoundedCornerShape(14.dp)); ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) { types.forEachIndexed { index, type -> DropdownMenuItem({ Text(type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }, { typeIndex = index; typeExpanded = false }) } } }
    OutlinedTextField(product, { product = it }, Modifier.fillMaxWidth(), label = { Text("Vacina, medicamento ou condição") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(nextDate, { nextDate = it }, Modifier.fillMaxWidth(), label = { Text("Próximo retorno (AAAA-MM-DD, opcional)") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { store.registerHealth(scope, targetId, product, types[typeIndex], nextDueDate = nextDate.ifBlank { null }?.let(LocalDate::parse)); product = ""; nextDate = ""; revision++ }.onSuccess { message("Registro de saúde salvo e retorno agendado quando informado.") }.onFailure { message("Selecione o animal/lote e informe o registro.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar registro de saúde") }
    MetricGrid(listOf("Registros" to events.size.toString(), "Vacinas" to events.count { it.type.name == "VACINA" }.toString(), "Retornos" to events.count { it.nextDueDate != null }.toString(), "Último" to events.firstOrNull()?.date?.toString().orEmpty().ifBlank { "—" }))
    if (events.isEmpty()) EmptyState("Sem histórico de saúde", "Selecione um animal ou lote e faça o primeiro lançamento.") else events.forEach { event -> PressCard { Text(event.productOrCondition, fontWeight = FontWeight.Bold); Text("${event.type.name.lowercase().replaceFirstChar { it.uppercase() }} • ${event.date}", color = RuralSecondaryText); event.nextDueDate?.let { Text("Retorno: $it", color = RuralWarning, style = MaterialTheme.typography.bodySmall) } } }
}
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
@Composable private fun ProductionChart(title: String, values: List<BigDecimal>, unit: String, dates: List<LocalDate>) { val total = values.fold(BigDecimal.ZERO, BigDecimal::plus); val max = values.maxOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE; PressCard { Text(title, fontWeight = FontWeight.Bold); Text("${total.stripTrailingZeros().toPlainString()} $unit no período", color = RuralDarkGreen, fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) { values.forEachIndexed { index, value -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) { Box(Modifier.fillMaxWidth().height((72f * value.divide(max, 3, java.math.RoundingMode.HALF_UP).toFloat()).coerceAtLeast(3f).dp), contentAlignment = Alignment.TopCenter) { Surface(Modifier.fillMaxSize(), color = RuralGreen, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) {} }; if (dates.size <= 7) Text(dates.getOrNull(index)?.dayOfMonth?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall, color = RuralSecondaryText) } } } } }
@Composable private fun MoreScreen(open: (String) -> Unit) = Page("Mais") { MoreGroup("Gestão", listOf("Produção" to RuralRoutes.PRODUCTION, "Pedidos" to RuralRoutes.ORDERS, "Agenda" to RuralRoutes.AGENDA, "Saúde" to RuralRoutes.HEALTH, "Compras" to RuralRoutes.PURCHASES, "Vendas" to RuralRoutes.SALES), open); MoreGroup("Análise", listOf("Relatórios" to RuralRoutes.PRODUCTION, "Indicadores" to RuralRoutes.PRODUCTION, "Histórico geral" to RuralRoutes.HISTORY), open); MoreGroup("Sistema", listOf("Embalagens de ovos" to RuralRoutes.PACKAGING, "Configurações" to RuralRoutes.SETTINGS, "Backup" to RuralRoutes.BACKUP, "Assistente Rural" to RuralRoutes.ASSISTANT, "Sobre" to RuralRoutes.ABOUT), open) }

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
@Composable private fun SettingsScreen(open: (String) -> Unit) = Page("Configurações") { listOf("Propriedade" to RuralRoutes.feature("Propriedade"), "Atividades" to RuralRoutes.ACTIVITIES, "Aves" to RuralRoutes.BIRDS, "Bovinos" to RuralRoutes.CATTLE, "Suínos" to RuralRoutes.SWINE, "Financeiro" to RuralRoutes.FINANCE, "Alertas" to RuralRoutes.AGENDA, "Backup" to RuralRoutes.BACKUP, "Aparência" to RuralRoutes.feature("Aparência"), "Geral" to RuralRoutes.feature("Geral")).forEach { (name, route) -> PressCard({ open(route) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium); Icon(Icons.Default.ChevronRight, null, tint = RuralSecondaryText) } } } }
@Composable private fun ActivitiesScreen(back: () -> Unit, message: (String) -> Unit) = Page("Atividades da propriedade", "Módulos habilitados nesta versão.", back) { listOf("Aves" to "Lotes, ovos, saúde e estoque", "Bovinos" to "Ficha individual, leite, alimentação e saúde", "Suínos" to "Engorda, matrizes, pesagem, partos e saúde").forEach { (name, detail) -> PressCard { Text(name, fontWeight = FontWeight.Medium); Text(detail, color = RuralSecondaryText) } }; PressCard { Text("Novas atividades", fontWeight = FontWeight.Medium); Text("Caprinos, ovinos e piscicultura serão habilitados quando houver formulários, indicadores e relatórios próprios — sem criar telas vazias.", color = RuralSecondaryText) } }
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
@Composable private fun BackupScreen(back: () -> Unit, message: (String) -> Unit) = Page("Backup local", "Mantém somente as três cópias mais recentes.", back) {
    val context = LocalContext.current
    val store = remember { LocalBackupStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    val copies = remember(revision) { store.recent() }
    PressCard { Text("Proteção automática", fontWeight = FontWeight.Bold); Text("O aplicativo agenda uma cópia local por hora. Backup na nuvem depende de conectar um provedor compatível.", color = RuralSecondaryText) }
    Button({ runCatching { store.create(); revision++ }.onSuccess { message("Backup local criado com sucesso.") }.onFailure { message("Não foi possível criar o backup agora.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("Criar backup agora") }
    if (copies.isEmpty()) EmptyState("Sem cópias locais", "Toque em criar backup agora para gerar a primeira cópia.") else copies.forEachIndexed { index, backup -> PressCard { Text("Cópia ${index + 1}", fontWeight = FontWeight.Bold); Text(backup.file.name, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
}
@Composable private fun AboutScreen(back: () -> Unit) = Page("Sobre o Simplifica Rural", "Organização local da rotina da fazenda.", back) {
    PressCard { Text("Simplifica Rural", fontWeight = FontWeight.Bold); Text("Registros de animais, produção, estoque, financeiro, agenda e secretária local.", color = RuralSecondaryText) }
    PressCard { Text("Como os dados funcionam", fontWeight = FontWeight.Bold); Text("Cada confirmação grava um lançamento local ligado à fazenda selecionada. A secretária prepara a ação; você revisa antes de salvar.", color = RuralSecondaryText) }
}
@Composable private fun PackagingScreen(back: () -> Unit, message: (String) -> Unit) = Page("Embalagens de ovos", "Defina quantos ovos entram em cada embalagem.", back) { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { PackagingConversionService(context) }; var name by remember { mutableStateOf("Bandeja padrão") }; var eggs by remember { mutableStateOf(service.eggsPerPackage(scope, name).toString()) }; OutlinedTextField(name, { name = it; eggs = service.eggsPerPackage(scope, it).toString() }, Modifier.fillMaxWidth(), label = { Text("Embalagem") }, shape = RoundedCornerShape(14.dp)); OutlinedTextField(eggs, { eggs = it }, Modifier.fillMaxWidth(), label = { Text("Ovos por embalagem") }, shape = RoundedCornerShape(14.dp)); Button({ runCatching { service.setEggsPerPackage(scope, name, eggs.toInt()) }.onSuccess { message("Conversão salva para $name.") }.onFailure { message("Informe uma embalagem e quantidade válida.") } }, Modifier.fillMaxWidth().height(48.dp)) { Text("Salvar conversão") }; listOf("Meia dúzia" to 6, "Dúzia" to 12, "Bandeja pequena" to 20, "Bandeja padrão" to 30).forEach { (label, amount) -> PressCard({ name = label; eggs = service.eggsPerPackage(scope, label).toString() }) { Text(label, fontWeight = FontWeight.Bold); Text("${service.eggsPerPackage(scope, label)} ovos", color = RuralSecondaryText) } } }
@Composable private fun OrdersScreen(back: () -> Unit, message: (String) -> Unit) = Page("Pedidos", "Pedidos só entram no caixa quando são entregues.", back) { val context = LocalContext.current; val scope = remember { FarmContextStore(context).current() }; val service = remember { RuralOrderService(context) }; var revision by remember { mutableIntStateOf(0) }; val orders = remember(revision) { service.list(scope) }; if (orders.isEmpty()) EmptyState("Sem pedidos", "A secretária cria pedidos quando uma venda precisa ser reagendada.") else orders.forEach { order -> var partial by remember(order.id) { mutableStateOf("") }; PressCard { Text("${order.customer} • ${order.product}", fontWeight = FontWeight.Bold); Text("${order.quantity.stripTrailingZeros().toPlainString()} ${order.unit} • ${money(order.total)} • ${order.status.name.lowercase().replace('_', ' ')}", color = RuralSecondaryText); order.note?.let { Text(it, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton({ service.review(order); revision++ }) { Text("Rever estoque") }; if (order.status == br.com.simplificarural.domain.orders.RuralOrderStatus.PRONTO_PARA_SEPARAR) TextButton({ runCatching { service.delivered(order); revision++ }.onFailure { message(it.message ?: "Não foi possível entregar.") } }) { Text("Entregar") } }; if (order.status in setOf(br.com.simplificarural.domain.orders.RuralOrderStatus.AGENDADO, br.com.simplificarural.domain.orders.RuralOrderStatus.PRONTO_PARA_SEPARAR)) { OutlinedTextField(partial, { partial = it }, Modifier.fillMaxWidth(), label = { Text("Quantidade para entrega parcial") }, shape = RoundedCornerShape(14.dp)); TextButton({ runCatching { service.deliverPartial(order, partial.replace(',', '.').toBigDecimal()); revision++ }.onSuccess { message("Parte entregue; saldo virou novo pedido pendente.") }.onFailure { message(it.message ?: "Quantidade parcial inválida ou estoque insuficiente.") } }) { Text("Entregar parcial") } } } } }
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

@Composable private fun PressCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState(); val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(120), label = "card"); var expanded by remember { mutableStateOf(false) }; val action = onClick ?: { expanded = !expanded }
    Card(Modifier.fillMaxWidth().scale(scale).clickable(interactionSource = interaction, indication = null, onClick = action), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE8EEE9)), elevation = CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { content(); if (onClick == null && expanded) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, Modifier.size(15.dp), tint = RuralGreen); Spacer(Modifier.width(6.dp)); Text("Detalhes exibidos acima. Toque novamente para recolher.", color = RuralSecondaryText, style = MaterialTheme.typography.labelSmall) } } }
}
@Composable private fun RecentProductionHistory(records: List<br.com.simplificarural.domain.management.ManagementRecord>, open: (String) -> Unit) {
    val production = records.filter { it.type in setOf(
        br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS,
        br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE,
        br.com.simplificarural.domain.management.ManagementRecordType.PESAGEM_SUINOS,
        br.com.simplificarural.domain.management.ManagementRecordType.PARTO_SUINOS
    ) }.sortedByDescending { it.createdAt }.take(5)
    Text("Últimas alterações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (production.isEmpty()) EmptyState("Sem produção registrada", "Os lançamentos confirmados de ovos, leite, pesagens e partos aparecerão aqui.") else production.forEach { record ->
        val route = when (record.type) {
            br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_OVOS -> RuralRoutes.BIRDS
            br.com.simplificarural.domain.management.ManagementRecordType.PRODUCAO_LEITE -> RuralRoutes.CATTLE
            else -> RuralRoutes.SWINE
        }
        PressCard({ open(route) }) { Text(record.description, fontWeight = FontWeight.Bold); Text("${record.date} • ${record.quantity?.stripTrailingZeros()?.toPlainString().orEmpty()} ${record.unit.orEmpty()}", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable private fun ActivityCard(title: String, headline: String, detail: String, value: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit, action: String = "Ver") = PressCard(click) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(10.dp), color = RuralLightGreen) { RuralAnimalIllustration(title, icon, Modifier.padding(3.dp).size(44.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Text(headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(detail, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ArrowForward, action, tint = RuralGreen, modifier = Modifier.size(22.dp)) }; value?.let { Text("Valor estimado: $it", color = RuralSuccess, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun RuralAnimalIllustration(title: String, fallback: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    val resource = when (title) { "Aves" -> R.drawable.illust_aves; "Bovinos" -> R.drawable.illust_bovinos; "Suínos" -> R.drawable.illust_suinos; else -> null }
    if (resource == null) Icon(fallback, title, modifier, tint = RuralGreen) else androidx.compose.foundation.Image(painterResource(resource), title, modifier)
}
@Composable private fun FinancialCard(result: br.com.simplificarural.domain.management.FinancialResult, click: () -> Unit) = PressCard(click) { Text("Financeiro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { MiniMetric("Entradas", money(result.revenue), RuralSuccess); MiniMetric("Saídas", money(result.totalCost), RuralDanger); MiniMetric("Resultado", money(result.cashGeneration), RuralDarkGreen) } }
@Composable private fun NoticeCard(open: () -> Unit) = PressCard(open) { Row { Text("Avisos e agenda", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Abrir agenda", color = RuralGreen) }; Text("Os retornos de saúde e lembretes confirmados aparecem aqui conforme forem registrados.", color = RuralSecondaryText) }
@Composable private fun DailyTipCard(eggsToday: Int) = PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = RuralGreen); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text("Dica do dia", fontWeight = FontWeight.Bold); Text(if (eggsToday == 0) "Ainda não há produção de ovos hoje. Registre pelo card de aves ou pela secretária." else "Você já confirmou $eggsToday ovos hoje. Confira embalagem e ração antes do próximo manejo.", color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
private fun money(value: BigDecimal): String = "R$ ${value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')}"
private fun stockCategory(name: String): String = when {
    name.contains("raç", true) || name.contains("silagem", true) || name.contains("farelo", true) -> "Alimentação"
    name.contains("vacina", true) || name.contains("remédio", true) || name.contains("medic", true) -> "Saúde"
    name.contains("martelo", true) || name.contains("ferrament", true) -> "Ferramentas"
    else -> "Outros"
}
private fun itemCategory(item: br.com.simplificarural.domain.management.StockBalance): String = item.inventoryCategory.takeIf { it in setOf("Alimentação", "Saúde", "Ferramentas", "Outros") } ?: stockCategory(item.productName)
@Composable private fun Notice(text: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = color, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text, color = RuralSecondaryText) }
@Composable private fun MiniMetric(label: String, value: String, color: Color) = Column { Text(label, color = RuralSecondaryText, style = MaterialTheme.typography.labelSmall); Text(value, color = color, fontWeight = FontWeight.Bold) }
@Composable private fun MetricGrid(metrics: List<Pair<String, String>>) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { metrics.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { (label, value) -> var expanded by remember(label, value) { mutableStateOf(false) }; Card(Modifier.weight(1f).clickable { expanded = !expanded }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE8EEE9))) { Column(Modifier.padding(10.dp)) { Text(label, color = RuralSecondaryText, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); if (expanded) Text("Indicador calculado a partir dos registros desta área.", color = RuralSecondaryText, style = MaterialTheme.typography.labelSmall) } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } } }
@Composable private fun QuickGrid(items: List<Pair<String, String>>, open: (String) -> Unit) { var moreOpen by remember { mutableStateOf(false) }; val primary = if (items.size > 4) items.take(3) + ("Mais" to "") else items.take(4); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { primary.forEach { (name, route) -> if (name == "Mais") Box(Modifier.weight(1f)) { ActionCard(name, null, Icons.Default.MoreHoriz, Modifier.fillMaxWidth()) { moreOpen = true }; DropdownMenu(moreOpen, { moreOpen = false }) { items.drop(3).forEach { (extraName, extraRoute) -> val target = if (extraRoute == RuralRoutes.GENERIC) RuralRoutes.feature(extraName) else extraRoute; DropdownMenuItem({ Text(extraName) }, { moreOpen = false; open(target) }) } } } else { val target = if (route == RuralRoutes.GENERIC) RuralRoutes.feature(name) else route; ActionCard(name, target, Icons.Default.AddCircle, Modifier.weight(1f)) { open(target) } } }; repeat(4 - primary.size) { Spacer(Modifier.weight(1f)) } } }
@Composable private fun ActionCard(title: String, route: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, click: () -> Unit) { val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState(); val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(120), label = "action"); Card(modifier.heightIn(min = 58.dp).scale(scale).clickable(interactionSource = interaction, indication = null, onClick = click), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE8EEE9))) { Column(Modifier.fillMaxSize().padding(vertical = 5.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { RuralVectorIcon(title, route, icon, Modifier.size(19.dp)); Spacer(Modifier.height(2.dp)); Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun RuralVectorIcon(label: String, route: String? = null, fallback: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val resource = when {
        route == RuralRoutes.BIRD_LOTS -> R.drawable.ic_rural_birds
        route == RuralRoutes.SWINE_LOTS || route == RuralRoutes.SWINE_FATTENING || route == RuralRoutes.SWINE_BREEDING || route == RuralRoutes.SOW_DETAIL -> R.drawable.ic_rural_swine
        route == RuralRoutes.ASSISTANT -> R.drawable.ic_rural_assistant
        route == RuralRoutes.AGENDA -> R.drawable.ic_rural_agenda
        route == RuralRoutes.HEALTH -> R.drawable.ic_rural_health
        route == RuralRoutes.STOCK || route == RuralRoutes.STOCK_DETAIL -> R.drawable.ic_rural_stock
        route == RuralRoutes.PURCHASES || route == RuralRoutes.NEW_PURCHASE -> R.drawable.ic_rural_stock
        route == RuralRoutes.SALES -> R.drawable.ic_rural_sale
        route == RuralRoutes.PRODUCTION -> R.drawable.ic_rural_production
        label.contains("ovos", true) -> R.drawable.ic_rural_egg
        label.contains("Aves", true) -> R.drawable.ic_rural_birds
        label.contains("Lotes", true) || label.contains("Animais", true) || label.contains("Leitões", true) -> R.drawable.ic_rural_lot
        label.contains("Alimentação", true) || label.contains("ração", true) -> R.drawable.ic_rural_feed
        label.contains("leite", true) -> R.drawable.ic_rural_milk
        label.contains("Bov", true) || label.contains("vaca", true) -> R.drawable.ic_rural_cattle
        label.contains("Suín", true) || label.contains("Matriz", true) || label.contains("peso", true) || label.contains("Partos", true) -> R.drawable.ic_rural_swine
        label.contains("Reprodução", true) || label.contains("Cio", true) || label.contains("Cobertura", true) || label.contains("Inseminação", true) || label.contains("Prenhez", true) || label.contains("Desmame", true) -> R.drawable.ic_rural_reproduction
        label.contains("Histórico", true) -> R.drawable.ic_rural_history
        label.contains("Ocorrências", true) || label.contains("Mortalidade", true) -> R.drawable.ic_rural_alert
        label.contains("Contas", true) -> R.drawable.ic_rural_bill
        label.contains("Entrada", true) || label.contains("receber", true) -> R.drawable.ic_rural_stock_in
        label.contains("Saída", true) || label.contains("pagar", true) -> R.drawable.ic_rural_stock_out
        label.contains("Backup", true) -> R.drawable.ic_rural_backup
        label.contains("Assistente", true) -> R.drawable.ic_rural_assistant
        label.contains("Vendas", true) || label.contains("venda", true) -> R.drawable.ic_rural_sale
        label.contains("Estoque", true) || label.contains("compra", true) || label.contains("Inventário", true) -> R.drawable.ic_rural_stock
        label.contains("Saúde", true) || label.contains("vacina", true) -> R.drawable.ic_rural_health
        label.contains("Produção", true) || label.contains("Relatórios", true) || label.contains("Indicadores", true) -> R.drawable.ic_rural_production
        label.contains("Agenda", true) || label.contains("Avisos", true) -> R.drawable.ic_rural_agenda
        label.contains("Financeiro", true) || label.contains("entrada", true) || label.contains("despesa", true) || label.contains("Vendas", true) -> R.drawable.ic_rural_finance
        else -> null
    }
    if (resource != null) androidx.compose.foundation.Image(painterResource(resource), label, modifier) else Icon(fallback, label, modifier, tint = RuralGreen)
}
@Composable private fun Segment(labels: List<String>, onSelected: (Int) -> Unit = {}) { var selected by remember { mutableIntStateOf(0) }; SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { labels.forEachIndexed { index, label -> SegmentedButton(selected = selected == index, onClick = { selected = index; onSelected(index) }, shape = SegmentedButtonDefaults.itemShape(index, labels.size)) { Text(label) } } } }
@Composable private fun SearchField(placeholder: String, onQueryChanged: (String) -> Unit = {}) { var text by remember { mutableStateOf("") }; OutlinedTextField(text, { text = it; onQueryChanged(it) }, Modifier.fillMaxWidth(), placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp)) }
@Composable private fun FormField(label: String) { var text by remember { mutableStateOf("") }; OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(label) }, shape = RoundedCornerShape(14.dp), minLines = if (label == "Observação") 2 else 1) }
@Composable private fun StatusChip(text: String, color: Color) = Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(20.dp)) { Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium) }
@Composable private fun StockRow(name: String, quantity: String, status: String, warning: Boolean, click: () -> Unit) = PressCard(click) { Row(verticalAlignment = Alignment.CenterVertically) { RuralVectorIcon(name, RuralRoutes.STOCK_DETAIL, Icons.Default.Inventory2, Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(quantity, color = RuralSecondaryText) }; StatusChip(status, if (warning) RuralWarning else RuralSuccess) } }
@Composable private fun SimpleRow(left: String, right: String) = PressCard { Row { Text(left, Modifier.weight(1f), fontWeight = FontWeight.Medium); Text(right, color = RuralSecondaryText) } }
@Composable private fun AgendaRow(title: String, subtitle: String, date: String, color: Color) = PressCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Event, null, tint = color); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = RuralSecondaryText) }; StatusChip(date, color) } }
@Composable private fun ProductionCard(title: String, value: String, detail: String) = PressCard { Text(title, fontWeight = FontWeight.Bold); Text(value, style = MaterialTheme.typography.headlineSmall, color = RuralDarkGreen, fontWeight = FontWeight.Bold); Text(detail, color = RuralSecondaryText); LinearProgressIndicator(.72f, Modifier.fillMaxWidth(), color = RuralGreen, trackColor = RuralLightGreen) }
@Composable private fun MoreGroup(title: String, entries: List<Pair<String, String>>, open: (String) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); entries.forEach { (name, route) -> val target = if (route == RuralRoutes.GENERIC) RuralRoutes.feature(name) else route; PressCard({ open(target) }) { Row(verticalAlignment = Alignment.CenterVertically) { RuralVectorIcon(name, target, Icons.Default.GridView, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(name, Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = RuralSecondaryText) } } } }
@Composable private fun EmptyState(title: String, detail: String) = PressCard { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(detail, color = RuralSecondaryText) }
