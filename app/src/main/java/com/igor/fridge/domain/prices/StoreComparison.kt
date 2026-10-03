package com.igor.fridge.domain.prices

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.domain.receipt.receiptMatches
import java.time.LocalDate
import kotlin.math.roundToLong

/** Da dove viene un prezzo: dai tuoi scontrini o dalla comunita' (Open Prices). */
enum class PriceSource { MINE, COMMUNITY }

/** Un prezzo osservato in un negozio, per unita' di riferimento. */
data class StoreObservation(
    val store: String,
    val unitPriceCents: Long,
    val referenceUnit: QuantityUnit,
    val date: LocalDate,
    val source: PriceSource,
    /** Quanto costava l'acquisto intero (per stimare un prodotto comprato a confezioni). */
    val totalCents: Long? = null,
    val quantity: Double? = null,
    val unit: QuantityUnit? = null,
)

fun PriceRecord.toObservation(): StoreObservation? {
    val label = storeLabel(store) ?: return null
    return StoreObservation(
        store = label,
        unitPriceCents = unitPriceCents,
        referenceUnit = referenceUnit,
        date = purchasedOn,
        source = PriceSource.MINE,
        totalCents = totalCents,
        quantity = quantity,
        unit = unit,
    )
}

/**
 * L'ultimo prezzo per ogni negozio, dal piu' conveniente. Un prezzo personale vince su uno
 * della comunita' dello stesso negozio solo se non e' piu' vecchio: conta il piu' recente.
 * A parita' di data e di fonte vince il prezzo piu' basso, qualunque sia l'ordine d'arrivo.
 */
fun latestByStore(observations: List<StoreObservation>): List<StoreObservation> =
    observations.groupBy { it.store.lowercase() }
        .map { (_, group) -> group.maxWith(LATEST_FIRST) }
        .sortedWith(compareBy<StoreObservation> { it.unitPriceCents }.thenBy { it.store.lowercase() })

private val LATEST_FIRST: Comparator<StoreObservation> =
    compareBy<StoreObservation> { it.date }
        .thenBy { it.source == PriceSource.MINE }
        .thenByDescending { it.unitPriceCents }
        .thenByDescending { it.totalCents ?: Long.MAX_VALUE }

/** Quanto costerebbe una voce della lista in ogni negozio. */
data class ItemQuote(
    val item: ShoppingItem,
    /** Negozio -> costo stimato in centesimi, dal piu' conveniente. */
    val byStore: List<Pair<StoreObservation, Long>>,
) {
    val cheapest: Pair<StoreObservation, Long>? get() = byStore.firstOrNull()
}

/** Quanto costerebbe la lista in un negozio, sulle voci di cui conosce il prezzo. */
data class StoreEstimate(
    val store: String,
    val totalCents: Long,
    val covered: Int,
    val itemCount: Int,
) {
    val isComplete: Boolean get() = covered == itemCount
}

data class ListComparison(
    val quotes: List<ItemQuote>,
    val stores: List<StoreEstimate>,
    /** Comprando ogni voce dove costa meno, sulle voci con almeno un prezzo. */
    val splitCents: Long,
    val pricedItems: Int,
)

/**
 * Stima il costo di ogni voce in ogni negozio.
 *
 * Il prezzo unitario si applica alla quantita' da comprare se le unita' sono confrontabili
 * (2 kg di mele a 1,99 €/kg); altrimenti si assume che si compri lo stesso formato
 * dell'ultima volta, moltiplicato per i pezzi in lista (se l'ultima volta si e' comprato a
 * peso, non si stima). I negozi sono ordinati per voci
 * coperte e poi per totale: un totale basso su meta' lista non e' un affare.
 */
fun compareList(
    items: List<ShoppingItem>,
    observationsFor: (ShoppingItem) -> List<StoreObservation>,
): ListComparison {
    val quotes = items.map { item ->
        val byStore = latestByStore(observationsFor(item))
            .mapNotNull { obs -> estimateCents(item, obs)?.let { obs to it } }
            .sortedBy { it.second }
        ItemQuote(item, byStore)
    }
    val stores = quotes.flatMap { q -> q.byStore.map { (obs, cents) -> obs.store to cents } }
        .groupBy({ it.first }, { it.second })
        .map { (store, costs) -> StoreEstimate(store, costs.sum(), costs.size, items.size) }
        .sortedWith(compareByDescending<StoreEstimate> { it.covered }.thenBy { it.totalCents })
    val priced = quotes.filter { it.cheapest != null }
    return ListComparison(
        quotes = quotes,
        stores = stores,
        splitCents = priced.sumOf { it.cheapest!!.second },
        pricedItems = priced.size,
    )
}

internal fun estimateCents(item: ShoppingItem, obs: StoreObservation): Long? {
    val (quantity, reference) = toReference(item.quantity, item.unit)
    if (reference == obs.referenceUnit) return (obs.unitPriceCents * quantity).roundToLong()
    // Unita' diverse: "1 pz" di pasta contro un prezzo al kg. Si stima con l'ultimo formato
    // comprato, se c'e', moltiplicato per i pezzi; altrimenti non si stima.
    val pack = packCents(obs) ?: return null
    val pieces = if (item.unit.isCount()) item.quantity else 1.0
    return (pack * pieces).roundToLong()
}

/**
 * Quanto costava una confezione l'ultima volta: l'acquisto intero se era un formato
 * ("PASTA 500G"), diviso per i pezzi se erano piu' pezzi. Un acquisto a peso (1,5 kg di
 * mele) non dice quanto costa una mela: null.
 */
private fun packCents(obs: StoreObservation): Double? {
    val total = obs.totalCents ?: return null
    val unit = obs.unit ?: return null
    val quantity = obs.quantity ?: return null
    return when {
        unit.isCount() -> if (quantity > 0.0) total / quantity else null
        unit == QuantityUnit.G || unit == QuantityUnit.ML || unit == QuantityUnit.L -> total.toDouble()
        else -> null
    }
}

private fun QuantityUnit.isCount(): Boolean = this == QuantityUnit.PZ || this == QuantityUnit.CONF

/**
 * Gli acquisti registrati che parlano di questa voce: stesso nome normalizzato, oppure un
 * nome di cassa che contiene ogni parola della voce ("Latte" in "Latte ps granarolo").
 */
fun recordsFor(item: ShoppingItem, records: List<PriceRecord>): List<PriceRecord> {
    val key = productKey(item.name)
    val exact = records.filter { it.productKey == key }
    if (exact.isNotEmpty()) return exact
    return records.filter { receiptMatches(item.name, it.productName) }
}
