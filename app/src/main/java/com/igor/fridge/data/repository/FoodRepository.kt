package com.igor.fridge.data.repository

import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.FoodItemDao
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Unico punto di accesso all'inventario alimentare. */
class FoodRepository(private val dao: FoodItemDao) {

    fun observeAll(): Flow<List<FoodItem>> = dao.observeAll()

    suspend fun findById(id: Long): FoodItem? = dao.findById(id)

    suspend fun findLastByBarcode(barcode: String): FoodItem? = dao.findLastByBarcode(barcode)

    /** @return l'id dell'articolo salvato (nuovo o aggiornato). */
    suspend fun save(item: FoodItem): Long = dao.upsert(item)

    suspend fun delete(item: FoodItem) = dao.delete(item)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /**
     * Articoli scaduti oppure in scadenza entro [withinDays] giorni a partire da [today].
     */
    suspend fun findExpiring(today: LocalDate, withinDays: Int): List<FoodItem> =
        dao.findExpiringOnOrBefore(today.plusDays(withinDays.toLong()))
}
