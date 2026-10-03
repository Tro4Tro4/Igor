package com.igor.fridge.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Foto salvate nella memoria privata dell'app (`files/shopping_photos`), incluse nel
 * backup. Ogni immagine viene ridotta a [MAX_SIDE_PX] sul lato lungo: per riconoscere una
 * confezione basta, e una foto da 12 megapixel per ogni voce riempirebbe il telefono.
 */
class FilePhotoStore(context: Context) : PhotoStore {

    private val appContext = context.applicationContext
    private val photoDir = File(appContext.filesDir, PHOTO_DIR)
    private val captureDir = File(appContext.cacheDir, CAPTURE_DIR)

    override suspend fun import(source: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val upright = appContext.contentResolver.decodeUpright(source, MAX_SIDE_PX)
                ?: return@withContext null
            photoDir.mkdirs()
            val name = "${UUID.randomUUID()}.jpg"
            File(photoDir, name).outputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            name
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } finally {
            // Lo scatto della fotocamera e' solo un passaggio: l'immagine buona e' la copia.
            // Solo il nome del file: un segmento con "../" non deve uscire dalla cartella.
            if (source.authority == authority()) {
                File(captureDir, File(source.lastPathSegment.orEmpty()).name).delete()
            }
        }
    }

    override suspend fun compressForUpload(
        source: Uri,
        crop: VerticalCrop,
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val upright = appContext.contentResolver.decodeUpright(source, MAX_SIDE_PX)
                ?: return@withContext null
            val cropped = cropVertical(upright, crop)
            ByteArrayOutputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** I nomi arrivano dal database: si tiene solo il nome, mai un percorso. */
    override fun fileOf(name: String): File = File(photoDir, File(name).name)

    override fun newCaptureUri(): Uri {
        captureDir.mkdirs()
        val file = File(captureDir, "${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(appContext, authority(), file)
    }

    override suspend fun deleteAllExcept(keep: Set<String>, graceMillis: Long) =
        withContext(Dispatchers.IO) {
            val threshold = System.currentTimeMillis() - graceMillis
            photoDir.listFiles()
                ?.filter { it.name !in keep && it.lastModified() < threshold }
                ?.forEach { it.delete() }
            captureDir.listFiles()
                ?.filter { it.lastModified() < threshold }
                ?.forEach { it.delete() }
            Unit
        }

    private fun authority(): String = "${appContext.packageName}$AUTHORITY_SUFFIX"

    companion object {
        const val PHOTO_DIR = "shopping_photos"
        private const val CAPTURE_DIR = "captures"
        private const val AUTHORITY_SUFFIX = ".photos"
        private const val MAX_SIDE_PX = 1280
        private const val JPEG_QUALITY = 85
    }
}
