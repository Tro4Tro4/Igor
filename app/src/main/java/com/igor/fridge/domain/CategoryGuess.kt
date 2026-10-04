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
 * parole come "surgelato", che cambiano la corsia qualunque sia il prodotto, e le
 * espressioni di due parole ("burro di arachidi", "te freddo"), in cui articoli e
 * preposizioni non contano.
 *
 * Se nessuna parola e' riconosciuta per intero, si prova con le abbreviazioni degli
 * scontrini: "MOZZ" e "PARMIG" sono l'inizio di una sola parola nota, e valgono quella.
 * Un'abbreviazione ambigua (inizio di parole di categorie diverse) non decide nulla.
 *
 * E' un aiuto, non un classificatore: cio' che non riconosce finisce in [FoodCategory.ALTRO]
 * e l'utente lo sposta a mano.
 */
fun guessCategory(name: String): FoodCategory {
    val words = nameWords(name)
    if (words.isEmpty()) return FoodCategory.ALTRO
    if (words.any { it in FROZEN_MARKERS }) return FoodCategory.SURGELATI

    // "fiocchi di latte" e' l'espressione "fiocchi latte": le preposizioni non contano.
    val content = words.filterNot { it in STOPWORDS }
    for (index in content.indices) {
        if (index + 1 < content.size) {
            PHRASES["${content[index]} ${content[index + 1]}"]?.let { return it }
        }
        KEYWORDS[content[index]]?.let { category ->
            // Il confezionamento cambia solo il pesce riconosciuto come prodotto principale.
            return if (content[index] in PRESERVED_FISH && words.any { it in PRESERVED_MARKERS }) {
                FoodCategory.CONSERVE_PESCE_CARNE
            } else category
        }
    }
    // "fette" decide solo se nient'altro lo fa: "fette di salmone" e' pesce.
    content.firstNotNullOfOrNull { WEAK_KEYWORDS[it] }?.let { return it }
    for (word in content) {
        if (word.length < MIN_ABBREVIATION || word in NOT_ABBREVIATIONS) continue
        val categories = KEYWORDS.filterKeys { it.startsWith(word) }.values.toSet()
        if (categories.size == 1) return categories.single()
    }
    return FoodCategory.ALTRO
}

/** Sotto questa lunghezza un inizio di parola e' troppo vago ("pa" e' pane o pasta?). */
private const val MIN_ABBREVIATION = 4

/**
 * Parole comuni nei nomi commerciali che non sono abbreviazioni: "GRAN CEREALE" non e'
 * grana, "MINI" non e' l'inizio di nulla.
 */
private val NOT_ABBREVIATIONS = setOf("gran", "mini", "maxi", "mega", "fior", "super", "extra", "light")

/** Articoli e preposizioni: non separano le due parole di un'espressione. */
private val STOPWORDS = setOf(
    "di", "d", "del", "dello", "della", "dei", "degli", "delle", "al", "allo", "alla", "ai",
    "agli", "alle", "da", "dal", "dalla", "con", "e", "ed", "per", "in", "il", "lo", "la",
    "l", "i", "gli", "le",
)

/** Le parole di un nome, normalizzate: "Caffè d'orzo" diventa [caffe, d, orzo]. */
internal fun nameWords(text: String): List<String> =
    normalize(text).split(' ').filter { it.isNotEmpty() }

/**
 * Minuscole, senza accenti e senza punteggiatura: "Caffè d'orzo" diventa "caffe d orzo".
 * Le lettere che non si scompongono in lettera e accento ("ß", "ø", il cirillico) restano:
 * toglierle svuoterebbe il nome. Per i nomi in caratteri latini il risultato e' quello di
 * sempre, e le chiavi gia' salvate continuano a valere ([com.igor.fridge.domain.prices.productKey]).
 */
private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace(NOT_WORD, " ")
        .trim()

private val MARKS = Regex("\\p{M}+")

/** Tutto cio' che non e' lettera o cifra; "ª" e "º" sono lettere solo per Unicode. */
private val NOT_WORD = Regex("(?:[^\\p{L}\\p{Nd}]|[ªº])+")

private val FROZEN_MARKERS = setOf(
    "surgelato", "surgelata", "surgelati", "surgelate", "congelato", "congelata", "congelati",
    "congelate", "surg", "surgel",
)

private val PRESERVED_FISH = setOf("tonno", "sgombro", "sardine", "acciughe")
private val PRESERVED_MARKERS = setOf("scatola", "scatole", "conserva", "conserve", "sottolio")

/** Parole che dicono poco da sole: decidono solo se nessun'altra e' riconosciuta. */
private val WEAK_KEYWORDS: Map<String, FoodCategory> = mapOf("fette" to FoodCategory.PANE)

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
    "fiocchi latte" to FoodCategory.LATTICINI,
    "crema latte" to FoodCategory.LATTICINI,
    "burro arachidi" to FoodCategory.DISPENSA,
    "burro cacao" to FoodCategory.IGIENE,
    "te freddo" to FoodCategory.BEVANDE,
    "the freddo" to FoodCategory.BEVANDE,
    "olio motore" to FoodCategory.CASA,
    "acqua ossigenata" to FoodCategory.IGIENE,
    "acqua distillata" to FoodCategory.CASA,
    "crema corpo" to FoodCategory.IGIENE,
    "crema viso" to FoodCategory.IGIENE,
    "crema mani" to FoodCategory.IGIENE,
    "crema solare" to FoodCategory.IGIENE,
    "fior latte" to FoodCategory.FORMAGGI_FRESCHI,
    "latte soia" to FoodCategory.ALTERNATIVE_VEGETALI,
    "latte avena" to FoodCategory.ALTERNATIVE_VEGETALI,
    "latte mandorla" to FoodCategory.ALTERNATIVE_VEGETALI,
    "latte mandorle" to FoodCategory.ALTERNATIVE_VEGETALI,
    "bevanda vegetale" to FoodCategory.ALTERNATIVE_VEGETALI,
    "burro arachidi" to FoodCategory.CONFETTURE_CREME,
    "fiocchi latte" to FoodCategory.FORMAGGI_FRESCHI,
    "crema latte" to FoodCategory.LATTE_PANNA,
    "panna cotta" to FoodCategory.YOGURT,
    "frutta secca" to FoodCategory.FRUTTA_SECCA,
    "pane grattugiato" to FoodCategory.FARINE_DOLCI,
    "pan grattato" to FoodCategory.FARINE_DOLCI,
    "pasta fresca" to FoodCategory.PASTA_FRESCA,
    "base pizza" to FoodCategory.PIADINE_BASI,
    "basi pizza" to FoodCategory.PIADINE_BASI,
    "te freddo" to FoodCategory.SUCCHI_BIBITE,
    "the freddo" to FoodCategory.SUCCHI_BIBITE,
    "crema spalmabile" to FoodCategory.CONFETTURE_CREME,
    "detersivo lavatrice" to FoodCategory.BUCATO,
    "sapone bucato" to FoodCategory.BUCATO,
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
        "biscottate", "cornetti", "brioche", "croissant", "pancarre", "tramezzini",
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
        "pizza", "pizze",
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
        "quinoa", "polenta", "pangrattato", "caramelle", "crema", "cereale",
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

    put(FoodCategory.ERBE_AROMATICHE, "basilico", "prezzemolo", "rosmarino", "salvia", "menta")
    put(FoodCategory.FRUTTA_SECCA, "noci", "mandorle", "nocciole", "pistacchi", "arachidi", "anacardi", "semi")
    put(FoodCategory.SALUMI, "prosciutto", "speck", "salame", "salami", "mortadella", "bresaola", "pancetta", "guanciale", "affettati", "cotto", "crudo", "porchetta", "wurstel")
    put(FoodCategory.UOVA, "uovo", "uova")
    put(FoodCategory.LATTE_PANNA, "latte", "panna")
    put(FoodCategory.YOGURT, "yogurt", "kefir", "skyr", "budino", "budini")
    put(FoodCategory.BURRO_MARGARINA, "burro", "margarina")
    put(FoodCategory.FORMAGGI_FRESCHI, "mozzarella", "mozzarelle", "ricotta", "mascarpone", "stracchino", "burrata", "philadelphia", "feta", "crescenza", "fiordilatte")
    put(FoodCategory.FORMAGGI_STAGIONATI, "parmigiano", "grana", "pecorino", "emmental", "fontina", "taleggio", "asiago", "provola", "scamorza", "gorgonzola")
    put(FoodCategory.PIADINE_BASI, "piadina", "piadine")
    put(FoodCategory.CRACKERS_GALLETTE, "crackers", "cracker", "grissini", "gallette", "taralli")
    put(FoodCategory.BISCOTTI, "biscotto", "biscotti", "frollini")
    put(FoodCategory.MERENDINE, "merendina", "merendine", "snackdolce")
    put(FoodCategory.CEREALI_COLAZIONE, "cereali", "cereale", "muesli", "granola", "cornflakes", "fiocchi")
    put(FoodCategory.CONFETTURE_CREME, "marmellata", "confettura", "miele", "nutella")
    put(FoodCategory.DOLCI, "caramelle", "cioccolato", "cioccolata", "torta", "torte", "dolci")
    put(FoodCategory.PASTA_SECCA, "pasta", "spaghetti", "penne", "fusilli", "rigatoni", "tagliatelle", "lasagne")
    put(FoodCategory.PASTA_FRESCA, "gnocchi", "ravioli", "tortellini", "cappelletti")
    put(FoodCategory.RISO_CEREALI, "riso", "orzo", "couscous", "farro", "quinoa", "polenta")
    put(FoodCategory.LEGUMI, "legumi", "fagioli", "ceci", "lenticchie")
    put(FoodCategory.CONSERVE_VEGETALI, "sottoli", "sottaceti", "olive", "capperi", "mais")
    put(FoodCategory.CONSERVE_PESCE_CARNE, "simmenthal")
    put(FoodCategory.FARINE_DOLCI, "farina", "farine", "zucchero", "lievito", "cacao", "pangrattato")
    put(FoodCategory.SUGHI_PASSATE, "passata", "pelati", "polpa", "pesto", "sugo", "sughi")
    put(FoodCategory.SALSE, "maionese", "ketchup", "senape", "salsa", "salse")
    put(FoodCategory.OLI_ACETI, "olio", "aceto")
    put(FoodCategory.SALE_SPEZIE, "sale", "pepe", "spezie", "origano", "curry", "paprika", "peperoncino", "brodo", "dado")
    put(FoodCategory.PIATTI_PRONTI, "gastronomia", "lasagna", "risotto")
    put(FoodCategory.ALTERNATIVE_VEGETALI, "tofu", "seitan", "tempeh")
    put(FoodCategory.SNACK_SALATI, "patatine", "popcorn", "salatini")
    put(FoodCategory.GELATI, "gelato", "gelati", "ghiaccioli")
    put(FoodCategory.ACQUA, "acqua")
    put(FoodCategory.SUCCHI_BIBITE, "succo", "succhi", "aranciata", "cola", "coca", "spremuta", "bibite", "bibita", "tonica", "chinotto", "gassosa", "energy")
    put(FoodCategory.CAFFE_INFUSI, "caffe", "te", "the", "camomilla", "tisana", "tisane")
    put(FoodCategory.ALCOLICI, "vino", "birra", "birre", "prosecco", "spumante", "liquore", "amaro", "grappa")
    put(FoodCategory.BUCATO, "ammorbidente", "lavatrice")
}
