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
            storage >= 3_100L * MB && memory >= 4_000L * MB -> catalog.getValue(LocalModelProfile.PADRAO)
            else -> catalog.getValue(LocalModelProfile.SEM_MODELO)
        }
    }

    companion object {
        private const val MB = 1024L * 1024L
        val catalog = mapOf(
            LocalModelProfile.SEM_MODELO to LocalModelDescriptor(LocalModelProfile.SEM_MODELO, "", "", 0, ""),
            LocalModelProfile.LEVE to LocalModelDescriptor(LocalModelProfile.LEVE, "", "", 0, ""),
            LocalModelProfile.PADRAO to LocalModelDescriptor(LocalModelProfile.PADRAO, "gemma-4-E2B-it.litertlm", "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true", 2_588_147_712L, "181938105E0EEFD105961417E8DA75903EACDA102C4FCE9CE90F50B97139A63C")
        )
    }
}
