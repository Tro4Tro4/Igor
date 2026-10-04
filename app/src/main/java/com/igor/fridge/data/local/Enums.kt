package com.igor.fridge.data.local

/**
 * Categoria merceologica, usata per filtri, icone e per raggruppare la lista della spesa.
 *
 * L'ordine delle voci e' quello delle corsie di un supermercato tipico: la lista della
 * spesa raggruppa le voci in quest'ordine, cosi' si percorre il negozio una volta sola.
 * Il database salva il nome, non la posizione, quindi riordinare non tocca i dati.
 *
 * [isFood] separa cio' che entra in frigo da cio' che si compra soltanto: un detersivo
 * spuntato esce dalla lista ma non diventa un alimento dell'inventario.
 */
enum class FoodCategory(val isFood: Boolean = true) {
    FRUTTA,
    VERDURA,
    ERBE_AROMATICHE,
    FRUTTA_SECCA,
    CARNE,
    SALUMI,
    PESCE,
    UOVA,
    LATTE_PANNA,
    YOGURT,
    BURRO_MARGARINA,
    FORMAGGI_FRESCHI,
    FORMAGGI_STAGIONATI,
    LATTICINI,
    PANE,
    PIADINE_BASI,
    CRACKERS_GALLETTE,
    BISCOTTI,
    MERENDINE,
    CEREALI_COLAZIONE,
    CONFETTURE_CREME,
    DOLCI,
    PASTA_SECCA,
    PASTA_FRESCA,
    RISO_CEREALI,
    LEGUMI,
    CONSERVE_VEGETALI,
    CONSERVE_PESCE_CARNE,
    FARINE_DOLCI,
    DISPENSA,
    SUGHI_PASSATE,
    SALSE,
    OLI_ACETI,
    SALE_SPEZIE,
    CONDIMENTI,
    PIATTI_PRONTI,
    ALTERNATIVE_VEGETALI,
    SNACK_SALATI,
    SURGELATI,
    GELATI,
    ACQUA,
    SUCCHI_BIBITE,
    CAFFE_INFUSI,
    ALCOLICI,
    BEVANDE,
    CASA(isFood = false),
    BUCATO(isFood = false),
    IGIENE(isFood = false),
    ALTRO,
    ;

    val isPerishable: Boolean
        get() = when (this) {
            FRUTTA, VERDURA, ERBE_AROMATICHE, CARNE, SALUMI, PESCE, UOVA, LATTE_PANNA, YOGURT, BURRO_MARGARINA, FORMAGGI_FRESCHI, FORMAGGI_STAGIONATI, LATTICINI, PANE, PASTA_FRESCA, PIATTI_PRONTI, ALTERNATIVE_VEGETALI -> true
            else -> false
        }

    val isSoldByWeight: Boolean
        get() = when (this) {
            FRUTTA, VERDURA, ERBE_AROMATICHE, CARNE, PESCE, SALUMI,
            FORMAGGI_FRESCHI, FORMAGGI_STAGIONATI -> true
            else -> false
        }

    /** Suggerimento per il calendario: mai una scadenza assegnata senza conferma. */
    val typicalShelfLifeDays: Long
        get() = when (this) {
            FRUTTA, UOVA, LATTE_PANNA, YOGURT, BURRO_MARGARINA, FORMAGGI_FRESCHI, FORMAGGI_STAGIONATI, LATTICINI, ALTERNATIVE_VEGETALI -> 7L
            VERDURA, ERBE_AROMATICHE -> 5L
            FRUTTA_SECCA, PIADINE_BASI, CRACKERS_GALLETTE, BISCOTTI, MERENDINE, CEREALI_COLAZIONE, CONFETTURE_CREME, DOLCI, PASTA_SECCA, RISO_CEREALI, LEGUMI, CONSERVE_VEGETALI, CONSERVE_PESCE_CARNE, FARINE_DOLCI, DISPENSA, SUGHI_PASSATE, SALSE, OLI_ACETI, SALE_SPEZIE, CONDIMENTI, SNACK_SALATI, ACQUA, SUCCHI_BIBITE, CAFFE_INFUSI, ALCOLICI, BEVANDE, CASA, BUCATO, IGIENE, ALTRO -> 30L
            CARNE, SALUMI, PANE, PASTA_FRESCA, PIATTI_PRONTI -> 3L
            PESCE -> 2L
            SURGELATI, GELATI -> 90L
        }

    val defaultLocation: StorageLocation
        get() = when (this) {
            SURGELATI, GELATI -> StorageLocation.FREEZER
            FRUTTA, VERDURA, ERBE_AROMATICHE, CARNE, SALUMI, PESCE, UOVA, LATTE_PANNA, YOGURT, BURRO_MARGARINA, FORMAGGI_FRESCHI, FORMAGGI_STAGIONATI, LATTICINI, PASTA_FRESCA, PIATTI_PRONTI, ALTERNATIVE_VEGETALI, ALTRO -> StorageLocation.FRIGO
            else -> StorageLocation.DISPENSA
        }
}

/** Dove e' conservato l'alimento. */
enum class StorageLocation {
    FRIGO,
    FREEZER,
    DISPENSA,
}

/** Unita' di misura della quantita'. */
enum class QuantityUnit {
    PZ,
    G,
    KG,
    ML,
    L,
    CONF,
}

/**
 * Perche' un alimento e' uscito dall'inventario. La distinzione fra consumato e buttato
 * e' l'unica informazione che rende sensata una statistica sugli sprechi; ERRORE marca
 * le rimozioni annullabili dall'utente.
 */
enum class RemovalReason {
    CONSUMATO,
    BUTTATO,
    ERRORE,
}
