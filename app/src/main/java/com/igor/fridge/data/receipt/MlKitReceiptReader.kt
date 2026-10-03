package com.igor.fridge.data.receipt

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.igor.fridge.data.photos.decodeUpright
import com.igor.fridge.domain.receipt.TextFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
        // Mai InputImage.fromFilePath: decodifica la foto a piena risoluzione e su una
        // foto da decine di megapixel esaurisce la memoria. Ridotta a OCR_MAX_SIDE_PX il
        // testo di uno scontrino resta leggibile.
        val bitmap = appContext.contentResolver.decodeUpright(source, OCR_MAX_SIDE_PX)
            ?: return@withContext null
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(image).await()
            text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { box ->
                    TextFragment(line.text, box.left, box.top, box.right, box.bottom)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ML Kit segnala con eccezioni generiche un'immagine che non sa leggere.
            null
        } finally {
            // Il bitmap non si ricicla qui: se la coroutine e' stata annullata ML Kit
            // potrebbe starlo ancora leggendo. Ci pensa il garbage collector.
            recognizer.close()
        }
    }

    private companion object {
        const val OCR_MAX_SIDE_PX = 3000
    }
}

/** Attende un Task di Play Services senza aggiungere una dipendenza solo per questo. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
