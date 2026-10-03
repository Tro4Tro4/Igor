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
    PANE,
    CARNE,
    PESCE,
    LATTICINI,
    SURGELATI,
    DISPENSA,
    CONDIMENTI,
    BEVANDE,
    CASA(isFood = false),
    IGIENE(isFood = false),
    ALTRO,
    ;

    /** Dove finisce di solito un prodotto appena comprato di questa categoria. */
    val defaultLocation: StorageLocation
        get() = when (this) {
            SURGELATI -> StorageLocation.FREEZER
            PANE, DISPENSA, CONDIMENTI, BEVANDE, CASA, IGIENE -> StorageLocation.DISPENSA
            else -> StorageLocation.FRIGO
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
