package com.igor.fridge.domain

import com.igor.fridge.data.local.FoodCategory
import java.text.Normalizer
import java.util.Locale

/**
 * Propone una categoria a partire dal nome di un prodotto, per chi lo scrive per la prima
 * volta: quando quel nome e' gia' passato dall'inventario vale la categoria scelta allora,
 * e questa funzione non viene interpellata.
 *
 * Vince la prima parola riconosciuta, perche' in italiano il nome principale viene prima
 * dei complementi: "gelato al latte" e' un surgelato, "succo di mela" una bevanda,
 * "petto di pollo" carne anche se "petto" da solo non dice nulla. Fanno eccezione le
 * parole come "surgelato", che cambiano la corsia qualunque sia il prodotto.
 *
 * E' un aiuto, non un classificatore: cio' che non riconosce finisce in [FoodCategory.ALTRO]
 * e l'utente lo sposta a mano.
 */
fun guessCategory(name: String): FoodCategory {
    val words = normalize(name).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return FoodCategory.ALTRO
    if (words.any { it in FROZEN_MARKERS }) return FoodCategory.SURGELATI

    for (index in words.indices) {
        if (index + 1 < words.size) {
            PHRASES["${words[index]} ${words[index + 1]}"]?.let { return it }
        }
        KEYWORDS[words[index]]?.let { return it }
    }
    return FoodCategory.ALTRO
}

/** Minuscole, senza accenti e senza punteggiatura: "Caffè d'orzo" diventa "caffe d orzo". */
private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private val FROZEN_MARKERS = setOf(
    "surgelato", "surgelata", "surgelati", "surgelate", "congelato", "congelata", "congelati",
    "congelate",
)

private val PHRASES: Map<String, FoodCategory> = mapOf(
    "carta igienica" to FoodCategory.IGIENE,
    "carta cucina" to FoodCategory.CASA,
    "carta forno" to FoodCategory.CASA,
    "panna cotta" to FoodCategory.LATTICINI,
    "frutti bosco" to FoodCategory.FRUTTA,
    "frutta secca" to FoodCategory.DISPENSA,
    "pan carre" to FoodCategory.PANE,
    "pan grattato" to FoodCategory.DISPENSA,
    "pane grattugiato" to FoodCategory.DISPENSA,
    "fette biscottate" to FoodCategory.PANE,
    "sapone piatti" to FoodCategory.CASA,
)

private val KEYWORDS: Map<String, FoodCategory> = buildMap {
    fun put(category: FoodCategory, vararg words: String) = words.forEach { put(it, category) }

    put(
        FoodCategory.FRUTTA,
        "mela", "mele", "pera", "pere", "banana", "banane", "arancia", "arance", "mandarino",
        "mandarini", "clementine", "limone", "limoni", "uva", "fragola", "fragole", "kiwi",
        "pesca", "pesche", "albicocca", "albicocche", "ciliegie", "prugne", "susine", "ananas",
        "melone", "anguria", "cocomero", "mango", "avocado", "frutta", "mirtilli", "lamponi",
        "pompelmo", "fichi", "cachi", "melograno",
    )
    put(
        FoodCategory.VERDURA,
        "insalata", "lattuga", "rucola", "spinaci", "pomodoro", "pomodori", "pomodorini",
        "zucchina", "zucchine", "melanzana", "melanzane", "peperone", "peperoni", "carota",
        "carote", "cipolla", "cipolle", "aglio", "patata", "patate", "cetriolo", "cetrioli",
        "sedano", "finocchio", "finocchi", "broccoli", "cavolfiore", "cavolo", "verza",
        "funghi", "champignon", "zucca", "porri", "porro", "asparagi", "carciofi", "fagiolini",
        "piselli", "radicchio", "basilico", "prezzemolo", "rosmarino", "verdura", "verdure",
        "bietole", "cime", "scalogno",
    )
    put(
        FoodCategory.PANE,
        "pane", "panini", "panino", "piadina", "piadine", "focaccia", "grissini", "crackers",
        "fette", "biscottate", "cornetti", "brioche", "croissant", "pancarre", "tramezzini",
        "taralli", "friselle",
    )
    put(
        FoodCategory.CARNE,
        "pollo", "tacchino", "manzo", "vitello", "maiale", "agnello", "coniglio", "carne",
        "macinato", "salsiccia", "salsicce", "wurstel", "hamburger", "bistecca", "bistecche",
        "fettine", "cotolette", "arrosto", "prosciutto", "speck", "salame", "mortadella",
        "bresaola", "pancetta", "guanciale", "affettati", "cotto", "crudo", "porchetta",
        "polpette", "spezzatino",
    )
    put(
        FoodCategory.PESCE,
        "pesce", "tonno", "salmone", "merluzzo", "orata", "branzino", "spigola", "gamberi",
        "gamberetti", "cozze", "vongole", "calamari", "seppie", "polpo", "acciughe", "alici",
        "sgombro", "sardine", "baccala", "trota", "surimi",
    )
    put(
        FoodCategory.LATTICINI,
        "latte", "yogurt", "burro", "panna", "formaggio", "formaggi", "mozzarella",
        "mozzarelle", "parmigiano", "grana", "pecorino", "ricotta", "mascarpone",
        "stracchino", "gorgonzola", "fontina", "provola", "scamorza", "emmental", "feta",
        "burrata", "philadelphia", "uova", "uovo", "kefir", "skyr", "crescenza", "taleggio",
        "asiago",
    )
    put(
        FoodCategory.SURGELATI,
        "gelato", "gelati", "ghiaccioli", "ghiaccio", "bastoncini", "sofficini", "minestrone",
    )
    put(
        FoodCategory.DISPENSA,
        "pasta", "spaghetti", "penne", "fusilli", "rigatoni", "tagliatelle", "lasagne",
        "gnocchi", "riso", "farina", "zucchero", "biscotti", "cereali", "muesli", "fiocchi",
        "legumi", "fagioli", "ceci", "lenticchie", "passata", "pelati", "polpa", "conserva",
        "lievito", "cioccolato", "cioccolata", "nutella", "marmellata",
        "confettura", "miele", "caffe", "te", "the", "camomilla", "tisana", "orzo", "cacao",
        "mais", "noci", "mandorle", "nocciole", "pistacchi", "arachidi",
        "patatine", "merendine", "brodo", "dado", "couscous", "farro",
        "quinoa", "polenta", "pangrattato",
    )
    put(
        FoodCategory.CONDIMENTI,
        "olio", "aceto", "sale", "pepe", "maionese", "ketchup", "senape", "pesto", "sugo",
        "salsa", "spezie", "origano", "curry", "paprika", "peperoncino", "capperi", "olive",
        "sottaceti", "sottoli",
    )
    put(
        FoodCategory.BEVANDE,
        "acqua", "vino", "birra", "birre", "succo", "succhi", "aranciata", "cola",
        "coca", "spremuta", "bibite", "bibita", "tonica", "chinotto", "gassosa", "prosecco",
        "spumante", "liquore", "amaro", "grappa", "energy",
    )
    put(
        FoodCategory.CASA,
        "detersivo", "detersivi", "ammorbidente", "candeggina", "sgrassatore", "anticalcare",
        "spugne", "spugna", "sacchetti", "sacchi", "pellicola", "alluminio", "tovaglioli",
        "scottex", "lavastoviglie", "pastiglie", "brillantante", "lampadina", "lampadine",
        "pile", "batterie", "stracci", "panno", "panni", "candele", "fiammiferi",
    )
    put(
        FoodCategory.IGIENE,
        "shampoo", "balsamo", "bagnoschiuma", "docciaschiuma", "sapone", "saponetta",
        "dentifricio", "spazzolino", "spazzolini", "collutorio", "deodorante", "assorbenti",
        "pannolini", "salviette", "cotone", "cotton", "rasoio", "rasoi", "lamette",
        "fazzoletti", "cerotti",
    )
}
