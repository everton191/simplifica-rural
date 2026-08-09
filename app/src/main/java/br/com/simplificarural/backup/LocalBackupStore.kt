package br.com.simplificarural.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class LocalBackup(val file: File, val createdAt: Instant)

/**
 * Creates self-contained, versioned snapshots of the local operational data.
 * Files are written atomically so an interrupted backup never replaces a valid one.
 */
class LocalBackupStore(private val context: Context) {
    fun create(): LocalBackup {
        val createdAt = Instant.now()
        val directory = File(context.filesDir, BACKUP_DIRECTORY).apply { mkdirs() }
        val target = File(directory, "simplifica-rural-${FILE_TIME.format(createdAt)}.json")
        val temporary = File(directory, "${target.name}.part")

        temporary.writeText(buildPayload(createdAt).toString(2), StandardCharsets.UTF_8)
        check(temporary.renameTo(target)) { "Não foi possível finalizar o backup local." }
        retainRecentBackups(directory)
        return LocalBackup(target, createdAt)
    }

    private fun buildPayload(createdAt: Instant): JSONObject {
        val eventsPreferences = context.getSharedPreferences(EVENTS_PREFERENCES, Context.MODE_PRIVATE)
        val contextPreferences = context.getSharedPreferences(CONTEXT_PREFERENCES, Context.MODE_PRIVATE)
        val managementPreferences = context.getSharedPreferences(MANAGEMENT_PREFERENCES, Context.MODE_PRIVATE)
        val cattlePreferences = context.getSharedPreferences(CATTLE_PREFERENCES, Context.MODE_PRIVATE)
        val animalPreferences = context.getSharedPreferences(ANIMAL_PREFERENCES, Context.MODE_PRIVATE)
        val milkSecretaryPreferences = context.getSharedPreferences(MILK_SECRETARY_PREFERENCES, Context.MODE_PRIVATE)
        val events = eventsPreferences.getString(EVENTS_KEY, "[]").orEmpty()

        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("createdAt", createdAt.toString())
            put("events", runCatching { JSONArray(events) }.getOrElse { JSONArray() })
            put("managementRecords", runCatching { JSONArray(managementPreferences.getString("records", "[]")) }.getOrElse { JSONArray() })
            put("cattle", JSONObject().apply {
                put("profiles", runCatching { JSONArray(cattlePreferences.getString("cows", "[]")) }.getOrElse { JSONArray() })
                put("milkRecords", runCatching { JSONArray(cattlePreferences.getString("milk", "[]")) }.getOrElse { JSONArray() })
                put("ingredients", runCatching { JSONArray(cattlePreferences.getString("ingredients", "[]")) }.getOrElse { JSONArray() })
                put("mixes", runCatching { JSONArray(cattlePreferences.getString("mixes", "[]")) }.getOrElse { JSONArray() })
            })
            put("animalRecords", JSONObject().apply {
                put("animals", runCatching { JSONArray(animalPreferences.getString("animals", "[]")) }.getOrElse { JSONArray() })
                put("batches", runCatching { JSONArray(animalPreferences.getString("batches", "[]")) }.getOrElse { JSONArray() })
                put("health", runCatching { JSONArray(animalPreferences.getString("health", "[]")) }.getOrElse { JSONArray() })
                put("tasks", runCatching { JSONArray(animalPreferences.getString("tasks", "[]")) }.getOrElse { JSONArray() })
            })
            put("milkDiary", runCatching { JSONArray(milkSecretaryPreferences.getString("records", "[]")) }.getOrElse { JSONArray() })
            put("activeScope", JSONObject().apply {
                put("organizationId", contextPreferences.getString("organizationId", "local-organization"))
                put("farmId", contextPreferences.getString("farmId", "default-farm"))
                put("unitId", contextPreferences.getString("unitId", null))
            })
        }
    }

    private fun retainRecentBackups(directory: File) {
        directory.listFiles { file -> file.isFile && file.name.startsWith("simplifica-rural-") && file.extension == "json" }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_HOURLY_BACKUPS)
            ?.forEach(File::delete)
    }

    private companion object {
        const val BACKUP_DIRECTORY = "backups"
        const val EVENTS_PREFERENCES = "rural_events"
        const val EVENTS_KEY = "events"
        const val CONTEXT_PREFERENCES = "active_farm_context"
        const val MANAGEMENT_PREFERENCES = "rural_management"
        const val CATTLE_PREFERENCES = "cattle_management"
        const val ANIMAL_PREFERENCES = "animal_records"
        const val MILK_SECRETARY_PREFERENCES = "milk_secretary"
        const val SCHEMA_VERSION = 1
        const val MAX_HOURLY_BACKUPS = 3 // The newest copies replace the oldest one.
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
    }
}
