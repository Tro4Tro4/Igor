package com.igor.fridge.data.repository

import com.igor.fridge.data.local.SavedList
import com.igor.fridge.data.local.SavedListDao
import com.igor.fridge.data.local.SavedListItem
import com.igor.fridge.data.local.SavedListSummary
import com.igor.fridge.data.local.ShoppingItem
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/** Le liste della spesa salvate con un nome, da ricaricare. */
class SavedListRepository(
    private val dao: SavedListDao,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {

    /** Esito di [save]: se il nome era gia' usato, la lista e' stata sovrascritta. */
    enum class SaveResult { CREATED, REPLACED, NOTHING_TO_SAVE }

    fun observeSummaries(): Flow<List<SavedListSummary>> = dao.observeSummaries()

    /**
     * Salva [items] con il nome [name]. Un nome gia' usato (senza distinguere maiuscole)
     * sovrascrive quella lista mantenendone l'identita': "Spesa settimanale" si aggiorna,
     * non si sdoppia.
     *
     * Della voce si salva cosa comprare, non lo stato dell'acquisto: spunta e quantita'
     * presa appartengono a una spesa, non al modello da cui si riparte.
     */
    suspend fun save(name: String, items: List<ShoppingItem>): SaveResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || items.isEmpty()) return SaveResult.NOTHING_TO_SAVE

        val now = clock()
        val existing = dao.findByName(trimmed)
        val list = existing?.copy(name = trimmed, updatedAt = now)
            ?: SavedList(uuid = newUuid(), name = trimmed, createdAt = now, updatedAt = now)

        dao.replace(
            list = list,
            items = items.mapIndexed { index, item ->
                SavedListItem(
                    uuid = newUuid(),
                    listUuid = list.uuid,
                    position = index,
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    category = item.category,
                    brand = item.brand,
                    notes = item.notes,
                    photoPath = item.photoPath,
                )
            },
        )
        return if (existing == null) SaveResult.CREATED else SaveResult.REPLACED
    }

    suspend fun itemsOf(listUuid: String): List<SavedListItem> = dao.itemsOf(listUuid)

    suspend fun delete(listUuid: String) = dao.delete(listUuid)

    /** Le foto a cui le liste salvate tengono ancora. */
    suspend fun photoNames(): Set<String> = dao.photoPaths().toSet()
}
