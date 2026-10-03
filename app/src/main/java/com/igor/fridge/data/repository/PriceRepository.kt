package com.igor.fridge.data.repository

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.PriceRecordDao
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.domain.prices.productKey
import com.igor.fridge.domain.prices.toReference
import com.igor.fridge.domain.prices.unitPriceCents
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Un acquisto da registrare: cosa, quanto e quanto e' costato in tutto. */
data class Purchase(
    val name: String,
    val quantity: Double,
    val unit: QuantityUnit,
    val totalCents: Long,
)

/** Lo storico dei prezzi pagati. */
class PriceRepository(
    private val dao: PriceRecordDao,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeAll(): Flow<List<PriceRecord>> = dao.observeAll()

    fun observeProduct(productKey: String): Flow<List<PriceRecord>> = dao.observeByProduct(productKey)

    /**
     * Registra gli acquisti di uno scontrino. Si scartano quelli senza nome, senza importo
     * o con una quantita' che non permette di calcolare un prezzo unitario.
     *
     * @return quanti prezzi sono stati registrati.
     */
    suspend fun record(purchases: List<Purchase>, store: String?, date: LocalDate): Int {
        val now = clock()
        val cleanStore = store?.trim()?.takeIf { it.isNotEmpty() }
        val records = purchases.mapNotNull { purchase ->
            val name = purchase.name.trim()
            val key = productKey(name)
            if (key.isEmpty() || purchase.totalCents <= 0) return@mapNotNull null
            val unitPrice = unitPriceCents(purchase.totalCents, purchase.quantity, purchase.unit)
                ?: return@mapNotNull null
            PriceRecord(
                uuid = newUuid(),
                productKey = key,
                productName = name,
                purchasedOn = date,
                store = cleanStore,
                quantity = purchase.quantity,
                unit = purchase.unit,
                totalCents = purchase.totalCents,
                unitPriceCents = unitPrice,
                referenceUnit = toReference(purchase.quantity, purchase.unit).second,
                createdAt = now,
            )
        }
        if (records.isNotEmpty()) dao.insertAll(records)
        return records.size
    }

    /** L'ultimo prezzo pagato per questo prodotto, da proporre nella lista della spesa. */
    suspend fun latest(name: String): PriceRecord? =
        productKey(name).takeIf { it.isNotEmpty() }?.let { dao.findLatest(it) }

    suspend fun delete(record: PriceRecord) = dao.delete(record)
}
