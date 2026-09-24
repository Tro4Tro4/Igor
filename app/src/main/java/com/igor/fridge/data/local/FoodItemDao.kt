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
        WHERE removedAt IS NULL
        ORDER BY (expiryDate IS NULL), expiryDate ASC, name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): FoodItem?

    /** Usato dopo la scansione per riconoscere un prodotto gia' inserito in passato. */
    @Query(
        """
        SELECT * FROM food_items
        WHERE barcode = :barcode
        ORDER BY updatedAt DESC LIMIT 1
        """
    )
    suspend fun findLastByBarcode(barcode: String): FoodItem?

    /**
     * Ultimo articolo con questo nome, **compresi quelli usciti dal frigo**: serve a
     * ereditare categoria, unita' e posizione quando un prodotto rientra dalla lista
     * della spesa o viene dettato col solo nome.
     */
    @Query(
        """
        SELECT * FROM food_items
        WHERE name = :name COLLATE NOCASE
        ORDER BY updatedAt DESC LIMIT 1
        """
    )
    suspend fun findLastByName(name: String): FoodItem?

    @Query(
        """
        SELECT * FROM food_items
        WHERE removedAt IS NULL AND expiryDate IS NOT NULL AND expiryDate <= :limitDate
        ORDER BY expiryDate ASC
        """
    )
    suspend fun findExpiringOnOrBefore(limitDate: LocalDate): List<FoodItem>

    @Upsert
    suspend fun upsert(item: FoodItem)

    @Delete
    suspend fun delete(item: FoodItem)
}
