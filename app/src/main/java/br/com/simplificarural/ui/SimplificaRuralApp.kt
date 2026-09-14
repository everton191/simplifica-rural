@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package br.com.simplificarural.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.navigation.*
import br.com.simplificarural.ui.theme.*
import kotlinx.coroutines.launch

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
