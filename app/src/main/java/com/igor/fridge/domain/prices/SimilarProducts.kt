package com.igor.fridge.domain.prices

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.domain.nameWords
import java.time.LocalDate

/**
 * Un prodotto simile a quello cercato, con il suo ultimo prezzo in un negozio.
 *
 * [unitPriceCents] e' per [referenceUnit]: al kg o al litro se se ne conosce il formato,
 * altrimenti al pezzo (il prezzo della confezione). [privateLabel] e' la catena di cui e'
 * la marca del supermercato, se lo e'.
 */
data class SimilarOffer(
    val productName: String,
    val brands: String?,
    val privateLabel: String?,
    val store: String,
    val priceCents: Long,
    val unitPriceCents: Long,
    val referenceUnit: QuantityUnit,
    val date: LocalDate,
    val source: PriceSource,
    /** Quanto il nome somiglia a quello cercato, da 0 a 1. */
    val similarity: Double,
) {
    /** Un prezzo al kg o al litro: l'unico che si confronta fra prodotti diversi. */
    val isComparable: Boolean get() = referenceUnit == QuantityUnit.KG || referenceUnit == QuantityUnit.L
}

/**
 * Le parole che descrivono un prodotto: senza quelle corte ("di", "ps"), senza formati
 * ("500g", "1l") e senza i nomi delle catene, che dicono dove si compra e non cosa e'.
 */
fun descriptionWords(name: String): List<String> =
    nameWords(name).filter { word ->
        word.length >= MIN_WORD && word.none(Char::isDigit) && word !in CHAIN_WORDS
    }

/**
 * Solo i nomi di una parola: di "Fior Fiore" (Coop) togliere "fiore" rovinerebbe
 * "Mozzarella fior di latte".
 */
private val CHAIN_WORDS: Set<String> =
    KNOWN_CHAINS.flatMap { it.keywords + it.privateLabels }.filterNot { ' ' in it }.toSet()

/**
 * Quanto due nomi descrivono lo stesso tipo di prodotto, da 0 a 1: la quota di parole in
 * comune. La prima parola e' il prodotto ("latte" in "Latte parzialmente scremato") e
 * deve coincidere: "Latte" e "Caffe' latte" non sono simili. Vale anche l'abbreviazione
 * della cassa ("MOZZAR" per mozzarella).
 */
fun nameSimilarity(a: List<String>, b: List<String>): Double {
    if (a.isEmpty() || b.isEmpty() || !sameWord(a.first(), b.first())) return 0.0
    val shared = a.count { word -> b.any { sameWord(it, word) } }
    return shared.toDouble() / (a.size + b.size - shared)
}

private fun sameWord(x: String, y: String): Boolean {
    if (x == y) return true
    val (short, long) = if (x.length <= y.length) x to y else y to x
    return short.length >= MIN_ABBREVIATION && long.startsWith(short) && short.length * 2 >= long.length
}

/**
 * Gli acquisti registrati di prodotti simili a [name] ma diversi ([productKey] esclusa):
 * l'ultimo prezzo di ciascun prodotto in ciascun negozio.
 */
fun ownSimilarOffers(name: String, productKey: String, records: List<PriceRecord>): List<SimilarOffer> {
    val wanted = descriptionWords(name)
    if (wanted.isEmpty()) return emptyList()
    return records.asSequence()
        .filter { it.productKey != productKey }
        .mapNotNull { record ->
            val similarity = nameSimilarity(wanted, descriptionWords(record.productName))
            if (similarity <= 0.0) return@mapNotNull null
            val store = storeLabel(record.store) ?: return@mapNotNull null
            SimilarOffer(
                productName = record.productName,
                brands = null,
                privateLabel = privateLabelChain(record.productName)?.name,
                store = store,
                priceCents = record.totalCents,
                unitPriceCents = record.unitPriceCents,
                referenceUnit = record.referenceUnit,
                date = record.purchasedOn,
                source = PriceSource.MINE,
                similarity = similarity,
            )
        }
        .toList()
        .let(::rankOffers)
}

/**
 * Un'offerta per prodotto e negozio, la piu' recente; poi le confrontabili (al kg o al
 * litro) dalla piu' conveniente, e in fondo quelle di cui si sa solo il prezzo a confezione.
 */
fun rankOffers(offers: List<SimilarOffer>): List<SimilarOffer> =
    offers.groupBy { it.productName.lowercase() to it.store.lowercase() }
        .map { (_, group) -> group.maxWith(compareBy<SimilarOffer> { it.date }.thenByDescending { it.unitPriceCents }) }
        .sortedWith(
            compareByDescending<SimilarOffer> { it.isComparable }
                .thenBy { it.referenceUnit }
                .thenBy { it.unitPriceCents }
                .thenByDescending { it.similarity },
        )

/**
 * L'alternativa piu' conveniente a un prezzo di [baselineCents] per [referenceUnit]: un
 * prodotto simile che costa meno al kg o al litro. Un prezzo a confezione non si propone:
 * un pacco piu' piccolo costa meno ma non conviene.
 */
fun bestAlternative(offers: List<SimilarOffer>, baselineCents: Long, referenceUnit: QuantityUnit): SimilarOffer? =
    offers.filter { it.isComparable && it.referenceUnit == referenceUnit && it.unitPriceCents < baselineCents }
        .minWithOrNull(compareBy<SimilarOffer> { it.unitPriceCents }.thenByDescending { it.similarity })

/** Un prodotto simile che conviene, e di quanto al kg o al litro. */
data class Alternative(val offer: SimilarOffer, val savingPercent: Int)

/**
 * La migliore alternativa ai prezzi noti di un prodotto ([baselines]: prezzo al kg o al
 * litro e unita'), se fa risparmiare almeno [MIN_SAVING_PERCENT]: qualche centesimo al
 * kg non vale il cambio di marca. Per ogni unita' conta il prezzo piu' basso gia' noto.
 */
fun cheaperAlternative(offers: List<SimilarOffer>, baselines: List<Pair<Long, QuantityUnit>>): Alternative? =
    baselines.groupBy({ it.second }, { it.first })
        .mapNotNull { (unit, prices) ->
            val baseline = prices.min()
            if (baseline <= 0) return@mapNotNull null
            bestAlternative(offers, baseline, unit)?.let { offer ->
                Alternative(offer, ((baseline - offer.unitPriceCents) * 100 / baseline).toInt())
            }
        }
        .filter { it.savingPercent >= MIN_SAVING_PERCENT }
        .maxByOrNull { it.savingPercent }

private const val MIN_SAVING_PERCENT = 5

/** "di", "al", "ps" non descrivono un prodotto. */
private const val MIN_WORD = 3

/** Un'abbreviazione piu' corta ("LAT") prenderebbe troppe parole. */
private const val MIN_ABBREVIATION = 4
