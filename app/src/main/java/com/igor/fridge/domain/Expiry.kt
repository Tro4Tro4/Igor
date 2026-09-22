package com.igor.fridge.domain

import com.igor.fridge.data.local.FoodItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Stato di freschezza di un alimento rispetto a una data di riferimento. */
enum class ExpiryStatus {
    /** La data di scadenza e' gia' passata. */
    SCADUTO,

    /** Scade oggi o entro la soglia di preavviso configurata. */
    IN_SCADENZA,

    /** Scadenza oltre la soglia di preavviso. */
    FRESCO,

    /** Prodotto senza data di scadenza: non genera avvisi. */
    SENZA_DATA,
}

/**
 * Giorni che mancano alla scadenza: 0 significa "scade oggi", i valori negativi
 * indicano i giorni trascorsi dalla scadenza. `null` se l'articolo non ha data.
 */
fun FoodItem.daysUntilExpiry(today: LocalDate): Long? =
    expiryDate?.let { ChronoUnit.DAYS.between(today, it) }

/**
 * @param warningDays giorni di preavviso: un prodotto che scade entro questo numero
 *   di giorni (estremo incluso) e' considerato [ExpiryStatus.IN_SCADENZA].
 */
fun FoodItem.expiryStatus(today: LocalDate, warningDays: Int): ExpiryStatus {
    val days = daysUntilExpiry(today) ?: return ExpiryStatus.SENZA_DATA
    return when {
        days < 0 -> ExpiryStatus.SCADUTO
        days <= warningDays -> ExpiryStatus.IN_SCADENZA
        else -> ExpiryStatus.FRESCO
    }
}
