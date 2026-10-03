package com.igor.fridge.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
            val bitmap = decodeScaled(source) ?: return@withContext null
            val upright = rotateUpright(bitmap, readRotation(source))
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
            if (source.authority == authority()) {
                File(captureDir, source.lastPathSegment.orEmpty()).delete()
            }
        }
    }

    override suspend fun compressForUpload(source: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeScaled(source) ?: return@withContext null
            val upright = rotateUpright(bitmap, readRotation(source))
            ByteArrayOutputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    override fun fileOf(name: String): File = File(photoDir, name)

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

    /** Legge prima solo le dimensioni, poi decodifica a una frazione della risoluzione. */
    private fun decodeScaled(source: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE_PX) {
            sampleSize *= 2
        }
        val decoded = appContext.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: return null

        val longSide = maxOf(decoded.width, decoded.height)
        if (longSide <= MAX_SIDE_PX) return decoded
        val scale = MAX_SIDE_PX.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** Le fotocamere salvano spesso l'immagine coricata e indicano la rotazione nell'EXIF. */
    private fun readRotation(source: Uri): Int = try {
        appContext.contentResolver.openInputStream(source)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: IOException) {
        0
    }

    private fun rotateUpright(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        const val PHOTO_DIR = "shopping_photos"
        private const val CAPTURE_DIR = "captures"
        private const val AUTHORITY_SUFFIX = ".photos"
        private const val MAX_SIDE_PX = 1280
        private const val JPEG_QUALITY = 85
    }
}
