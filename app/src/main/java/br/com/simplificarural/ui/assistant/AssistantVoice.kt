package br.com.simplificarural.ui.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import br.com.simplificarural.ui.theme.RuralDarkGreen

@Composable internal fun VoiceHoldButton(modifier: Modifier = Modifier, onRecognized: (String) -> Unit, onMessage: (String) -> Unit, startImmediately: Boolean = false) {
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
