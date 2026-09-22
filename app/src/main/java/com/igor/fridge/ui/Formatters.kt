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
fun formatQuantity(quantity: Double, unit: QuantityUnit): String {
    val number = if (quantity % 1.0 == 0.0) {
        quantity.toLong().toString()
    } else {
        String.format(Locale.ITALY, "%.2f", quantity).trimEnd('0').trimEnd(',')
    }
    return "$number ${unit.label()}"
}

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
    FoodCategory.LATTICINI -> "Latticini"
    FoodCategory.CARNE -> "Carne"
    FoodCategory.PESCE -> "Pesce"
    FoodCategory.FRUTTA -> "Frutta"
    FoodCategory.VERDURA -> "Verdura"
    FoodCategory.BEVANDE -> "Bevande"
    FoodCategory.CONDIMENTI -> "Condimenti"
    FoodCategory.SURGELATI -> "Surgelati"
    FoodCategory.DISPENSA -> "Dispensa"
    FoodCategory.ALTRO -> "Altro"
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
