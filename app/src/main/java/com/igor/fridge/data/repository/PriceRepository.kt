package com.igor.fridge.data.repository

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.PriceRecordDao
import com.igor.fridge.data.local.ProductCode
import com.igor.fridge.data.local.ProductCodeDao
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.domain.prices.normalizeGtin
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
    private val codes: ProductCodeDao,
    private val clock: () -> Instant = Instant::now,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeAll(): Flow<List<PriceRecord>> = dao.observeAll()

    fun observeProduct(productKey: String): Flow<List<PriceRecord>> = dao.observeByProduct(productKey)

    /**
     * Registra gli acquisti di uno scontrino. Si scartano quelli senza nome, senza importo
     * o con una quantita' che non permette di calcolare un prezzo unitario.
     *
     * Uno scontrino gia' registrato ([isAlreadyRecorded]) non si registra una seconda
     * volta: rileggerlo raddoppierebbe la spesa del mese e falserebbe medie e minimi.
     *
     * @return quanti prezzi sono stati registrati.
     */
    suspend fun record(purchases: List<Purchase>, store: String?, date: LocalDate): Int {
        val records = toRecords(purchases, store, date)
        if (records.isEmpty() || containsAll(records, date)) return 0
        dao.insertAll(records)
        return records.size
    }

    /**
     * Vero se ogni prezzo di questo scontrino e' gia' nello storico: stesso giorno, stesso
     * negozio, stesso prodotto e stesso importo, contando i doppioni (due latti da 1,29
     * chiedono due righe da 1,29). Uno scontrino che aggiunge anche un solo prezzo nuovo
     * e' un altro scontrino, e si registra per intero.
     */
    suspend fun isAlreadyRecorded(purchases: List<Purchase>, store: String?, date: LocalDate): Boolean {
        val records = toRecords(purchases, store, date)
        return records.isNotEmpty() && containsAll(records, date)
    }

    private suspend fun containsAll(records: List<PriceRecord>, date: LocalDate): Boolean {
        val existing = dao.onDate(date).groupingBy { it.identity() }.eachCount().toMutableMap()
        return records.all { record ->
            val left = existing[record.identity()] ?: 0
            existing[record.identity()] = left - 1
            left > 0
        }
    }

    private fun PriceRecord.identity() = Triple(productKey, totalCents, store?.lowercase())

    private fun toRecords(purchases: List<Purchase>, store: String?, date: LocalDate): List<PriceRecord> {
        val now = clock()
        val cleanStore = store?.trim()?.takeIf { it.isNotEmpty() }
        return purchases.mapNotNull { purchase ->
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
    }

    /** L'ultimo prezzo pagato per questo prodotto, da proporre nella lista della spesa. */
    suspend fun latest(name: String): PriceRecord? =
        productKey(name).takeIf { it.isNotEmpty() }?.let { dao.findLatest(it) }

    suspend fun delete(record: PriceRecord) = dao.delete(record)

    /** Il codice a barre associato al prodotto, se c'e'. */
    suspend fun barcodeOf(productKey: String): String? = codes.find(productKey)?.barcode

    suspend fun barcodeForName(name: String): String? =
        productKey(name).takeIf { it.isNotEmpty() }?.let { barcodeOf(it) }

    /**
     * Associa un codice a barre al prodotto. Il codice si controlla (cifre, lunghezza,
     * cifra di controllo) prima di salvarlo.
     * @return il codice salvato, oppure null se non e' valido.
     */
    suspend fun setBarcode(productKey: String, barcode: String): String? {
        val normalized = normalizeGtin(barcode) ?: return null
        codes.upsert(ProductCode(productKey = productKey, barcode = normalized, updatedAt = clock()))
        return normalized
    }

    suspend fun clearBarcode(productKey: String) = codes.delete(productKey)

    suspend fun allCodes(): List<ProductCode> = codes.all()

    /** Tutti i codici associati, per chiave del prodotto. */
    suspend fun allBarcodes(): Map<String, String> = codes.all().associate { it.productKey to it.barcode }
}
