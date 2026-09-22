package com.igor.fridge.notification

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Minuti che mancano alla prossima occorrenza di [hourOfDay]; se quell'ora di oggi
 * e' gia' passata punta al giorno successivo.
 *
 * Vive in un file senza dipendenze Android per poter essere coperto dai test JVM.
 */
fun minutesUntilNextDailyRun(now: LocalDateTime, hourOfDay: Int): Long {
    val target = now.toLocalDate().atTime(LocalTime.of(hourOfDay.coerceIn(0, 23), 0))
    val next = if (target.isAfter(now)) target else target.plusDays(1)
    return Duration.between(now, next).toMinutes()
}
