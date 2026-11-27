package br.com.simplificarural.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
internal fun rememberRuralAudioGuide(): RuralAudioGuide {
    val context = LocalContext.current.applicationContext
    val guide = remember(context) { RuralAudioGuide(context) }
    DisposableEffect(guide) { onDispose { guide.close() } }
    return guide
}

internal class RuralAudioGuide(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending = ""

    fun speak(text: String) {
        pending = text
        if (ready) {
            play()
        } else if (engine == null) {
            engine = TextToSpeech(context) { status ->
                main.post {
                    val current = engine ?: return@post
                    if (status == TextToSpeech.SUCCESS && current.setLanguage(Locale.forLanguageTag("pt-BR")) >= TextToSpeech.LANG_AVAILABLE) {
                        ready = true
                        play()
                    } else {
                        close()
                        unavailable()
                    }
                }
            }
        }
    }

    private fun play() {
        if (engine?.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "rural-audio-guide") == TextToSpeech.ERROR) {
            unavailable()
        }
    }

    private fun unavailable() {
        Toast.makeText(context, "A voz em português está indisponível neste aparelho.", Toast.LENGTH_LONG).show()
    }

    fun close() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        pending = ""
    }
}
