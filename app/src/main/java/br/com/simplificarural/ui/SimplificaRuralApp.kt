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
import br.com.simplificarural.ui.assistant.*
import br.com.simplificarural.ui.cattle.*
import br.com.simplificarural.ui.common.*
import br.com.simplificarural.ui.backup.*
import br.com.simplificarural.ui.components.*
import br.com.simplificarural.ui.financial.*
import br.com.simplificarural.ui.health.*
import br.com.simplificarural.ui.home.*
import br.com.simplificarural.ui.inventory.*
import br.com.simplificarural.ui.navigation.*
import br.com.simplificarural.ui.orders.*
import br.com.simplificarural.ui.production.*
import br.com.simplificarural.ui.poultry.*
import br.com.simplificarural.ui.settings.*
import br.com.simplificarural.ui.swine.*
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
