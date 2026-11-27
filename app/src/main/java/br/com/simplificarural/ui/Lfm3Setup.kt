package br.com.simplificarural.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import br.com.simplificarural.ai.Lfm3DownloadWorker
import br.com.simplificarural.ai.Lfm3QaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun Lfm3Setup(onOpen: (() -> Unit)? = null) {
    val context = LocalContext.current
    val work = remember(context) { WorkManager.getInstance(context) }
    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<Lfm3DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork(Lfm3DownloadWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { enqueue() }
    val tasks by remember(work) { work.getWorkInfosForUniqueWorkFlow(Lfm3DownloadWorker.WORK_NAME) }
        .collectAsState(initial = emptyList())
    val task = tasks.firstOrNull { !it.state.isFinished } ?: tasks.firstOrNull()
    val model = Lfm3DownloadWorker.installedModel(context)
    val ready = model.isFile
    val engineStatus by Lfm3QaEngine.status.collectAsState()
    LaunchedEffect(model.lastModified()) {
        if (ready) withContext(Dispatchers.IO) { runCatching { Lfm3QaEngine.endpoint(context.applicationContext) } }
    }
    val active = task?.state?.isFinished == false
    val received = task?.progress?.getLong("received", 0L) ?: 0L
    val total = task?.progress?.getLong("total", 0L) ?: 0L
    val progress = if (total == 0L) 0f else (received.toDouble() / total).toFloat().coerceIn(0f, 1f)
    val status = when (task?.state) {
        WorkInfo.State.RUNNING -> if (total == 0L) "Consultando o Hugging Face…" else "Baixando modelo · ${(progress * 100).toInt()}%"
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Aguardando conexão ou nova tentativa."
        WorkInfo.State.SUCCEEDED -> "Modelo verificado."
        WorkInfo.State.FAILED -> task.outputData.getString("error") ?: "Não foi possível baixar o modelo."
        WorkInfo.State.CANCELLED -> "Download pausado. Você pode continuar depois."
        else -> if (ready) "Modelo pronto para teste." else "Modelo ainda não baixado."
    }

    val engineMessage = when (engineStatus) {
        Lfm3QaEngine.Status.IDLE -> "Modelo instalado"
        Lfm3QaEngine.Status.STARTING -> "Preparando a IA…"
        Lfm3QaEngine.Status.READY -> "IA pronta"
        Lfm3QaEngine.Status.FAILED -> "IA indisponível neste aparelho"
    }
    if (onOpen != null) {
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text("LFM 3 · ${if (active) status else if (ready) engineMessage else "Instalar modelo"}")
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(status, style = MaterialTheme.typography.titleMedium)
        if (ready) Text(engineMessage)
        Text("O LFM3 ajuda a entender frases mais longas. O download vem do Hugging Face e usa cerca de 1,1 GB. Prefira Wi-Fi.")
        if (active) {
            if (total > 0L) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton({ work.cancelUniqueWork(Lfm3DownloadWorker.WORK_NAME) }) { Text("Pausar") }
        } else {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else enqueue()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (ready) "Verificar atualização" else if (task?.state == WorkInfo.State.CANCELLED) "Continuar download" else "Instalar LFM 3")
            }
        }
        Text("Você pode continuar usando o app. Permita as notificações para acompanhar o download fora desta tela.")
    }
}
