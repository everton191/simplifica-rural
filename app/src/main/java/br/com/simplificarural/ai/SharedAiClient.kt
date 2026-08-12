package br.com.simplificarural.ai

import android.content.Context
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin client for the one model owned by the Simplifica IA package. */
class SharedAiClient(private val context: Context) {
    private val provider = Uri.parse("content://br.com.simplifica.ai.provider")

    fun status(): SharedAiStatus = context.contentResolver.call(provider, "status", null, null).toStatus()

    fun ensureModel(): SharedAiStatus = context.contentResolver.call(provider, "ensure_model", null, null).toStatus()

    suspend fun generate(system: String, prompt: String): String = withContext(Dispatchers.IO) {
        val result = context.contentResolver.call(provider, "generate", null, Bundle().apply {
            putString("system", system)
            putString("prompt", prompt)
        }) ?: error("O Simplifica IA não retornou dados.")
        result.getString("response").orEmpty().ifBlank { error("O Gemma 4 E2B não retornou texto.") }
    }

    private fun Bundle?.toStatus(): SharedAiStatus {
        val data = requireNotNull(this) { "Instale o Simplifica IA para compartilhar o modelo." }
        return SharedAiStatus(
            compatible = data.getBoolean("compatible", true),
            incompatibilityReason = data.getString("incompatibilityReason"),
            ready = data.getBoolean("modelReady"),
            downloading = data.getBoolean("downloading") || data.getBoolean("downloadQueued"),
            downloadedBytes = data.getLong("downloadedBytes").takeIf { it > 0 } ?: data.getLong("modelBytes"),
            totalBytes = data.getLong("totalBytes").takeIf { it > 0 } ?: data.getLong("minimumBytes")
        )
    }
}

data class SharedAiStatus(
    val compatible: Boolean,
    val incompatibilityReason: String?,
    val ready: Boolean,
    val downloading: Boolean,
    val downloadedBytes: Long,
    val totalBytes: Long
)
