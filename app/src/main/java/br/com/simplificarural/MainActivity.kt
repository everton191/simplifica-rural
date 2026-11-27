package br.com.simplificarural

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import br.com.simplificarural.ai.Lfm3DownloadWorker
import br.com.simplificarural.ai.Lfm3QaEngine
import br.com.simplificarural.backup.BackupScheduler
import br.com.simplificarural.ui.RuralLaunchScreen
import br.com.simplificarural.ui.theme.SimplificaRuralTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackupScheduler.schedule(applicationContext)
        setContent { SimplificaRuralTheme { RuralLaunchScreen() } }
        if (Lfm3DownloadWorker.installedModel(applicationContext).isFile) {
            lifecycleScope.launch(Dispatchers.IO) { runCatching { Lfm3QaEngine.endpoint(applicationContext) } }
        }
    }
}
