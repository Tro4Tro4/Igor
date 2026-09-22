package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Un alimento presente in casa.
 *
 * [expiryDate] e' nullable perche' non tutti i prodotti hanno (o riportano) una scadenza:
 * gli articoli senza data restano in inventario ma non generano notifiche.
 */
@Entity(
    tableName = "food_items",
    indices = [Index("barcode"), Index("expiryDate")],
)
data class FoodItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val barcode: String? = null,
    val category: FoodCategory = FoodCategory.ALTRO,
    val location: StorageLocation = StorageLocation.FRIGO,
    val quantity: Double = 1.0,
    val unit: QuantityUnit = QuantityUnit.PZ,
    val expiryDate: LocalDate? = null,
    val addedAt: LocalDate = LocalDate.now(),
    val notes: String? = null,
)
