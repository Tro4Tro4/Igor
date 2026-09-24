package com.igor.fridge.data

import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.ShoppingItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * DAO in memoria per la lista della spesa: la logica anti-duplicati sta nel repository,
 * non nel database.
 *
 * Come per FakeFoodItemDao lo stato vive in un MutableStateFlow, cosi' `observeAll()`
 * riemette dopo ogni scrittura: un test che osservi la lista dopo un `addIfAbsent`
 * resterebbe altrimenti appeso a un flusso emesso una volta sola.
 *
 * L'ordinamento ripete quello della query vera.
 */
class FakeShoppingItemDao : ShoppingItemDao {
    private val state = MutableStateFlow<List<ShoppingItem>>(emptyList())

    /** Contenuto grezzo, in ordine di scrittura: serve a ispezionare lo stato nei test. */
    val items: List<ShoppingItem> get() = state.value

    override fun observeAll(): Flow<List<ShoppingItem>> =
        state.map { list -> list.sortedWith(SHOPPING_ORDER) }

    override suspend fun findByName(name: String): ShoppingItem? =
        items.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun upsert(item: ShoppingItem) {
        state.update { list -> list.filterNot { it.uuid == item.uuid } + item }
    }

    override suspend fun delete(item: ShoppingItem) {
        state.update { list -> list.filterNot { it.uuid == item.uuid } }
    }

    private companion object {
        /** ORDER BY isChecked ASC, createdAt DESC, name COLLATE NOCASE ASC */
        val SHOPPING_ORDER: Comparator<ShoppingItem> = compareBy<ShoppingItem> { it.isChecked }
            .thenByDescending { it.createdAt }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
}
