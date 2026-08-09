package br.com.simplificarural.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiModelRepository(private val context: Context) {
    fun isInstalled(): Boolean { val model = ModelDownloadWorker.model(context); return model.profile != LocalModelProfile.SEM_MODELO && ModelDownloadWorker.modelFile(context).length() >= model.minimumBytes }

    /** The UI can show bytes and percentage while WorkManager owns retries across app restarts. */
    fun downloadProgress(): Flow<ModelDownloadProgress> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_WORK_NAME)
        .map { works -> works.firstOrNull()?.let { work ->
            val downloaded = work.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0)
            val total = work.progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0)
            ModelDownloadProgress(work.state == WorkInfo.State.RUNNING, downloaded, total, work.state.name)
        } ?: ModelDownloadProgress(false, 0, 0, "AGUARDANDO") }

    /** Starts automatically when the user has accepted the model license, only on Wi-Fi. */
    fun enqueueAutomaticDownload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .addTag(ModelDownloadWorker.UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ModelDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

data class ModelDownloadProgress(val downloading: Boolean, val downloadedBytes: Long, val totalBytes: Long, val state: String) {
    val percent: Int get() = if (totalBytes <= 0) 0 else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}
