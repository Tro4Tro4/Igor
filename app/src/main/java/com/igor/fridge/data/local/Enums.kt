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
