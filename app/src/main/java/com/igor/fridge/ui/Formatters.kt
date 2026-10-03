package com.igor.fridge.ui

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.StorageLocation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY)

fun LocalDate.formatShort(): String = format(dateFormatter)

/** Mostra "1" invece di "1.0" e usa la virgola decimale. */
fun formatQuantity(quantity: Double, unit: QuantityUnit): String =
    "${formatNumber(quantity)} ${unit.label()}"

/** Il numero di [formatQuantity], senza unita'. */
fun formatNumber(quantity: Double): String =
    if (quantity % 1.0 == 0.0) {
        quantity.toLong().toString()
    } else {
        String.format(Locale.ITALY, "%.2f", quantity).trimEnd('0').trimEnd(',')
    }

/**
 * Legge una quantita' scritta dall'utente, con la virgola o con il punto.
 * @return null se il testo non e' un numero maggiore di zero.
 */
fun parseQuantity(text: String): Double? =
    text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

fun QuantityUnit.label(): String = when (this) {
    QuantityUnit.PZ -> "pz"
    QuantityUnit.G -> "g"
    QuantityUnit.KG -> "kg"
    QuantityUnit.ML -> "ml"
    QuantityUnit.L -> "l"
    QuantityUnit.CONF -> "conf."
}

fun StorageLocation.label(): String = when (this) {
    StorageLocation.FRIGO -> "Frigo"
    StorageLocation.FREEZER -> "Freezer"
    StorageLocation.DISPENSA -> "Dispensa"
}

fun FoodCategory.label(): String = when (this) {
    FoodCategory.FRUTTA -> "Frutta"
    FoodCategory.VERDURA -> "Verdura"
    FoodCategory.PANE -> "Pane e forno"
    FoodCategory.CARNE -> "Carne"
    FoodCategory.PESCE -> "Pesce"
    FoodCategory.LATTICINI -> "Latticini e uova"
    FoodCategory.SURGELATI -> "Surgelati"
    FoodCategory.DISPENSA -> "Dispensa"
    FoodCategory.CONDIMENTI -> "Condimenti"
    FoodCategory.BEVANDE -> "Bevande"
    FoodCategory.CASA -> "Casa e pulizia"
    FoodCategory.IGIENE -> "Igiene personale"
    FoodCategory.ALTRO -> "Altro"
}

/**
 * Icona della categoria, come emoji: a colori e riconoscibile anche in piccolo. Sono tutte
 * emoji presenti da Android 8 (minSdk 26), cosi' nessuna diventa un quadratino vuoto.
 */
fun FoodCategory.icon(): String = when (this) {
    FoodCategory.FRUTTA -> "\uD83C\uDF4E" // mela rossa
    FoodCategory.VERDURA -> "\uD83E\uDD66" // broccolo
    FoodCategory.PANE -> "\uD83C\uDF5E" // pane
    FoodCategory.CARNE -> "\uD83E\uDD69" // taglio di carne
    FoodCategory.PESCE -> "\uD83D\uDC1F" // pesce
    FoodCategory.LATTICINI -> "\uD83E\uDD5B" // bicchiere di latte
    FoodCategory.SURGELATI -> "\u2744\uFE0F" // fiocco di neve
    FoodCategory.DISPENSA -> "\uD83E\uDD6B" // cibo in scatola
    FoodCategory.CONDIMENTI -> "\uD83C\uDF36\uFE0F" // peperoncino
    FoodCategory.BEVANDE -> "\uD83E\uDD64" // bicchiere con cannuccia
    FoodCategory.CASA -> "\uD83C\uDFE0" // casa
    FoodCategory.IGIENE -> "\uD83D\uDEC1" // vasca da bagno
    FoodCategory.ALTRO -> "\uD83D\uDED2" // carrello
}

/**
 * Testo dello stato di scadenza: "Scaduto da 2 giorni", "Scade oggi", "3 giorni".
 * [days] e' il risultato di `daysUntilExpiry`.
 */
fun expiryLabel(days: Long?): String = when {
    days == null -> "Senza scadenza"
    days < -1 -> "Scaduto da ${-days} giorni"
    days == -1L -> "Scaduto ieri"
    days == 0L -> "Scade oggi"
    days == 1L -> "Scade domani"
    else -> "Scade fra $days giorni"
}
