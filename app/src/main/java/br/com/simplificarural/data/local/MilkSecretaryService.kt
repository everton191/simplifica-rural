package br.com.simplificarural.data.local

import android.content.Context
import br.com.simplificarural.domain.property.FarmScope
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class MilkShift { MANHA, TARDE, NOITE, NAO_INFORMADO;
    companion object { fun automatic(hour: Int): MilkShift = when { hour < 12 -> MANHA; hour < 17 -> TARDE; else -> NOITE } }
}
data class MilkShiftRecord(val id: String, val scope: FarmScope, val liters: BigDecimal, val shift: MilkShift, val recordedAt: LocalDateTime)

/** Keeps the diary-level detail that a production total alone cannot preserve. */
class MilkSecretaryService(context: Context) {
    private val prefs = context.getSharedPreferences("milk_secretary", Context.MODE_PRIVATE)
    fun record(scope: FarmScope, liters: BigDecimal, shift: MilkShift, at: LocalDateTime = LocalDateTime.now()): MilkShiftRecord {
        val record = MilkShiftRecord(UUID.randomUUID().toString(), scope, liters, if (shift == MilkShift.NAO_INFORMADO) MilkShift.automatic(at.hour) else shift, at)
        val records = JSONArray(prefs.getString("records", "[]")); records.put(record.json()); prefs.edit().putString("records", records.toString()).apply(); return record
    }
    fun dayTotal(scope: FarmScope, day: LocalDate = LocalDate.now()): BigDecimal = all(scope).filter { it.recordedAt.toLocalDate() == day }.fold(BigDecimal.ZERO) { total, item -> total + item.liters }
    fun records(scope: FarmScope): List<MilkShiftRecord> = all(scope).sortedByDescending { it.recordedAt }
    fun closingDay(scope: FarmScope): DayOfWeek = prefs.getString("closing_${scope.organizationId}_${scope.farmId}_${scope.unitId.orEmpty()}", DayOfWeek.THURSDAY.name)?.let(DayOfWeek::valueOf) ?: DayOfWeek.THURSDAY
    fun setClosingDay(scope: FarmScope, day: DayOfWeek) { prefs.edit().putString("closing_${scope.organizationId}_${scope.farmId}_${scope.unitId.orEmpty()}", day.name).apply() }
    fun missingShift(scope: FarmScope, now: LocalDateTime = LocalDateTime.now()): MilkShift? {
        val shifts = all(scope).filter { it.recordedAt.toLocalDate() == now.toLocalDate() }.map { it.shift }.toSet()
        return when { now.hour >= 12 && MilkShift.MANHA !in shifts -> MilkShift.MANHA; now.hour >= 18 && MilkShift.TARDE !in shifts -> MilkShift.TARDE; else -> null }
    }
    fun weeklyTotal(scope: FarmScope, closingDay: DayOfWeek = DayOfWeek.THURSDAY, today: LocalDate = LocalDate.now()): BigDecimal {
        val start = today.minusDays(((today.dayOfWeek.value - closingDay.value + 7) % 7).toLong())
        return all(scope).filter { !it.recordedAt.toLocalDate().isBefore(start) && !it.recordedAt.toLocalDate().isAfter(today) }.fold(BigDecimal.ZERO) { total, item -> total + item.liters }
    }
    private fun all(scope: FarmScope): List<MilkShiftRecord> = JSONArray(prefs.getString("records", "[]")).let { a -> (0 until a.length()).map(a::getJSONObject).map(::fromJson).filter { it.scope == scope } }
    private fun MilkShiftRecord.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("liters", liters.toPlainString()); put("shift", shift.name); put("recordedAt", recordedAt.toString()) }
    private fun fromJson(json: JSONObject) = MilkShiftRecord(json.getString("id"), FarmScope(json.getString("organizationId"), json.getString("farmId"), json.optString("unitId").ifBlank { null }), json.getString("liters").toBigDecimal(), MilkShift.valueOf(json.getString("shift")), LocalDateTime.parse(json.getString("recordedAt")))
}
