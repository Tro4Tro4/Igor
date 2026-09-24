package com.igor.fridge.data

import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.FoodItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * DAO in memoria: le regole di scrittura stanno nel repository, non nel database.
 *
 * Lo stato vive in un MutableStateFlow e non in una lista: `observeAll()` deve riemettere
 * dopo ogni scrittura, altrimenti un test che osserva l'inventario dopo una rimozione
 * resterebbe in attesa per sempre.
 */
class FakeFoodItemDao : FoodItemDao {
    private val state = MutableStateFlow<List<FoodItem>>(emptyList())

    val items: List<FoodItem> get() = state.value

    override fun observeAll(): Flow<List<FoodItem>> =
        state.map { list -> list.filter { it.removedAt == null } }

    override suspend fun findByUuid(uuid: String): FoodItem? =
        items.firstOrNull { it.uuid == uuid }

    override suspend fun findLastByBarcode(barcode: String): FoodItem? =
        items.filter { it.barcode == barcode }.maxByOrNull { it.updatedAt }

    override suspend fun findLastByName(name: String): FoodItem? =
        items.filter { it.name.equals(name, ignoreCase = true) }.maxByOrNull { it.updatedAt }

    override suspend fun findExpiringOnOrBefore(limitDate: LocalDate): List<FoodItem> =
        items.filter { it.removedAt == null && it.expiryDate?.let { d -> !d.isAfter(limitDate) } == true }

    override suspend fun upsert(item: FoodItem) {
        state.update { list -> list.filterNot { it.uuid == item.uuid } + item }
    }

    override suspend fun delete(item: FoodItem) {
        state.update { list -> list.filterNot { it.uuid == item.uuid } }
    }
}
