package com.igor.fridge.data.receipt

import android.net.Uri
import com.igor.fridge.domain.receipt.TextFragment

/** Legge il testo di una foto di scontrino. */
interface ReceiptReader {

    /** @return i frammenti di testo riconosciuti, oppure null se l'immagine non e' leggibile. */
    suspend fun read(source: Uri): List<TextFragment>?
}
