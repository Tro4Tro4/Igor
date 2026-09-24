package com.igor.fridge.data.repository

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.FoodItemDao
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.StorageLocation
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unico punto di accesso all'inventario alimentare.
 *
 * Identificatore e timbro temporale si assegnano qui e in nessun altro posto: se la UI
 * potesse dimenticarsene, un articolo finirebbe in database senza identita' stabile o
 * senza il dato che serve a risolvere i conflitti di una futura sincronizzazione.
 * [clock] e [newUuid] sono parametri per poter scrivere test deterministici.
 */
class FoodRepository(
    private val dao: FoodItemDao,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeAll(): Flow<List<FoodItem>> = dao.observeAll()

    suspend fun findByUuid(uuid: String): FoodItem? = dao.findByUuid(uuid)

    suspend fun findLastByBarcode(barcode: String): FoodItem? = dao.findLastByBarcode(barcode)

    suspend fun findLastByName(name: String): FoodItem? = dao.findLastByName(name.trim())

    /** @return l'articolo come e' stato salvato, con identificatore e timbro valorizzati. */
    suspend fun save(item: FoodItem): FoodItem {
        val stamped = item.copy(
            uuid = item.uuid.ifBlank { newUuid() },
            updatedAt = clock(),
        )
        dao.upsert(stamped)
        return stamped
    }

    /**
     * Crea un articolo a partire da una voce della lista della spesa.
     *
     * Categoria e posizione sono ereditate dall'ultima volta che quel nome e' stato in
     * casa — anche se quell'articolo e' gia' stato consumato — cosi' chi spunta "Latte"
     * non deve ricatalogarlo ogni volta. Nome, quantita' e unita' arrivano invece dalla
     * lista: sono dati che l'utente ha inserito, e prevalgono.
     *
     * La scadenza resta assente perche' la lista della spesa non puo' conoscerla, e il
     * codice a barre non si eredita perche' identifica una confezione, non un prodotto.
     */
    suspend fun addFromShopping(name: String, quantity: Double, unit: QuantityUnit): FoodItem {
        val trimmed = name.trim()
        val previous = dao.findLastByName(trimmed)
        return save(
            FoodItem(
                uuid = "",
                name = trimmed,
                category = previous?.category ?: FoodCategory.ALTRO,
                location = previous?.location ?: StorageLocation.FRIGO,
                quantity = quantity,
                unit = unit,
            ),
        )
    }

    /** Fa uscire l'articolo dall'inventario conservandone la storia. */
    suspend fun remove(item: FoodItem, reason: RemovalReason) {
        val now = clock()
        dao.upsert(item.copy(removedAt = now, removalReason = reason, updatedAt = now))
    }

    /** Annulla una rimozione. */
    suspend fun restore(item: FoodItem) {
        dao.upsert(item.copy(removedAt = null, removalReason = null, updatedAt = clock()))
    }

    /** Articoli scaduti oppure in scadenza entro [withinDays] giorni a partire da [today]. */
    suspend fun findExpiring(today: LocalDate, withinDays: Int): List<FoodItem> =
        dao.findExpiringOnOrBefore(today.plusDays(withinDays.toLong()))
}
