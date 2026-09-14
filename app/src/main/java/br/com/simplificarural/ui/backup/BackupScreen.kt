package br.com.simplificarural.ui.backup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.backup.LocalBackupStore
import br.com.simplificarural.ui.components.EmptyState
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.theme.RuralSecondaryText

@Composable internal fun BackupScreen(back: () -> Unit, message: (String) -> Unit) = Page("Backup local", "Mantém somente as três cópias mais recentes.", back) {
    val context = LocalContext.current
    val store = remember { LocalBackupStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    val copies = remember(revision) { store.recent() }
    PressCard { Text("Proteção automática", fontWeight = FontWeight.Bold); Text("O aplicativo agenda uma cópia local por hora. Backup na nuvem depende de conectar um provedor compatível.", color = RuralSecondaryText) }
    Button({ runCatching { store.create(); revision++ }.onSuccess { message("Backup local criado com sucesso.") }.onFailure { message("Não foi possível criar o backup agora.") } }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("Criar backup agora") }
    if (copies.isEmpty()) EmptyState("Sem cópias locais", "Toque em criar backup agora para gerar a primeira cópia.") else copies.forEachIndexed { index, backup -> PressCard { Text("Cópia ${index + 1}", fontWeight = FontWeight.Bold); Text(backup.file.name, color = RuralSecondaryText, style = MaterialTheme.typography.bodySmall) } }
}
