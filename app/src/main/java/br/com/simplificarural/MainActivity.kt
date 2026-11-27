package br.com.simplificarural

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.simplificarural.backup.BackupScheduler
import br.com.simplificarural.ui.RuralLaunchScreen
import br.com.simplificarural.ui.theme.SimplificaRuralTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackupScheduler.schedule(applicationContext)
        setContent { SimplificaRuralTheme { RuralLaunchScreen() } }
    }
}
