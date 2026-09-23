package com.igor.fridge.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Millisecondi che mancano alla mezzanotte successiva. Vive in un file senza dipendenze
 * Android per poter essere coperto dai test JVM.
 */
fun millisUntilNextMidnight(now: LocalDateTime): Long =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()

/**
 * Data corrente come flusso: emette subito, poi una volta a ogni mezzanotte.
 *
 * Serve perche' `LocalDate.now()` letto una sola volta congela gli stati di scadenza:
 * con l'app aperta oltre la mezzanotte, "scade oggi" continuerebbe a riferirsi a ieri.
 */
fun currentDateFlow(clock: () -> LocalDateTime = LocalDateTime::now): Flow<LocalDate> = flow {
    while (true) {
        val now = clock()
        emit(now.toLocalDate())
        delay(millisUntilNextMidnight(now))
    }
}
