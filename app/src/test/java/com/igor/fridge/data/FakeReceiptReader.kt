package com.igor.fridge.data

import android.net.Uri
import com.igor.fridge.data.receipt.ReceiptReader
import com.igor.fridge.domain.receipt.TextFragment

/** Lettore di scontrini che restituisce le righe preparate, una per frammento. */
class FakeReceiptReader : ReceiptReader {
    /** null simula una foto illeggibile. */
    var lines: List<String>? = emptyList()

    override suspend fun read(source: Uri): List<TextFragment>? =
        lines?.mapIndexed { index, text ->
            TextFragment(text, left = 0, top = index * 30, right = 400, bottom = index * 30 + 20)
        }
}
