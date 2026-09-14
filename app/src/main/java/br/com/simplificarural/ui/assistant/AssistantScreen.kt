package br.com.simplificarural.ui.assistant

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.simplificarural.ai.AiModelRepository
import br.com.simplificarural.ai.AssistantResult
import br.com.simplificarural.ai.RuralAssistant
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.components.QuickGrid
import br.com.simplificarural.ui.components.SimpleText
import br.com.simplificarural.ui.theme.RuralSecondaryText
import kotlinx.coroutines.launch

@Composable internal fun AssistantScreen(back: () -> Unit, message: (String) -> Unit, open: (String) -> Unit, startVoice: Boolean) = Page("Assistente Rural", "Conversa com contexto da propriedade", back) {
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
    if (showReview) pending?.let { draft -> AlertDialog(onDismissRequest = { showReview = false }, title = { Text("Confira antes de salvar") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(draft.summary); draft.parameters.forEach { (label, value) -> if (value.isNotBlank()) SimpleText(label.replaceFirstChar { it.uppercase() }, value) }; Text("Nada será gravado até você confirmar.", color = RuralSecondaryText) } }, confirmButton = { Button({ reply = assistant.confirm(draft); pending = assistant.pendingDraft(); showReview = false; if (pending == null) open(draft.destination()) }) { Text("Confirmar e abrir módulo") } }, dismissButton = { TextButton({ showReview = false }) { Text("Corrigir depois") } }) }
}
