package com.igor.fridge.domain.receipt

import com.igor.fridge.data.local.QuantityUnit
import java.util.Locale

/** Un prodotto letto dallo scontrino. */
data class ReceiptEntry(
    val name: String,
    /** null se lo scontrino non dice quanto: decide chi conferma. */
    val quantity: Double? = null,
    val unit: QuantityUnit? = null,
    /** Importo della riga in euro, cosi' come stampato. */
    val price: Double? = null,
)

/**
 * Estrae i prodotti dalle righe di uno scontrino italiano (documento commerciale).
 *
 * Una riga prodotto e' un nome seguito da un importo ("LATTE PS 1L  4%  1,29"); l'aliquota
 * IVA e le lettere di reparto in mezzo si ignorano. Le righe di dettaglio danno la quantita'
 * del prodotto che accompagnano: "2 x 0,89" (pezzi) e "0,725 kg x 2,00 €/kg" (peso). Alcuni
 * negozi le stampano sotto il prodotto, altri sopra: si attaccano a quello il cui importo
 * corrisponde al conto, o in mancanza a quello sopra. Un formato nel nome ("1L", "500G")
 * vale come quantita' quando manca il dettaglio.
 *
 * Uno sconto ("SCONTO PASTA -0,20", "PROMO 0,50-") riduce l'importo del prodotto sopra:
 * il prezzo che interessa tenere d'occhio e' quello pagato. Resi e righe di servizio si
 * scartano; dopo il totale non ci sono piu' prodotti.
 * Cio' che non e' leggibile si salta: la schermata di conferma permette di correggere, ma
 * non di scoprire una riga inventata.
 */
fun parseReceipt(lines: List<String>): List<ReceiptEntry> {
    val entries = mutableListOf<ReceiptEntry>()
    // Il dettaglio di una riga gia' completata non deve finire su un'altra.
    var lastHasDetail = false
    // Un dettaglio che non torna col prodotto sopra aspetta quello sotto; se nemmeno
    // quello torna, va a quello sopra (pendingAbove), che e' la disposizione piu' comune.
    var pending: Detail? = null
    var pendingAbove = -1

    for (raw in lines) {
        val line = clean(raw)
        if (line.isEmpty()) continue
        if (END.containsMatchIn(line.uppercase(Locale.ITALIAN))) break

        val detail = parseDetail(line)
        if (detail != null) {
            val above = entries.lastIndex
            if (above >= 0 && !lastHasDetail && detail.matches(entries[above].price)) {
                entries[above] = detail.applyTo(entries[above])
                lastHasDetail = true
            } else {
                pending = detail
                pendingAbove = if (above >= 0 && !lastHasDetail) above else -1
            }
            continue
        }

        val discount = parseDiscount(line)
        if (discount != null) {
            val last = entries.lastIndex
            if (last >= 0) {
                val price = entries[last].price
                if (price != null) entries[last] = entries[last].copy(price = (price - discount).coerceAtLeast(0.0))
            }
            continue
        }

        val product = parseProductLine(line) ?: continue
        val waiting = pending
        if (waiting != null && waiting.matches(product.price)) {
            entries += waiting.applyTo(product)
            lastHasDetail = true
        } else {
            if (waiting != null && pendingAbove >= 0) {
                entries[pendingAbove] = waiting.applyTo(entries[pendingAbove])
            }
            entries += product
            lastHasDetail = false
        }
        pending = null
        pendingAbove = -1
    }
    val waiting = pending
    if (waiting != null && pendingAbove >= 0) {
        entries[pendingAbove] = waiting.applyTo(entries[pendingAbove])
    }
    return entries
}

/** Quantita' letta da una riga di dettaglio, con il prezzo unitario per verificarla. */
private data class Detail(val quantity: Double, val unit: QuantityUnit, val unitPrice: Double) {
    fun matches(price: Double?): Boolean =
        price != null && kotlin.math.abs(quantity * unitPrice - price) <= 0.02

    /** "2 x LATTE 1L" fa 2 litri: il formato nel nome moltiplica il numero di pezzi. */
    fun applyTo(entry: ReceiptEntry): ReceiptEntry =
        if (unit == QuantityUnit.PZ && entry.quantity != null && entry.unit != null) {
            entry.copy(quantity = entry.quantity * quantity)
        } else {
            entry.copy(quantity = quantity, unit = unit)
        }
}

private fun parseDetail(line: String): Detail? {
    WEIGHT.matchEntire(line)?.let { m ->
        val weight = amount(m.groupValues[1]) ?: return null
        val unitPrice = amount(m.groupValues[2]) ?: return null
        return Detail(weight, QuantityUnit.KG, unitPrice)
    }
    PIECES.matchEntire(line)?.let { m ->
        val count = m.groupValues[1].toDoubleOrNull() ?: return null
        val unitPrice = amount(m.groupValues[2]) ?: return null
        if (count <= 0.0) return null
        return Detail(count, QuantityUnit.PZ, unitPrice)
    }
    return null
}

/**
 * L'importo di una riga di sconto, in positivo: un importo col segno meno (davanti o, come
 * stampano molte casse, dietro) oppure una riga che si dichiara sconto.
 */
private fun parseDiscount(line: String): Double? {
    val tokens = line.split(Regex("\\s+"))
    var index = tokens.lastIndex
    while (index >= 0 && isMarker(tokens[index])) index--
    if (index < 0) return null
    val token = tokens[index]
    val match = DISCOUNT_AMOUNT.matchEntire(token) ?: return null
    val signed = token.startsWith("-") || token.endsWith("-")
    val declared = DISCOUNT_WORDS.containsMatchIn(line.uppercase(Locale.ITALIAN))
    if (!signed && !declared) return null
    return amount(match.groupValues[1])?.takeIf { it > 0.0 }
}

private fun parseProductLine(line: String): ReceiptEntry? {
    val tokens = line.split(Regex("\\s+")).toMutableList()
    // L'importo e' l'ultimo numero con due decimali; dopo possono esserci solo marcatori.
    var priceIndex = tokens.lastIndex
    while (priceIndex >= 0 && isMarker(tokens[priceIndex])) priceIndex--
    if (priceIndex < 1) return null
    val priceToken = tokens[priceIndex]
    if (!PRICE.matches(priceToken)) return null
    val price = amount(priceToken) ?: return null
    if (priceToken.startsWith("-")) return null

    val nameTokens = tokens.subList(0, priceIndex).filterNot { isMarker(it) }.toMutableList()
    // I codici articolo (solo cifre, lunghi) non sono parte del nome.
    nameTokens.removeAll { it.length >= 5 && it.all(Char::isDigit) }

    var quantity: Double? = null
    var unit: QuantityUnit? = null
    val sizeIndex = nameTokens.indexOfLast { SIZE.matches(it) }
    if (sizeIndex >= 0) {
        val m = SIZE.matchEntire(nameTokens[sizeIndex])!!
        val size = SIZE_UNITS[m.groupValues[2].uppercase(Locale.ITALIAN)]
        val value = amount(m.groupValues[1])
        if (size != null && value != null) {
            quantity = value * size.second
            unit = size.first
            nameTokens.removeAt(sizeIndex)
        }
    }

    val name = nameTokens.joinToString(" ").trim()
    if (name.count { it.isLetter() } < 2) return null
    val upper = name.uppercase(Locale.ITALIAN)
    if (SKIP_WORDS.any { Regex("\\b$it\\b").containsMatchIn(upper) }) return null

    return ReceiptEntry(name = prettify(name), quantity = quantity, unit = unit, price = price)
}

/** Aliquote IVA, lettere di reparto, simboli di valuta: stanno fra nome e prezzo. */
private fun isMarker(token: String): Boolean =
    token.matches(Regex("\\d{1,2}(?:[.,]\\d+)?%|[A-Ea-e]|\\*+|€|EUR|eur|E|VI|IVA\\d*"))

/** "LATTE PS GRANAROLO" diventa "Latte ps granarolo". */
private fun prettify(name: String): String =
    name.lowercase(Locale.ITALIAN).replaceFirstChar { it.titlecase(Locale.ITALIAN) }

/** Toglie il simbolo dell'euro e gli spazi superflui, che l'OCR mette dove capita. */
private fun clean(line: String): String =
    line.replace("€", " ").replace(Regex("\\s+"), " ").trim()

private fun amount(text: String): Double? =
    text.removePrefix("-").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private val PRICE = Regex("^-?\\d{1,4}[.,]\\d{2}$")
private val DISCOUNT_AMOUNT = Regex("^-?(\\d{1,4}[.,]\\d{2})-?$")
private val DISCOUNT_WORDS = Regex("\\b(SCONTO|SCONTI|PROMO|ABBUONO|RIDUZIONE|OFFERTA)\\b")
private val PIECES = Regex("^(\\d{1,3})\\s*[xX*]\\s*(\\d{1,4}[.,]\\d{2})(?:\\s.*)?$")
private val WEIGHT = Regex(
    "^(\\d{1,3}[.,]\\d{1,3})\\s*(?:kg|KG|Kg)\\s*[xX*]\\s*(\\d{1,4}[.,]\\d{2})(?:\\s.*)?$",
)
private val SIZE = Regex("^(\\d+(?:[.,]\\d+)?)(KG|G|GR|L|LT|ML|CL)$", RegexOption.IGNORE_CASE)

private val SIZE_UNITS: Map<String, Pair<QuantityUnit, Double>> = mapOf(
    "KG" to (QuantityUnit.KG to 1.0),
    "G" to (QuantityUnit.G to 1.0),
    "GR" to (QuantityUnit.G to 1.0),
    "L" to (QuantityUnit.L to 1.0),
    "LT" to (QuantityUnit.L to 1.0),
    "ML" to (QuantityUnit.ML to 1.0),
    "CL" to (QuantityUnit.ML to 10.0),
)

/** Da qui in giu' lo scontrino parla di pagamento, non di prodotti. */
private val END = Regex("^(?:SUB)?TOTALE\\b|^TOT(?:\\.|\\b)|\\bIMPORTO PAGATO\\b")

/** Righe con un importo che non sono prodotti. */
private val SKIP_WORDS = listOf(
    "SCONTO", "SCONTI", "RESO", "STORNO", "ABBUONO", "PROMO", "BUONO", "BUONI", "RESTO",
    "CONTANTE", "CONTANTI", "PAGAMENTO", "BANCOMAT", "CREDITO", "ELETTRONICO",
    "ARROTONDAMENTO", "IVA", "IMPONIBILE", "PUNTI", "SALDO", "TESSERA", "TICKET", "CASSA",
    "OPERATORE", "SCONTRINO", "DOCUMENTO", "SHOPPER", "SACCHETTO", "SACCHETTI", "BORSA",
)
