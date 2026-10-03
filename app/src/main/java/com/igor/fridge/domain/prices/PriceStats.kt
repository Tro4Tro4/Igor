package com.igor.fridge.domain.prices

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.domain.nameWords
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/** La chiave che raggruppa gli acquisti dello stesso prodotto: "Caffè" e "CAFFE" coincidono. */
fun productKey(name: String): String = nameWords(name).joinToString(" ")

/**
 * Porta una quantita' all'unita' con cui si confrontano i prezzi: grammi in chili,
 * millilitri in litri; pezzi e confezioni restano tali.
 */
fun toReference(quantity: Double, unit: QuantityUnit): Pair<Double, QuantityUnit> = when (unit) {
    QuantityUnit.G -> quantity / 1000.0 to QuantityUnit.KG
    QuantityUnit.ML -> quantity / 1000.0 to QuantityUnit.L
    else -> quantity to unit
}

/** Prezzo per unita' di riferimento, in centesimi; null se la quantita' non ha senso. */
fun unitPriceCents(totalCents: Long, quantity: Double, unit: QuantityUnit): Long? {
    val (reference, _) = toReference(quantity, unit)
    if (reference <= 0.0 || !reference.isFinite()) return null
    return (totalCents / reference).roundToLong()
}

/** Come si muove il prezzo di un prodotto. Gli importi sono per [referenceUnit]. */
data class ProductPriceSummary(
    val productKey: String,
    val productName: String,
    val referenceUnit: QuantityUnit,
    val lastCents: Long,
    val lastDate: LocalDate,
    val lastStore: String?,
    /** Il prezzo dell'acquisto precedente, per dire se e' salito o sceso; null se e' il primo. */
    val previousCents: Long?,
    val minCents: Long,
    val maxCents: Long,
    val averageCents: Long,
    val purchases: Int,
    val totalSpentCents: Long,
) {
    /** Variazione rispetto all'acquisto precedente, in percentuale; null se non c'e'. */
    val changePercent: Double?
        get() = previousCents?.takeIf { it > 0 }?.let { (lastCents - it) * 100.0 / it }
}

/**
 * Un riepilogo per prodotto, dal piu' recente. Gli acquisti con un'unita' di riferimento
 * diversa dall'ultima (lo stesso nome comprato una volta a peso e una al pezzo) restano
 * fuori dal confronto e dalla spesa totale: un prezzo al kg e uno al pezzo non si
 * confrontano.
 *
 * Minimo, massimo e media ignorano i prezzi al kg o al litro calcolati su meno di
 * [MIN_REFERENCE_QUANTITY] (5 g o 5 ml): sono quasi sempre una quantita' letta o scritta
 * male, e 1,29 € su 0,001 g farebbe un massimo di milioni di euro al kg.
 */
fun summarize(records: List<PriceRecord>): List<ProductPriceSummary> =
    records.groupBy { it.productKey }
        .map { (key, group) ->
            val sorted = group.sortedWith(compareByDescending<PriceRecord> { it.purchasedOn }.thenByDescending { it.createdAt })
            val last = sorted.first()
            val comparable = sorted.filter { it.referenceUnit == last.referenceUnit }
            val prices = comparable.filter { it.isPlausible() }.ifEmpty { comparable }.map { it.unitPriceCents }
            ProductPriceSummary(
                productKey = key,
                productName = last.productName,
                referenceUnit = last.referenceUnit,
                lastCents = last.unitPriceCents,
                lastDate = last.purchasedOn,
                lastStore = last.store,
                previousCents = comparable.getOrNull(1)?.unitPriceCents,
                minCents = prices.min(),
                maxCents = prices.max(),
                averageCents = prices.average().roundToLong(),
                purchases = comparable.size,
                totalSpentCents = comparable.sumOf { it.totalCents },
            )
        }
        .sortedWith(compareByDescending<ProductPriceSummary> { it.lastDate }.thenBy { it.productName.lowercase() })

/** Sotto questa quantita' (in kg o litri) un prezzo al kg o al litro non e' credibile. */
private const val MIN_REFERENCE_QUANTITY = 0.005

private fun PriceRecord.isPlausible(): Boolean = when (unit) {
    QuantityUnit.G, QuantityUnit.KG, QuantityUnit.ML, QuantityUnit.L ->
        toReference(quantity, unit).first >= MIN_REFERENCE_QUANTITY
    else -> true
}

/** Quanto si e' speso ogni mese, dal piu' recente, sugli scontrini registrati. */
fun monthlySpending(records: List<PriceRecord>): List<Pair<YearMonth, Long>> =
    records.groupBy { YearMonth.from(it.purchasedOn) }
        .map { (month, group) -> month to group.sumOf { it.totalCents } }
        .sortedByDescending { it.first }
