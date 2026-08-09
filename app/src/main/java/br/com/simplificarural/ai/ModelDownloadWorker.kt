package br.com.simplificarural.ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads only the Android-compatible model bundle; never model code or executables. */
class ModelDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val model = model(applicationContext)
        if (model.profile == LocalModelProfile.SEM_MODELO) return@withContext Result.failure()
        val destination = modelFile(applicationContext)
        if (destination.exists() && destination.length() >= model.minimumBytes && sha256(destination) == model.sha256) return@withContext Result.success()

        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        if (temporary.exists() && temporary.length() >= model.minimumBytes && sha256(temporary) == model.sha256 && temporary.renameTo(destination)) {
            return@withContext Result.success()
        }
        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext Result.retry()
            val total = connection.contentLengthLong
            if (total in 1 until model.minimumBytes) return@withContext Result.failure()

            RandomAccessFile(temporary, "rw").channel.use { channel ->
                val lock = channel.tryLock() ?: return@withContext Result.retry()
                try {
                    channel.truncate(0)
                    channel.position(0)
                    connection.inputStream.use { input ->
                        java.nio.channels.Channels.newOutputStream(channel).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        setProgress(
                            Data.Builder()
                                .putLong(KEY_DOWNLOADED_BYTES, downloaded)
                                .putLong(KEY_TOTAL_BYTES, total)
                                .build()
                        )
                    }
                    output.flush()
                        }
                    }
                } finally {
                    if (lock.isValid) lock.release()
                }
            }
            if (temporary.length() < model.minimumBytes || sha256(temporary) != model.sha256) {
                temporary.delete()
                return@withContext Result.retry()
            }
            if (!temporary.renameTo(destination)) return@withContext Result.retry()
            Result.success()
        } catch (error: Exception) {
            Log.e(TAG, "Não foi possível baixar o modelo local", error)
            Result.retry()
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureActive() {
        if (isStopped) throw InterruptedException("Download cancelado")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "download-rural-ai-model-v2"
        const val KEY_DOWNLOADED_BYTES = "downloadedBytes"
        const val KEY_TOTAL_BYTES = "totalBytes"
        private const val TAG = "RuralAiDownload"

        fun model(context: Context): LocalModelDescriptor = DeviceModelSelector(context).selected()
        fun modelFile(context: Context): File = File(File(context.filesDir, "models"), model(context).fileName)
    }
}
