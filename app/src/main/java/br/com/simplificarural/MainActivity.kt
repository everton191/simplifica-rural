package br.com.simplificarural

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import br.com.simplificarural.ai.AiModelRepository
import br.com.simplificarural.ai.GemmaLocalAiEngine
import br.com.simplificarural.domain.property.FarmContextStore
import br.com.simplificarural.backup.BackupScheduler
import br.com.simplificarural.ui.RuralLaunchScreen
import br.com.simplificarural.ui.theme.SimplificaRuralTheme
import kotlinx.coroutines.launch

/**
 * The product screens are intentionally not created yet. The local AI layer is
 * kept separate in the ai package and will be connected after UI approval.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackupScheduler.schedule(applicationContext)
        val models = AiModelRepository(applicationContext)
        models.enqueueAutomaticDownload()
        if (models.isInstalled()) lifecycleScope.launch {
            runCatching { GemmaLocalAiEngine(applicationContext).interpret("consulte o resumo", FarmContextStore(applicationContext).current()) }
        }
        setContent { SimplificaRuralTheme { RuralLaunchScreen() } }
    }
}
