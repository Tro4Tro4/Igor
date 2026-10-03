package com.igor.fridge.domain.prices

import com.igor.fridge.domain.nameWords
import java.net.URLEncoder

/**
 * Una catena di supermercati. [keywords] la riconoscono nel nome stampato sullo scontrino
 * o in quello di un negozio OpenStreetMap ("ESSELUNGA S.P.A.", "Lidl Italia S.r.l.").
 * [domain] e' il sito su cui cercare un prodotto; null se la catena non ne ha uno utile.
 */
data class Chain(
    val name: String,
    val keywords: List<String>,
    val domain: String?,
)

/**
 * Le catene riconosciute. Le prime sono quelle per cui l'app offre "Cerca su...";
 * le altre servono a raggruppare gli scontrini sotto lo stesso nome.
 */
val KNOWN_CHAINS: List<Chain> = listOf(
    Chain("Esselunga", listOf("esselunga"), "esselunga.it"),
    Chain("Tigros", listOf("tigros"), "tigros.it"),
    Chain("Il Gigante", listOf("gigante"), "ilgigante.net"),
    Chain("Unes", listOf("unes", "u2"), "unes.it"),
    Chain("Lidl", listOf("lidl"), "lidl.it"),
    Chain("Eurospin", listOf("eurospin"), "eurospin.it"),
    Chain("Aldi", listOf("aldi"), "aldi.it"),
    Chain("Conad", listOf("conad"), "conad.it"),
    Chain("Carrefour", listOf("carrefour"), "carrefour.it"),
    Chain("Coop", listOf("coop", "ipercoop", "unicoop", "novacoop"), null),
    Chain("Penny", listOf("penny"), "penny.it"),
    Chain("Bennet", listOf("bennet"), "bennet.com"),
    Chain("Pam", listOf("pam", "panorama"), null),
    Chain("Iper", listOf("iper"), null),
    Chain("MD", listOf("md", "m d"), null),
    Chain("Famila", listOf("famila"), null),
    Chain("Despar", listOf("despar", "spar", "eurospar", "interspar"), null),
    Chain("Crai", listOf("crai"), null),
)

/**
 * La catena di un negozio, se il suo nome ne contiene una parola chiave. Una parola chiave
 * di piu' parole ("m d", cioe' "M.D.") vale se le parole compaiono di seguito.
 */
fun chainOf(store: String?): Chain? {
    if (store.isNullOrBlank()) return null
    val words = nameWords(store)
    val joined = words.joinToString(" ", prefix = " ", postfix = " ")
    return KNOWN_CHAINS.firstOrNull { chain ->
        chain.keywords.any { if (' ' in it) " $it " in joined else it in words }
    }
}

/**
 * Il nome con cui confrontare i negozi: quello della catena se riconosciuta ("Esselunga"
 * anche per "ESSELUNGA S.P.A. - VIA ROMA"), altrimenti il nome cosi' com'e'. Null se il
 * negozio non e' indicato: un prezzo senza negozio non si confronta.
 */
fun storeLabel(store: String?): String? =
    chainOf(store)?.name ?: store?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Indirizzo per cercare un prodotto sul sito di una catena. Passa da un motore di ricerca
 * limitato a quel sito: non dipende da come ogni catena organizza la propria ricerca
 * interna, e non legge nulla per conto dell'app (si apre nel browser).
 */
fun searchUrl(chain: Chain, product: String): String? {
    val domain = chain.domain ?: return null
    val query = URLEncoder.encode("site:$domain $product", "UTF-8")
    return "https://www.google.com/search?q=$query"
}
