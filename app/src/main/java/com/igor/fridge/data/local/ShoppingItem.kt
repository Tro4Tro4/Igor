package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Una voce della lista della spesa.
 *
 * [quantity] e' quanto si vuole comprare, [purchasedQuantity] quanto e' stato preso
 * davvero: null finche' l'utente non lo indica, e in quel caso vale [quantity]. Se e'
 * minore, mettendo in frigo la differenza resta in lista.
 *
 * [photoPath] e' il nome di un file nella cartella delle foto dell'app, non un percorso
 * assoluto: la cartella cambia fra installazioni e dopo un ripristino da backup.
 *
 * [unitPriceCents] e' il prezzo in centesimi per unita' di [unit] (al pezzo, al kg...): i
 * centesimi interi non accumulano gli errori di arrotondamento di un Double. [store] e' il
 * negozio in cui comprarlo; null vuol dire "dovunque".
 */
@Entity(tableName = "shopping_items", indices = [Index("nameKey")])
data class ShoppingItem(
    @PrimaryKey
    val uuid: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: QuantityUnit = QuantityUnit.PZ,
    val isChecked: Boolean = false,
    val createdAt: LocalDate = LocalDate.now(),
    val updatedAt: Instant = Instant.now(),
    val category: FoodCategory = FoodCategory.ALTRO,
    val brand: String? = null,
    val notes: String? = null,
    val photoPath: String? = null,
    val purchasedQuantity: Double? = null,
    val unitPriceCents: Long? = null,
    val store: String? = null,
) {
    /** Il nome in forma di ricerca, ricalcolato a ogni `copy` (vedi [FoodItem.nameKey]). */
    var nameKey: String = nameKeyOf(name)
}

/** Le quantita' sono Double: 0.3 - 0.1 - 0.2 non fa esattamente zero. */
private const val QUANTITY_EPSILON = 1e-9

/**
 * Quanto resta da comprare dopo l'acquisto, oppure null se non resta niente. E' fuori
 * dall'entita' perche' Room non provi a farne una colonna.
 */
val ShoppingItem.remainingAfterPurchase: Double?
    get() {
        val purchased = purchasedQuantity ?: return null
        val remaining = quantity - purchased
        return remaining.takeIf { it > QUANTITY_EPSILON }
    }
