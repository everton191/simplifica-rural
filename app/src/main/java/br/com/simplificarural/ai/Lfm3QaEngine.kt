package br.com.simplificarural.ai

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.UUID

internal object Lfm3QaEngine {
    data class Endpoint(val base: String, val key: String)
    enum class Status { IDLE, STARTING, READY, FAILED }

    private val mutableStatus = MutableStateFlow(Status.IDLE)
    val status = mutableStatus.asStateFlow()

    private var running: Process? = null
    private var endpoint: Endpoint? = null
    private var loadedVersion = 0L

    @Synchronized
    fun endpoint(context: Context): Endpoint {
        val model = Lfm3DownloadWorker.installedModel(context)
        require(model.isFile) { "Para entender frases mais longas, baixe o modelo em Mais → Modelo de IA para teste." }
        if (running?.isAlive == true && loadedVersion == model.lastModified()) {
            endpoint?.let { return it }
        }
        stop()
        mutableStatus.value = Status.STARTING
        val version = model.lastModified()
        return try {
            start(context, model).also {
                endpoint = it
                loadedVersion = version
                mutableStatus.value = Status.READY
            }
        } catch (failure: Exception) {
            stop()
            mutableStatus.value = Status.FAILED
            throw failure
        }
    }

    private fun start(context: Context, model: File): Endpoint {
        val nativeDirectory = context.applicationInfo.nativeLibraryDir
        val program = File(nativeDirectory, "liblfm3_server.so")
        check(program.canExecute()) { "O motor LFM3 não está disponível nesta arquitetura." }
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val address = Endpoint("http://127.0.0.1:$port", UUID.randomUUID().toString())
        val engineLog = File(context.cacheDir, "lfm3-engine.log")
        val process = ProcessBuilder(
            program.absolutePath, "-m", model.absolutePath, "--host", "127.0.0.1", "--port", port.toString(),
            "--api-key", address.key, "--jinja", "--ctx-size", "1536", "--threads", "4",
            "--threads-batch", "4", "-ngl", "0", "--parallel", "1"
        ).apply {
            environment()["LD_LIBRARY_PATH"] = nativeDirectory
            directory(File(nativeDirectory))
            redirectErrorStream(true)
            redirectOutput(engineLog)
        }.start()
        running = process
        repeat(60) {
            if (!process.isAlive) error("O motor local encerrou antes de carregar o modelo.")
            val ready = runCatching {
                val connection = URL("${address.base}/health").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 500
                    connection.readTimeout = 500
                    connection.setRequestProperty("Authorization", "Bearer ${address.key}")
                    connection.responseCode == HttpURLConnection.HTTP_OK
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(false)
            if (ready) return address
            SystemClock.sleep(400)
        }
        stop()
        error("O motor LFM3 não ficou pronto a tempo neste aparelho.")
    }

    @Synchronized
    fun stop() {
        running?.destroy()
        running = null
        endpoint = null
        mutableStatus.value = Status.IDLE
    }
}
