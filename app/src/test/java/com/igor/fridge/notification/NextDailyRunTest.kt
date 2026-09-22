package com.igor.fridge.notification

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class NextDailyRunTest {

    @Test
    fun `punta all ora di oggi se non e ancora passata`() {
        val now = LocalDateTime.of(2026, 3, 10, 7, 30)
        assertEquals(90L, minutesUntilNextDailyRun(now, hourOfDay = 9))
    }

    @Test
    fun `punta a domani se l ora e gia passata`() {
        val now = LocalDateTime.of(2026, 3, 10, 9, 0)
        assertEquals(24 * 60L, minutesUntilNextDailyRun(now, hourOfDay = 9))
    }

    @Test
    fun `ora fuori intervallo viene riportata nei limiti`() {
        val now = LocalDateTime.of(2026, 3, 10, 0, 0)
        assertEquals(23 * 60L, minutesUntilNextDailyRun(now, hourOfDay = 30))
    }
}
