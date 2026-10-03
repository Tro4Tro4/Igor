package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import java.time.Instant

/**
 * Il codice a barre di un prodotto dello storico dei prezzi. Uno scontrino non lo stampa:
 * lo si associa una volta (scansione o inventario) e vale per tutti gli acquisti dello
 * stesso prodotto. E' cio' che permette di confrontarsi con i prezzi della comunita'.
 */
@Entity(tableName = "product_codes")
data class ProductCode(
    @PrimaryKey
    val productKey: String,
    val barcode: String,
    val updatedAt: Instant = Instant.now(),
)

@Dao
interface ProductCodeDao {

    @Query("SELECT * FROM product_codes WHERE productKey = :productKey")
    suspend fun find(productKey: String): ProductCode?

    @Query("SELECT * FROM product_codes")
    suspend fun all(): List<ProductCode>

    @Upsert
    suspend fun upsert(code: ProductCode)

    @Query("DELETE FROM product_codes WHERE productKey = :productKey")
    suspend fun delete(productKey: String)
}
