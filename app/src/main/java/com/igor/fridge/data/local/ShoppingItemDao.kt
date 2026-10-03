package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ShoppingItemDao {

    @Query(
        """
        SELECT * FROM shopping_items
        ORDER BY isChecked ASC, createdAt DESC, name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<ShoppingItem>>

    /** Le voci nel carrello, lette dentro la transazione di "Metti in frigo". */
    @Query("SELECT * FROM shopping_items WHERE isChecked = 1 ORDER BY createdAt DESC, name COLLATE NOCASE ASC")
    suspend fun checkedItems(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): ShoppingItem?

    /**
     * La voce con questo nome ([nameKeyOf]). Se per qualche motivo ce ne sono due, vince
     * quella ancora da comprare e poi la piu' recente: sempre la stessa, non una a caso.
     */
    @Query(
        """
        SELECT * FROM shopping_items WHERE nameKey = :nameKey
        ORDER BY isChecked ASC, updatedAt DESC LIMIT 1
        """
    )
    suspend fun findByNameKey(nameKey: String): ShoppingItem?

    @Query("SELECT photoPath FROM shopping_items WHERE photoPath IS NOT NULL")
    suspend fun photoPaths(): List<String>

    @Upsert
    suspend fun upsert(item: ShoppingItem)

    @Delete
    suspend fun delete(item: ShoppingItem)

    /**
     * Solo la spunta: due tocchi rapidi sulla stessa voce partono dalla stessa copia a
     * schermo, e riscrivere la riga intera ne farebbe vincere uno a caso. Togliere la
     * spunta dimentica la quantita' presa.
     */
    @Query(
        """
        UPDATE shopping_items SET isChecked = :checked, updatedAt = :at,
            purchasedQuantity = CASE WHEN :checked THEN purchasedQuantity ELSE NULL END
        WHERE uuid = :uuid
        """
    )
    suspend fun setChecked(uuid: String, checked: Boolean, at: Instant)
}
