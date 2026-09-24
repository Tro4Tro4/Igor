package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {

    @Query(
        """
        SELECT * FROM shopping_items
        ORDER BY isChecked ASC, createdAt DESC, name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ShoppingItem?

    @Upsert
    suspend fun upsert(item: ShoppingItem)

    @Delete
    suspend fun delete(item: ShoppingItem)
}
