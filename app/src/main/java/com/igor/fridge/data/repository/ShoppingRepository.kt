package com.igor.fridge.data.repository

import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.ShoppingItemDao
import kotlinx.coroutines.flow.Flow

/** Gestisce la lista della spesa. */
class ShoppingRepository(private val dao: ShoppingItemDao) {

    fun observeAll(): Flow<List<ShoppingItem>> = dao.observeAll()

    /**
     * Aggiunge una voce; se esiste gia' un articolo con lo stesso nome lo lascia invariato
     * (evita duplicati quando si aggiungono in blocco i prodotti in scadenza).
     *
     * @return true se e' stata creata una nuova voce.
     */
    suspend fun addIfAbsent(
        name: String,
        quantity: Double = 1.0,
        unit: QuantityUnit = QuantityUnit.PZ,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (dao.findByName(trimmed) != null) return false
        dao.upsert(ShoppingItem(name = trimmed, quantity = quantity, unit = unit))
        return true
    }

    suspend fun setChecked(item: ShoppingItem, checked: Boolean) {
        dao.upsert(item.copy(isChecked = checked))
    }

    suspend fun delete(item: ShoppingItem) = dao.delete(item)

    suspend fun deleteChecked() = dao.deleteChecked()
}
