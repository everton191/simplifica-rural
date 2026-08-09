package br.com.simplificarural.backup

import android.content.Context
import java.io.File

enum class CloudBackupProviderId { GOOGLE_DRIVE, ONEDRIVE }

sealed interface CloudUploadResult {
    data object Uploaded : CloudUploadResult
    data object NotConnected : CloudUploadResult
    data class Failed(val reason: String) : CloudUploadResult
}

/**
 * Boundary for cloud providers. Each provider must receive an OAuth connection
 * created by the account owner before it can upload anything.
 */
interface CloudBackupProvider {
    val id: CloudBackupProviderId
    suspend fun isConnected(): Boolean
    suspend fun upload(backup: File): CloudUploadResult
}

class CloudBackupRegistry(context: Context) {
    private val preferences = context.getSharedPreferences("cloud_backup", Context.MODE_PRIVATE)

    fun selectedProvider(): CloudBackupProviderId? = preferences.getString("provider", null)
        ?.let { value -> CloudBackupProviderId.entries.firstOrNull { it.name == value } }

    fun activeProvider(): CloudBackupProvider? {
        // OAuth client IDs, redirect URI and account connection are deliberately
        // not embedded in the app. A real provider is registered after setup.
        return null
    }
}
