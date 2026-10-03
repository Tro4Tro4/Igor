package com.igor.fridge.data.photos

import android.net.Uri
import java.io.File

/**
 * Le foto delle voci della spesa. Le voci ne conservano solo il nome del file: la
 * cartella che le contiene appartiene all'app e cambia fra un'installazione e l'altra.
 */
interface PhotoStore {

    /**
     * Copia l'immagine nella cartella dell'app, ridotta e raddrizzata.
     * @return il nome del file creato, oppure null se l'immagine non e' leggibile.
     */
    suspend fun import(source: Uri): String?

    /**
     * L'immagine ridotta e raddrizzata come JPEG in memoria, senza salvarla: serve a
     * caricare la foto di uno scontrino come prova su Open Prices. [crop] toglie strisce
     * in alto e in basso, dove gli scontrini stampano carta fedelta' e pagamento.
     * @return null se l'immagine non e' leggibile.
     */
    suspend fun compressForUpload(source: Uri, crop: VerticalCrop = VerticalCrop()): ByteArray?

    /** Il file corrispondente a un nome restituito da [import]. */
    fun fileOf(name: String): File

    /** Un Uri in cui la fotocamera di sistema puo' scrivere uno scatto da importare. */
    fun newCaptureUri(): Uri

    /**
     * Cancella le foto che nessuno usa piu' (voci eliminate, foto sostituite), tranne
     * quelle piu' recenti di [graceMillis]: una foto appena scelta in una schermata non
     * ancora salvata non e' orfana, e' in attesa.
     */
    suspend fun deleteAllExcept(keep: Set<String>, graceMillis: Long)
}

/**
 * Quanto togliere dall'alto e dal basso di un'immagine, in frazioni dell'altezza (0..1).
 * Cio' che resta e' sempre almeno [MIN_KEPT] dell'immagine.
 */
data class VerticalCrop(val top: Float = 0f, val bottom: Float = 0f) {
    init {
        require(top >= 0f && bottom >= 0f && 1f - top - bottom >= MIN_KEPT - 1e-4f) {
            "Ritaglio non valido: $top, $bottom"
        }
    }

    val isNone: Boolean get() = top == 0f && bottom == 0f

    companion object {
        const val MIN_KEPT = 0.2f
    }
}
