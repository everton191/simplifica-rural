package br.com.simplificarural.ai

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thin client for the one model owned by the Simplifica IA package. */
class SharedAiClient(private val context: Context) {
    private val provider = Uri.parse("content://br.com.simplifica.ai.provider")

    fun status(): SharedAiStatus = context.contentResolver.call(provider, "status", null, null).toStatus()

    /**
     * Asks the shared provider to start the model download when it is reachable.
     *
     * The provider may be temporarily unresolvable (for example right after this
     * app is reinstalled, before the system settles package visibility). That is
     * a transient infrastructure state, never a fatal error: callers already treat
     * a non-ready status as "waiting" and retry on the next cold start or when the
     * user taps "Preparar IA". So an unavailable provider returns a waiting status
     * instead of throwing. Only the expected unavailability failures are caught;
     * anything else keeps propagating.
     */
    fun ensureModel(): SharedAiStatus {
        if (!providerAvailable()) {
            Log.w(TAG, "shared AI provider unavailable; model download deferred")
            return WAITING
        }
        return try {
            context.contentResolver.call(provider, "ensure_model", null, null).toStatus()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "shared AI provider unavailable during ensure_model; download deferred")
            WAITING
        } catch (e: SecurityException) {
            Log.w(TAG, "shared AI provider denied access; download deferred")
            WAITING
        }
    }

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

    /** Best-effort visibility check; any lookup failure means "not clearly present". */
    private fun providerAvailable(): Boolean {
        val authority = provider.authority ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0)) != null
            } else {
                legacyProviderLookup(authority)
            }
        } catch (e: RuntimeException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyProviderLookup(authority: String) =
        context.packageManager.resolveContentProvider(authority, 0) != null

    private companion object {
        const val TAG = "SharedAiClient"

        /** Matches what [AiModelRepository.downloadProgress] already renders as "AGUARDANDO". */
        val WAITING = SharedAiStatus(
            compatible = true,
            incompatibilityReason = null,
            ready = false,
            downloading = false,
            downloadedBytes = 0,
            totalBytes = 0
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
