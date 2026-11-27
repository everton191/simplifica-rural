package br.com.simplificarural.ai

import br.com.simplificarural.data.local.milkCycle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class MilkCycleTest {
    @Test fun closingDayIsIncludedAndTheFollowingDayStartsANewCycle() {
        val thursday = LocalDate.of(2026, 11, 26)
        assertEquals(thursday.minusDays(6)..thursday, milkCycle(thursday, DayOfWeek.THURSDAY))
        assertEquals(thursday.plusDays(1)..thursday.plusWeeks(1), milkCycle(thursday.plusDays(1), DayOfWeek.THURSDAY))
    }
}
