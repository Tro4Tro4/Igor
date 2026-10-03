package com.igor.fridge.ui

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.StorageLocation
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
 * La quantita' in un campo di testo: fino a 3 decimali, senza zeri inutili e con la
 * virgola. A differenza di [formatNumber] non arrotonda i grammi: 0,125 kg resta 0,125,
 * e un prezzo al kg calcolato poi su quella quantita' resta giusto.
 */
fun formatQuantityInput(quantity: Double): String =
    if (quantity % 1.0 == 0.0) {
        quantity.toLong().toString()
    } else {
        String.format(Locale.ITALY, "%.3f", quantity).trimEnd('0').trimEnd(',')
    }

/**
 * Legge una quantita' scritta dall'utente, con la virgola o con il punto.
 * @return null se il testo non e' un numero maggiore di zero.
 */
fun parseQuantity(text: String): Double? =
    text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

/**
 * Legge un prezzo scritto dall'utente ("1,29", "1.29 €", "2") in centesimi.
 * @return null se il testo non e' un importo maggiore o uguale a zero.
 */
fun parsePriceCents(text: String): Long? {
    val value = text.replace("€", "").replace(',', '.').trim().toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < 0.0) return null
    return Math.round(value * 100)
}

/** 1290 diventa "12,90 €". */
fun formatEuro(cents: Long): String =
    String.format(Locale.ITALY, "%d,%02d €", cents / 100, kotlin.math.abs(cents % 100))

/** 129 al kg diventa "1,29 €/kg". */
fun formatUnitPrice(cents: Long, unit: QuantityUnit): String = "${formatEuro(cents)}/${unit.label()}"

/** "ottobre 2026". */
fun YearMonth.formatMonth(): String =
    "${month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN)} $year"

/** +5,2 % / -3 %: la variazione di un prezzo, col segno sempre esplicito. */
fun formatChange(percent: Double): String {
    val rounded = Math.round(percent * 10) / 10.0
    val sign = if (rounded > 0) "+" else ""
    val number = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else String.format(Locale.ITALY, "%.1f", rounded)
    return "$sign$number%"
}

/** Il prezzo nel campo di testo, senza simbolo: 129 diventa "1,29". */
fun formatPriceInput(cents: Long): String =
    String.format(Locale.ITALY, "%d,%02d", cents / 100, cents % 100)

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
