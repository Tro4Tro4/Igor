package com.igor.fridge.data.local

/** Categoria merceologica dell'alimento, usata per filtri e icone. */
enum class FoodCategory {
    LATTICINI,
    CARNE,
    PESCE,
    FRUTTA,
    VERDURA,
    BEVANDE,
    CONDIMENTI,
    SURGELATI,
    DISPENSA,
    ALTRO,
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
