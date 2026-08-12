package br.com.simplificarural.ai

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

class AiModelRepository(private val context: Context) {
    private val sharedAi = SharedAiClient(context)

    fun isInstalled(): Boolean = runCatching { sharedAi.status().ready }.getOrDefault(false)

    /** Mirrors the provider progress so every client sees the same download. */
    fun downloadProgress(): Flow<ModelDownloadProgress> = flow {
        while (true) {
            val status = runCatching { sharedAi.status() }.getOrNull()
            val state = when {
                status?.compatible == false -> "INCOMPATIVEL"
                status?.ready == true -> "PRONTO"
                status?.downloading == true -> "BAIXANDO"
                else -> "AGUARDANDO"
            }
            emit(ModelDownloadProgress(
                status?.downloading == true,
                status?.downloadedBytes ?: 0,
                status?.totalBytes ?: 0,
                state,
                status?.incompatibilityReason
            ))
            delay(1_000)
        }
    }

    /** The shared provider owns the Wi-Fi-only, resumable download. */
    fun enqueueAutomaticDownload() {
        sharedAi.ensureModel()
    }
}

data class ModelDownloadProgress(val downloading: Boolean, val downloadedBytes: Long, val totalBytes: Long, val state: String, val message: String? = null) {
    val percent: Int get() = if (totalBytes <= 0) 0 else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}
