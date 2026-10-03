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
     * caricare la foto di uno scontrino come prova su Open Prices.
     * @return null se l'immagine non e' leggibile.
     */
    suspend fun compressForUpload(source: Uri): ByteArray?

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
