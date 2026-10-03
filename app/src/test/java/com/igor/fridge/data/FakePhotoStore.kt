package com.igor.fridge.data

import android.net.Uri
import com.igor.fridge.data.photos.PhotoStore
import com.igor.fridge.data.photos.VerticalCrop
import java.io.File

/**
 * Archivio di foto senza disco: [import] restituisce [nextImportName] (null simula
 * un'immagine illeggibile) e ricorda cosa e' stato chiesto.
 */
class FakePhotoStore : PhotoStore {
    var nextImportName: String? = "foto-1.jpg"
    val imported = mutableListOf<Uri>()
    var kept: Set<String>? = null

    override suspend fun import(source: Uri): String? {
        imported += source
        return nextImportName
    }

    /** null simula una foto illeggibile al momento di caricarla come prova. */
    var uploadBytes: ByteArray? = byteArrayOf(1, 2, 3)

    var uploadCrop: VerticalCrop? = null

    override suspend fun compressForUpload(source: Uri, crop: VerticalCrop): ByteArray? {
        uploadCrop = crop
        return uploadBytes
    }

    override fun fileOf(name: String): File = File("/foto", name)

    override fun newCaptureUri(): Uri = throw UnsupportedOperationException("non serve nei test")

    override suspend fun deleteAllExcept(keep: Set<String>, graceMillis: Long) {
        kept = keep
    }
}
