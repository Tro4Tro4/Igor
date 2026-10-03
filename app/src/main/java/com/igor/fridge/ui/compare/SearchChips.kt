package com.igor.fridge.ui.compare

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.igor.fridge.R
import com.igor.fridge.domain.prices.KNOWN_CHAINS
import com.igor.fridge.domain.prices.searchUrl

/**
 * "Cerca su Esselunga, Tigros...": apre nel browser la ricerca del prodotto sul sito della
 * catena. L'app non legge quelle pagine: il prezzo lo guarda l'utente, sul sito della catena.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchOnChains(product: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KNOWN_CHAINS.forEach { chain ->
            val url = searchUrl(chain, product) ?: return@forEach
            AssistChip(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: ActivityNotFoundException) {
                        // Nessun browser: non c'e' niente di utile da fare.
                    }
                },
                label = { Text(stringResource(R.string.compare_search_on, chain.name)) },
            )
        }
    }
}
