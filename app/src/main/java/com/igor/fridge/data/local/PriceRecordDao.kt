package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PriceRecordDao {

    /** Dal piu' recente; a parita' di giorno, l'ultimo registrato prima. */
    @Query("SELECT * FROM price_records ORDER BY purchasedOn DESC, createdAt DESC")
    fun observeAll(): Flow<List<PriceRecord>>

    @Query(
        """
        SELECT * FROM price_records WHERE productKey = :productKey
        ORDER BY purchasedOn DESC, createdAt DESC
        """
    )
    fun observeByProduct(productKey: String): Flow<List<PriceRecord>>

    @Query(
        """
        SELECT * FROM price_records WHERE productKey = :productKey
        ORDER BY purchasedOn DESC, createdAt DESC LIMIT 1
        """
    )
    suspend fun findLatest(productKey: String): PriceRecord?

    @Query("SELECT * FROM price_records WHERE purchasedOn = :date")
    suspend fun onDate(date: LocalDate): List<PriceRecord>

    @Insert
    suspend fun insertAll(records: List<PriceRecord>)

    /** Un prezzo letto male si toglie: falserebbe medie e andamento. */
    @Delete
    suspend fun delete(record: PriceRecord)
}
