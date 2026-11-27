package br.com.simplificarural.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import br.com.simplificarural.MainActivity
import br.com.simplificarural.R
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class Lfm3DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if ("arm64-v8a" !in Build.SUPPORTED_ABIS) return@withContext failed("LFM3 requer Android ARM64 neste teste.")
        try {
            showProgress(0, 0)
            downloadLock.withLock { downloadLatest() }
            runCatching {
                notifications.notify(RESULT_NOTIFICATION, notification("Modelo LFM 3 instalado").build())
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            if (runAttemptCount < 4) Result.retry() else failed("Download interrompido. Toque para retomar.")
        } catch (invalid: Exception) {
            failed(invalid.message ?: "Não foi possível verificar o arquivo do modelo.")
        }
    }

    private suspend fun downloadLatest() {
        val release = open("$HUB/api/models/$REPO?blobs=true").useResponse {
            JSONObject(inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
        val revision = release.getString("sha")
        require(REVISION.matches(revision)) { "Revisão do Hugging Face inválida." }
        val siblings = release.getJSONArray("siblings")
        val artifact = (0 until siblings.length()).asSequence().map(siblings::getJSONObject)
            .firstOrNull { it.optString("rfilename") == FILE_NAME }?.getJSONObject("lfs")
            ?: error("O arquivo oficial do modelo não foi encontrado.")
        val expectedHash = artifact.getString("sha256").lowercase()
        val size = artifact.getLong("size")
        require(SHA256.matches(expectedHash) && size in (64L shl 20)..(3L shl 30)) {
            "Tamanho ou SHA-256 inválido no Hugging Face."
        }

        val target = installedModel(applicationContext)
        val folder = requireNotNull(target.parentFile)
        require(folder.isDirectory || folder.mkdirs()) { "Pasta do modelo indisponível." }
        if (target.length() == size && digest(target) == expectedHash) return
        val part = File(folder, "$expectedHash.part")
        if (part.length() > size) check(part.delete()) { "Arquivo parcial inválido." }
        check(folder.usableSpace >= size - part.length() + (64L shl 20)) {
            "Espaço insuficiente para concluir o download."
        }

        if (part.length() < size) fetchBytes(revision, size, part)
        check(part.length() == size) { "O tamanho do arquivo difere do publicado." }
        if (digest(part) != expectedHash) {
            part.delete()
            error("SHA-256 inválido. Baixe o modelo novamente.")
        }
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private suspend fun fetchBytes(revision: String, size: Long, part: File) {
        val offset = part.length()
        val request = open("$HUB/$REPO/resolve/$revision/$FILE_NAME")
        if (offset > 0) request.setRequestProperty("Range", "bytes=$offset-")
        request.useResponse {
            val start = when (responseCode) {
                HttpURLConnection.HTTP_OK -> 0L
                HttpURLConnection.HTTP_PARTIAL -> {
                    val range = getHeaderField("Content-Range").orEmpty()
                    check(range.startsWith("bytes $offset-") && range.endsWith("/$size")) {
                        "O servidor devolveu uma faixa inválida para retomar o download."
                    }
                    offset
                }
                else -> throw IOException("HTTP $responseCode")
            }
            check(requireNotNull(part.parentFile).usableSpace >= size - start + (64L shl 20)) {
                "Espaço insuficiente para continuar o download."
            }
            RandomAccessFile(part, "rw").use { output ->
                output.setLength(start)
                output.seek(start)
                inputStream.use { input ->
                    val buffer = ByteArray(128 * 1024)
                    var bytes = start
                    var lastUpdate = 0L
                    while (bytes < size) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), size - bytes).toInt())
                        if (count < 0) throw IOException("Download incompleto")
                        output.write(buffer, 0, count)
                        bytes += count
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= 1_000 || bytes == size) {
                            setProgress(workDataOf("received" to bytes, "total" to size))
                            showProgress(bytes, size)
                            lastUpdate = now
                        }
                    }
                    output.fd.sync()
                }
            }
        }
    }

    private val notifications get() = applicationContext.getSystemService(NotificationManager::class.java)

    private fun notification(message: String): NotificationCompat.Builder {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Download do modelo LFM 3", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_rural_assistant)
            .setContentTitle("Simplifica Rural")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
    }

    private suspend fun showProgress(received: Long, total: Long) {
        val percent = if (total > 0) (100 * received / total).toInt() else 0
        val progress = notification(if (total > 0) "Baixando LFM 3 · $percent%" else "Preparando download do LFM 3")
            .setOngoing(true)
            .setProgress(100, percent, total <= 0)
            .build()
        setForeground(ForegroundInfo(PROGRESS_NOTIFICATION, progress, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
    }

    private suspend fun digest(file: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val size = input.read(buffer)
                if (size < 0) break
                sha.update(buffer, 0, size)
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 30_000
        setRequestProperty("Accept-Encoding", "identity")
    }

    private inline fun <T> HttpURLConnection.useResponse(block: HttpURLConnection.() -> T): T =
        try { block() } finally { disconnect() }

    private fun failed(message: String) = Result.failure(workDataOf("error" to message))

    companion object {
        private const val CHANNEL = "lfm3-download"
        private const val PROGRESS_NOTIFICATION = 310
        private const val RESULT_NOTIFICATION = 311
        const val WORK_NAME = "lfm3-verified-download"
        private const val HUB = "https://huggingface.co"
        private const val REPO = "LiquidAI/LFM3-1.7B-GGUF"
        private const val FILE_NAME = "LFM3-1.7B-Q4_K_M.gguf"
        private val REVISION = Regex("[a-f0-9]{40}")
        private val SHA256 = Regex("[a-f0-9]{64}")
        private val downloadLock = Mutex()

        fun installedModel(context: Context) = File(context.filesDir, "lfm3/$FILE_NAME")
    }
}
