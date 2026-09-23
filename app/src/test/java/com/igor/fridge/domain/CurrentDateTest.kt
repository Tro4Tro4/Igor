package com.igor.fridge.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CurrentDateTest {

    @Test
    fun `a mezzanotte in punto manca un giorno intero`() {
        val now = LocalDateTime.of(2026, 4, 1, 0, 0)

        assertEquals(24 * 60 * 60 * 1000L, millisUntilNextMidnight(now))
    }

    @Test
    fun `un minuto prima di mezzanotte manca un minuto`() {
        val now = LocalDateTime.of(2026, 4, 1, 23, 59)

        assertEquals(60 * 1000L, millisUntilNextMidnight(now))
    }

    @Test
    fun `l'attesa e' sempre positiva`() {
        val now = LocalDateTime.of(2026, 4, 1, 23, 59, 59, 999_000_000)

        assertEquals(1L, millisUntilNextMidnight(now))
    }

    @Test
    fun `il flusso emette subito la data corrente`() = runTest {
        val flow = currentDateFlow { LocalDateTime.of(2026, 4, 1, 10, 0) }

        assertEquals(LocalDate.of(2026, 4, 1), flow.first())
    }
}
