package br.com.simplificarural.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

enum class LocalModelProfile { SEM_MODELO, LEVE, PADRAO }

data class LocalModelDescriptor(val profile: LocalModelProfile, val fileName: String, val url: String, val minimumBytes: Long, val sha256: String)

/** Chooses once per installation using capabilities, never just a brand name of chipset. */
class DeviceModelSelector(private val context: Context) {
    private val prefs = context.getSharedPreferences("local_model_selection", Context.MODE_PRIVATE)

    fun selected(): LocalModelDescriptor = prefs.getString("profile", null)?.let { saved ->
        catalog[LocalModelProfile.valueOf(saved)]
    } ?: recommend().also { prefs.edit().putString("profile", it.profile.name).apply() }

    fun recommend(): LocalModelDescriptor {
        val isArm64 = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", true) }
        val memory = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }.availMem
        val storage = StatFs(context.filesDir.path).availableBytes
        return when {
            !isArm64 || storage < 700L * MB || memory < 1_700L * MB -> catalog.getValue(LocalModelProfile.SEM_MODELO)
            storage >= 1_400L * MB && memory >= 3_000L * MB -> catalog.getValue(LocalModelProfile.PADRAO)
            else -> catalog.getValue(LocalModelProfile.LEVE)
        }
    }

    companion object {
        private const val MB = 1024L * 1024L
        val catalog = mapOf(
            LocalModelProfile.SEM_MODELO to LocalModelDescriptor(LocalModelProfile.SEM_MODELO, "", "", 0, ""),
            LocalModelProfile.LEVE to LocalModelDescriptor(LocalModelProfile.LEVE, "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm", "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm", 330L * MB, "2DF6821EC12702DAFD33915E7A1A1ADC7C4B053F3672FD9555DFAF3A114C4139"),
            LocalModelProfile.PADRAO to LocalModelDescriptor(LocalModelProfile.PADRAO, "LFM2.5-1.2B-Instruct_int4.litertlm", "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm", 700L * MB, "A28B5C59AC204E2E51C1F98D2D6DB6982F0E12DA59A268FE498EDCB33237E906")
        )
    }
}
