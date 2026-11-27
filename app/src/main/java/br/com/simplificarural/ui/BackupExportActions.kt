package br.com.simplificarural.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import br.com.simplificarural.backup.LocalBackup
import br.com.simplificarural.backup.LocalBackupStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BACKUP_MIME = "application/json"

@Composable
internal fun BackupExportActions(store: LocalBackupStore, onCreated: () -> Unit, message: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun createAndUse(success: String?, failure: String, use: suspend (LocalBackup) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val backup = withContext(Dispatchers.IO) { store.create() }
                onCreated()
                use(backup)
                success?.let(message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message(failure)
            } finally {
                busy = false
            }
        }
    }

    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        if (uri != null) createAndUse("Backup salvo.", "Não foi possível salvar a cópia neste local.") { backup ->
            withContext(Dispatchers.IO) {
                val output = checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                output.use { destination -> backup.file.inputStream().use { it.copyTo(destination) } }
            }
        }
    }
    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
        runCatching { saveDocument.launch("simplifica-rural-$timestamp.json") }
            .onFailure { message("O seletor de arquivos está indisponível.") }
    }) { Text("Salvar arquivo") }
    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
        createAndUse(null, "Não foi possível abrir o compartilhamento.") { backup ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.backups", backup.file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = BACKUP_MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, backup.file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Enviar backup"))
        }
    }) { Text("Enviar backup") }
}
