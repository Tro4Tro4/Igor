package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
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
     * della spesa o viene dettato col solo nome. [nameKey] viene da [nameKeyOf].
     */
    @Query(
        """
        SELECT * FROM food_items
        WHERE nameKey = :nameKey
        ORDER BY updatedAt DESC LIMIT 1
        """
    )
    suspend fun findLastByNameKey(nameKey: String): FoodItem?

    @Query(
        """
        SELECT * FROM food_items
        WHERE removedAt IS NULL AND expiryDate IS NOT NULL AND expiryDate <= :limitDate
        ORDER BY expiryDate ASC
        """
    )
    suspend fun findExpiringOnOrBefore(limitDate: LocalDate): List<FoodItem>

    /**
     * Non esiste un @Delete: un alimento non viene mai cancellato fisicamente, esce
     * dall'inventario valorizzando removedAt. Aggiungerlo qui basterebbe a far rientrare
     * dalla finestra la cancellazione fisica che la cancellazione logica esclude.
     */
    @Upsert
    suspend fun upsert(item: FoodItem)

    /**
     * Uscita e rientro toccano solo le colonne dello stato: riscrivere la riga intera da
     * una copia vecchia (quella mostrata a schermo) cancellerebbe una modifica fatta nel
     * frattempo, per esempio la scadenza corretta subito dopo "Metti in frigo".
     */
    @Query(
        """
        UPDATE food_items SET removedAt = :at, removalReason = :reason, updatedAt = :at
        WHERE uuid = :uuid
        """
    )
    suspend fun markRemoved(uuid: String, reason: RemovalReason, at: Instant)

    @Query(
        """
        UPDATE food_items SET removedAt = NULL, removalReason = NULL, updatedAt = :at
        WHERE uuid = :uuid
        """
    )
    suspend fun markRestored(uuid: String, at: Instant)
}
