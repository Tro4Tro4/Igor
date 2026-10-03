package com.igor.fridge.data.photos

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.IOException

/**
 * Legge un'immagine da un Uri ridotta a [maxSidePx] sul lato lungo e raddrizzata secondo
 * l'EXIF. Non decodifica mai la risoluzione piena: una foto da 50 megapixel occuperebbe
 * centinaia di MB e chiuderebbe l'app.
 *
 * @return null se l'Uri non e' leggibile, non e' un'immagine o la memoria non basta.
 */
internal fun ContentResolver.decodeUpright(source: Uri, maxSidePx: Int): Bitmap? = try {
    decodeScaled(source, maxSidePx)?.let { rotateUpright(it, readRotation(source)) }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/** Prima solo le dimensioni, poi la decodifica a una frazione della risoluzione. */
private fun ContentResolver.decodeScaled(source: Uri, maxSidePx: Int): Bitmap? {
    // Con inJustDecodeBounds decodeStream restituisce sempre null per costruzione: le
    // dimensioni arrivano in `bounds`. Il null non va quindi letto come un errore.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val probe = openInputStream(source) ?: return null
    probe.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSidePx) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val stream = openInputStream(source) ?: return null
    val decoded = stream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

    val longSide = maxOf(decoded.width, decoded.height)
    if (longSide <= maxSidePx) return decoded
    val scale = maxSidePx.toFloat() / longSide
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled
}

/** Le fotocamere salvano spesso l'immagine coricata e indicano la rotazione nell'EXIF. */
private fun ContentResolver.readRotation(source: Uri): Int = try {
    openInputStream(source)?.use { stream ->
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
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
