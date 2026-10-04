package com.igor.fridge.ui

import androidx.annotation.StringRes
import com.igor.fridge.R
import com.igor.fridge.data.local.FoodCategory

@StringRes
fun FoodCategory.labelRes(): Int = when (this) {
    FoodCategory.FRUTTA -> R.string.category_frutta
    FoodCategory.VERDURA -> R.string.category_verdura
    FoodCategory.ERBE_AROMATICHE -> R.string.category_erbe_aromatiche
    FoodCategory.FRUTTA_SECCA -> R.string.category_frutta_secca
    FoodCategory.CARNE -> R.string.category_carne
    FoodCategory.SALUMI -> R.string.category_salumi
    FoodCategory.PESCE -> R.string.category_pesce
    FoodCategory.UOVA -> R.string.category_uova
    FoodCategory.LATTE_PANNA -> R.string.category_latte_panna
    FoodCategory.YOGURT -> R.string.category_yogurt
    FoodCategory.BURRO_MARGARINA -> R.string.category_burro_margarina
    FoodCategory.FORMAGGI_FRESCHI -> R.string.category_formaggi_freschi
    FoodCategory.FORMAGGI_STAGIONATI -> R.string.category_formaggi_stagionati
    FoodCategory.LATTICINI -> R.string.category_latticini
    FoodCategory.PANE -> R.string.category_pane
    FoodCategory.PIADINE_BASI -> R.string.category_piadine_basi
    FoodCategory.CRACKERS_GALLETTE -> R.string.category_crackers_gallette
    FoodCategory.BISCOTTI -> R.string.category_biscotti
    FoodCategory.MERENDINE -> R.string.category_merendine
    FoodCategory.CEREALI_COLAZIONE -> R.string.category_cereali_colazione
    FoodCategory.CONFETTURE_CREME -> R.string.category_confetture_creme
    FoodCategory.DOLCI -> R.string.category_dolci
    FoodCategory.PASTA_SECCA -> R.string.category_pasta_secca
    FoodCategory.PASTA_FRESCA -> R.string.category_pasta_fresca
    FoodCategory.RISO_CEREALI -> R.string.category_riso_cereali
    FoodCategory.LEGUMI -> R.string.category_legumi
    FoodCategory.CONSERVE_VEGETALI -> R.string.category_conserve_vegetali
    FoodCategory.CONSERVE_PESCE_CARNE -> R.string.category_conserve_pesce_carne
    FoodCategory.FARINE_DOLCI -> R.string.category_farine_dolci
    FoodCategory.DISPENSA -> R.string.category_dispensa
    FoodCategory.SUGHI_PASSATE -> R.string.category_sughi_passate
    FoodCategory.SALSE -> R.string.category_salse
    FoodCategory.OLI_ACETI -> R.string.category_oli_aceti
    FoodCategory.SALE_SPEZIE -> R.string.category_sale_spezie
    FoodCategory.CONDIMENTI -> R.string.category_condimenti
    FoodCategory.PIATTI_PRONTI -> R.string.category_piatti_pronti
    FoodCategory.ALTERNATIVE_VEGETALI -> R.string.category_alternative_vegetali
    FoodCategory.SNACK_SALATI -> R.string.category_snack_salati
    FoodCategory.SURGELATI -> R.string.category_surgelati
    FoodCategory.GELATI -> R.string.category_gelati
    FoodCategory.ACQUA -> R.string.category_acqua
    FoodCategory.SUCCHI_BIBITE -> R.string.category_succhi_bibite
    FoodCategory.CAFFE_INFUSI -> R.string.category_caffe_infusi
    FoodCategory.ALCOLICI -> R.string.category_alcolici
    FoodCategory.BEVANDE -> R.string.category_bevande
    FoodCategory.CASA -> R.string.category_casa
    FoodCategory.BUCATO -> R.string.category_bucato
    FoodCategory.IGIENE -> R.string.category_igiene
    FoodCategory.ALTRO -> R.string.category_altro
}

/** Emoji della famiglia: sempre accompagnata dall'etichetta, decorativa per TalkBack. */
fun FoodCategory.icon(): String = when (this) {
    FoodCategory.FRUTTA -> "\uD83C\uDF4E"
    FoodCategory.VERDURA -> "\uD83E\uDD66"
    FoodCategory.ERBE_AROMATICHE -> "\uD83E\uDD66"
    FoodCategory.FRUTTA_SECCA -> "\uD83E\uDD6B"
    FoodCategory.CARNE -> "\uD83E\uDD69"
    FoodCategory.SALUMI -> "\uD83E\uDD69"
    FoodCategory.PESCE -> "\uD83D\uDC1F"
    FoodCategory.UOVA -> "\uD83E\uDD5B"
    FoodCategory.LATTE_PANNA -> "\uD83E\uDD5B"
    FoodCategory.YOGURT -> "\uD83E\uDD5B"
    FoodCategory.BURRO_MARGARINA -> "\uD83E\uDD5B"
    FoodCategory.FORMAGGI_FRESCHI -> "\uD83E\uDDC0"
    FoodCategory.FORMAGGI_STAGIONATI -> "\uD83E\uDDC0"
    FoodCategory.LATTICINI -> "\uD83E\uDD5B"
    FoodCategory.PANE -> "\uD83C\uDF5E"
    FoodCategory.PIADINE_BASI -> "\uD83E\uDD6B"
    FoodCategory.CRACKERS_GALLETTE -> "\uD83E\uDD6B"
    FoodCategory.BISCOTTI -> "\uD83E\uDD6B"
    FoodCategory.MERENDINE -> "\uD83E\uDD6B"
    FoodCategory.CEREALI_COLAZIONE -> "\uD83E\uDD6B"
    FoodCategory.CONFETTURE_CREME -> "\uD83E\uDD6B"
    FoodCategory.DOLCI -> "\uD83E\uDD6B"
    FoodCategory.PASTA_SECCA -> "\uD83E\uDD6B"
    FoodCategory.PASTA_FRESCA -> "\uD83E\uDD69"
    FoodCategory.RISO_CEREALI -> "\uD83E\uDD6B"
    FoodCategory.LEGUMI -> "\uD83E\uDD6B"
    FoodCategory.CONSERVE_VEGETALI -> "\uD83E\uDD6B"
    FoodCategory.CONSERVE_PESCE_CARNE -> "\uD83E\uDD6B"
    FoodCategory.FARINE_DOLCI -> "\uD83E\uDD6B"
    FoodCategory.DISPENSA -> "\uD83E\uDD6B"
    FoodCategory.SUGHI_PASSATE -> "\uD83E\uDD6B"
    FoodCategory.SALSE -> "\uD83E\uDD6B"
    FoodCategory.OLI_ACETI -> "\uD83E\uDD6B"
    FoodCategory.SALE_SPEZIE -> "\uD83E\uDD6B"
    FoodCategory.CONDIMENTI -> "\uD83E\uDD6B"
    FoodCategory.PIATTI_PRONTI -> "\uD83E\uDD69"
    FoodCategory.ALTERNATIVE_VEGETALI -> "\uD83E\uDD5B"
    FoodCategory.SNACK_SALATI -> "\uD83E\uDD6B"
    FoodCategory.SURGELATI -> "\u2744\uFE0F"
    FoodCategory.GELATI -> "\u2744\uFE0F"
    FoodCategory.ACQUA -> "\uD83E\uDD6B"
    FoodCategory.SUCCHI_BIBITE -> "\uD83E\uDD6B"
    FoodCategory.CAFFE_INFUSI -> "\uD83E\uDD6B"
    FoodCategory.ALCOLICI -> "\uD83E\uDD6B"
    FoodCategory.BEVANDE -> "\uD83E\uDD6B"
    FoodCategory.CASA -> "\uD83C\uDFE0"
    FoodCategory.BUCATO -> "\uD83C\uDFE0"
    FoodCategory.IGIENE -> "\uD83D\uDEC1"
    FoodCategory.ALTRO -> "\uD83D\uDED2"
}
