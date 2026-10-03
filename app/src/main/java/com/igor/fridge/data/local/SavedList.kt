package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Una lista della spesa salvata con un nome ("Spesa settimanale", "Grigliata"), da
 * ricaricare quando serve.
 *
 * E' un modello, non una seconda lista attiva: la lista della spesa resta una sola, ed
 * e' quella con cui inventario e "Metti in frigo" dialogano. Caricare una lista salvata
 * ne copia le voci in quella attiva; le voci salvate non cambiano mai stato.
 */
@Entity(tableName = "saved_lists", indices = [Index("nameKey")])
data class SavedList(
    @PrimaryKey
    val uuid: String,
    val name: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    /** Il nome in forma di ricerca, ricalcolato a ogni `copy` (vedi [FoodItem.nameKey]). */
    var nameKey: String = nameKeyOf(name)
}

/**
 * Una voce di una lista salvata: i dati di [ShoppingItem] che descrivono *cosa* comprare,
 * senza quelli che descrivono lo stato di un acquisto (spunta, quantita' presa).
 *
 * La cancellazione delle voci insieme alla lista e' fatta da [SavedListDao.delete] in una
 * transazione, senza chiave esterna: cosi' non dipende dal PRAGMA foreign_keys.
 */
@Entity(
    tableName = "saved_list_items",
    indices = [Index("listUuid")],
)
data class SavedListItem(
    @PrimaryKey
    val uuid: String,
    val listUuid: String,
    val position: Int,
    val name: String,
    val quantity: Double = 1.0,
    val unit: QuantityUnit = QuantityUnit.PZ,
    val category: FoodCategory = FoodCategory.ALTRO,
    val brand: String? = null,
    val notes: String? = null,
    val photoPath: String? = null,
    val unitPriceCents: Long? = null,
    val store: String? = null,
)

/** Riga dell'elenco delle liste salvate. */
data class SavedListSummary(
    val uuid: String,
    val name: String,
    val updatedAt: Instant,
    val itemCount: Int,
)
