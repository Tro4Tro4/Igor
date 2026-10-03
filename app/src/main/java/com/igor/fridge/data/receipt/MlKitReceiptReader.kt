package com.igor.fridge.data.receipt

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.igor.fridge.domain.receipt.TextFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Riconoscimento del testo con ML Kit, modello incluso nell'app: funziona senza rete e lo
 * scontrino non lascia il telefono. Restituisce le singole righe di testo con la loro
 * posizione, perche' la ricomposizione delle righe dello scontrino avviene altrove.
 */
class MlKitReceiptReader(context: Context) : ReceiptReader {

    private val appContext = context.applicationContext

    override suspend fun read(source: Uri): List<TextFragment>? = withContext(Dispatchers.IO) {
        val image = try {
            InputImage.fromFilePath(appContext, source)
        } catch (e: IOException) {
            return@withContext null
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(image).await()
            text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { box ->
                    TextFragment(line.text, box.left, box.top, box.right, box.bottom)
                }
            }
        } catch (e: Exception) {
            // ML Kit segnala con eccezioni generiche un'immagine che non sa leggere.
            null
        } finally {
            recognizer.close()
        }
    }
}

/** Attende un Task di Play Services senza aggiungere una dipendenza solo per questo. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
