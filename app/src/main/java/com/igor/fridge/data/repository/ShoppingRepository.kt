package com.igor.fridge.data.repository

import com.igor.fridge.data.Transactor
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.SavedListItem
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.ShoppingItemDao
import com.igor.fridge.data.local.nameKeyOf
import com.igor.fridge.domain.guessCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

/** Gestisce la lista della spesa. */
class ShoppingRepository(
    private val dao: ShoppingItemDao,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
    private val transactor: Transactor = Transactor.Direct,
) {

    fun observeAll(): Flow<List<ShoppingItem>> = dao.observeAll()

    /** La lista com'e' adesso, nell'ordine in cui la mostra la schermata. */
    suspend fun currentItems(): List<ShoppingItem> = dao.observeAll().first()

    suspend fun findByUuid(uuid: String): ShoppingItem? = dao.findByUuid(uuid)

    suspend fun findByName(name: String): ShoppingItem? = dao.findByNameKey(nameKeyOf(name))

    /** Da chiamare dentro la transazione di Undo, prima di qualsiasi scrittura. */
    suspend fun checkUndo(before: ShoppingItem?, after: ShoppingItem?) {
        val uuid = after?.uuid ?: requireNotNull(before).uuid
        if (dao.findByUuid(uuid) != after ||
            (after == null && before != null && dao.findByNameKey(before.nameKey) != null)) {
            throw UndoConflictException()
        }
    }

    /** Ripristina soltanto la riga identificata dal token, mai un omonimo. */
    suspend fun undoChange(before: ShoppingItem?, after: ShoppingItem?) {
        if (before != null) restore(before) else after?.let { dao.delete(it) }
    }

    suspend fun checkedItems(): List<ShoppingItem> = dao.checkedItems()

    /**
     * Mette il prodotto fra le cose da comprare.
     *
     * Se esiste gia' una voce con lo stesso nome ma e' spuntata, viene riportata da
     * comprare invece di essere ignorata: altrimenti un prodotto consumato una seconda
     * volta sparirebbe dall'inventario senza ricomparire in lista.
     *
     * [category] null significa "non la so": una voce nuova la riceve da [guessCategory],
     * una voce ripristinata tiene la sua. Allo stesso modo marca, note, foto, prezzo e
     * negozio sostituiscono quelli di una voce ripristinata solo se indicati.
     *
     * Ricerca e scrittura stanno in una transazione: due aggiunte quasi simultanee dello
     * stesso prodotto (doppio tocco, riga rapida) non creano due voci.
     *
     * @return true se la lista e' cambiata.
     */
    suspend fun addIfAbsent(
        name: String,
        quantity: Double = 1.0,
        unit: QuantityUnit = QuantityUnit.PZ,
        category: FoodCategory? = null,
        brand: String? = null,
        notes: String? = null,
        photoPath: String? = null,
        unitPriceCents: Long? = null,
        store: String? = null,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false

        return transactor.run {
            insertOrRevive(
                trimmed, quantity, unit, category, brand, notes, photoPath, unitPriceCents, store,
            )
        }
    }

    private suspend fun insertOrRevive(
        trimmed: String,
        quantity: Double,
        unit: QuantityUnit,
        category: FoodCategory?,
        brand: String?,
        notes: String?,
        photoPath: String?,
        unitPriceCents: Long?,
        store: String?,
    ): Boolean {
        val existing = dao.findByNameKey(nameKeyOf(trimmed))
        return when {
            existing == null -> {
                dao.upsert(
                    ShoppingItem(
                        uuid = newUuid(),
                        name = trimmed,
                        quantity = quantity,
                        unit = unit,
                        category = category ?: guessCategory(trimmed),
                        brand = brand,
                        notes = notes,
                        photoPath = photoPath,
                        unitPriceCents = unitPriceCents,
                        store = store,
                        updatedAt = clock(),
                    ),
                )
                true
            }

            existing.isChecked -> {
                dao.upsert(
                    existing.copy(
                        isChecked = false,
                        purchasedQuantity = null,
                        quantity = quantity,
                        unit = unit,
                        category = category ?: existing.category,
                        brand = brand ?: existing.brand,
                        notes = notes ?: existing.notes,
                        photoPath = photoPath ?: existing.photoPath,
                        unitPriceCents = unitPriceCents ?: existing.unitPriceCents,
                        store = store ?: existing.store,
                        updatedAt = clock(),
                    ),
                )
                true
            }

            else -> false
        }
    }

    /**
     * Copia nella lista le voci di una lista salvata, con le stesse regole di
     * [addIfAbsent]: cio' che e' gia' da comprare non si raddoppia.
     *
     * Tutta la lista in una transazione: una sola riemissione della lista invece di una per
     * voce, e niente lista caricata a meta' se qualcosa va storto.
     *
     * @return quante voci hanno cambiato la lista.
     */
    suspend fun addAll(items: List<SavedListItem>): Int = transactor.run {
        items.count { item ->
            addIfAbsent(
                name = item.name,
                quantity = item.quantity,
                unit = item.unit,
                category = item.category,
                brand = item.brand,
                notes = item.notes,
                photoPath = item.photoPath,
                unitPriceCents = item.unitPriceCents,
                store = item.store,
            )
        }
    }

    /** Salva le modifiche fatte a una voce. @return la voce come e' stata salvata. */
    suspend fun update(item: ShoppingItem): ShoppingItem {
        val stamped = item.copy(updatedAt = clock())
        dao.upsert(stamped)
        return stamped
    }

    /**
     * Togliere la spunta dimentica anche la quantita' presa: il prodotto e' tornato sullo
     * scaffale, e una quantita' rimasta indietro verrebbe usata al prossimo "Metti in frigo".
     */
    suspend fun setChecked(item: ShoppingItem, checked: Boolean) {
        dao.setChecked(item.uuid, checked, clock())
    }

    /** Dopo un acquisto parziale la voce resta in lista per [remaining], da comprare. */
    suspend fun keepRemainder(item: ShoppingItem, remaining: Double) {
        dao.upsert(
            item.copy(
                quantity = remaining,
                purchasedQuantity = null,
                isChecked = false,
                updatedAt = clock(),
            ),
        )
    }

    /**
     * Rimette in lista una voce rimossa, con il suo identificatore originale: serve
     * ad annullare uno spostamento in frigo senza che la voce cambi identita'.
     */
    suspend fun restore(item: ShoppingItem) {
        dao.upsert(item.copy(updatedAt = clock()))
    }

    suspend fun delete(item: ShoppingItem) = dao.delete(item)

    /** Toglie la voce ancora da comprare con questo nome: annulla un [addIfAbsent]. */
    suspend fun removeUnchecked(name: String) {
        dao.findByNameKey(nameKeyOf(name))?.takeUnless { it.isChecked }?.let { dao.delete(it) }
    }

    /** Le foto a cui la lista tiene ancora: le altre si possono cancellare. */
    suspend fun photoNames(): Set<String> = dao.photoPaths().toSet()
}
