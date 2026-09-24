package com.igor.fridge.data.repository

import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.ShoppingItemDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/** Gestisce la lista della spesa. */
class ShoppingRepository(
    private val dao: ShoppingItemDao,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeAll(): Flow<List<ShoppingItem>> = dao.observeAll()

    /**
     * Mette il prodotto fra le cose da comprare.
     *
     * Se esiste gia' una voce con lo stesso nome ma e' spuntata, viene riportata da
     * comprare invece di essere ignorata: altrimenti un prodotto consumato una seconda
     * volta sparirebbe dall'inventario senza ricomparire in lista.
     *
     * @return true se la lista e' cambiata.
     */
    suspend fun addIfAbsent(
        name: String,
        quantity: Double = 1.0,
        unit: QuantityUnit = QuantityUnit.PZ,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false

        val existing = dao.findByName(trimmed)
        return when {
            existing == null -> {
                dao.upsert(
                    ShoppingItem(
                        uuid = newUuid(),
                        name = trimmed,
                        quantity = quantity,
                        unit = unit,
                        updatedAt = clock(),
                    ),
                )
                true
            }

            existing.isChecked -> {
                dao.upsert(
                    existing.copy(
                        isChecked = false,
                        quantity = quantity,
                        unit = unit,
                        updatedAt = clock(),
                    ),
                )
                true
            }

            else -> false
        }
    }

    suspend fun setChecked(item: ShoppingItem, checked: Boolean) {
        dao.upsert(item.copy(isChecked = checked, updatedAt = clock()))
    }

    suspend fun delete(item: ShoppingItem) = dao.delete(item)

    suspend fun deleteChecked() = dao.deleteChecked()
}
