package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodItemDao {

    /** Ordina per scadenza crescente; gli articoli senza data finiscono in fondo. */
    @Query(
        """
        SELECT * FROM food_items
        ORDER BY (expiryDate IS NULL), expiryDate ASC, name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun findById(id: Long): FoodItem?

    /** Usato dopo la scansione per riconoscere un prodotto gia' inserito in passato. */
    @Query("SELECT * FROM food_items WHERE barcode = :barcode ORDER BY addedAt DESC LIMIT 1")
    suspend fun findLastByBarcode(barcode: String): FoodItem?

    @Query(
        """
        SELECT * FROM food_items
        WHERE expiryDate IS NOT NULL AND expiryDate <= :limitDate
        ORDER BY expiryDate ASC
        """
    )
    suspend fun findExpiringOnOrBefore(limitDate: LocalDate): List<FoodItem>

    @Upsert
    suspend fun upsert(item: FoodItem): Long

    @Delete
    suspend fun delete(item: FoodItem)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
