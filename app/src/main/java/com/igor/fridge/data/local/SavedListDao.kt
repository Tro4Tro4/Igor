package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Classe astratta e non interfaccia: le operazioni su piu' tabelle sono metodi con un
 * corpo annotati @Transaction, e Room li supporta senza riserve solo nelle classi astratte.
 */
@Dao
abstract class SavedListDao {

    @Query(
        """
        SELECT l.uuid AS uuid, l.name AS name, l.updatedAt AS updatedAt,
            (SELECT COUNT(*) FROM saved_list_items i WHERE i.listUuid = l.uuid) AS itemCount
        FROM saved_lists l
        ORDER BY l.name COLLATE NOCASE ASC
        """
    )
    abstract fun observeSummaries(): Flow<List<SavedListSummary>>

    @Query("SELECT * FROM saved_lists WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findByName(name: String): SavedList?

    @Query("SELECT * FROM saved_list_items WHERE listUuid = :listUuid ORDER BY position ASC")
    abstract suspend fun itemsOf(listUuid: String): List<SavedListItem>

    @Query("SELECT photoPath FROM saved_list_items WHERE photoPath IS NOT NULL")
    abstract suspend fun photoPaths(): List<String>

    @Upsert
    abstract suspend fun upsertList(list: SavedList)

    @Insert
    abstract suspend fun insertItems(items: List<SavedListItem>)

    @Query("DELETE FROM saved_list_items WHERE listUuid = :listUuid")
    abstract suspend fun deleteItemsOf(listUuid: String)

    @Query("DELETE FROM saved_lists WHERE uuid = :uuid")
    abstract suspend fun deleteListRow(uuid: String)

    /** Sovrascrive la lista e tutte le sue voci: o tutto o niente. */
    @Transaction
    open suspend fun replace(list: SavedList, items: List<SavedListItem>) {
        upsertList(list)
        deleteItemsOf(list.uuid)
        insertItems(items)
    }

    @Transaction
    open suspend fun delete(listUuid: String) {
        deleteItemsOf(listUuid)
        deleteListRow(listUuid)
    }
}
