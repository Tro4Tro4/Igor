package com.igor.fridge.data.photos

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Legge un'immagine da un Uri ridotta a [maxSidePx] sul lato lungo e raddrizzata secondo
 * l'EXIF. Non decodifica mai la risoluzione piena: una foto da 50 megapixel occuperebbe
 * centinaia di MB e chiuderebbe l'app.
 *
 * @return null se l'Uri non e' leggibile, non e' un'immagine o la memoria non basta.
 */
internal fun ContentResolver.decodeUpright(source: Uri, maxSidePx: Int): Bitmap? = try {
    decodeScaled(source, maxSidePx)?.let { transformUpright(it, readOrientation(source)) }
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

/**
 * Le fotocamere salvano spesso l'immagine coricata (o specchiata, con la fotocamera
 * frontale) e indicano nell'EXIF come raddrizzarla.
 */
private fun ContentResolver.readOrientation(source: Uri): Int = try {
    openInputStream(source)?.use { stream ->
        ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    } ?: ExifInterface.ORIENTATION_NORMAL
} catch (e: IOException) {
    ExifInterface.ORIENTATION_NORMAL
}

/** Le otto orientazioni EXIF: quattro rotazioni, ciascuna anche specchiata. */
private fun transformUpright(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (transformed !== bitmap) bitmap.recycle()
    return transformed
}

/** Toglie le strisce indicate da [crop]; l'originale viene liberato. */
internal fun cropVertical(bitmap: Bitmap, crop: VerticalCrop): Bitmap {
    if (crop.isNone) return bitmap
    // Le strisce si arrotondano ai pixel una per una: 1000 * (1 - 0,1 - 0,3) in virgola
    // mobile fa 599,99..., e troncandolo si perderebbe una riga.
    val top = (bitmap.height * crop.top).roundToInt()
    val bottom = (bitmap.height * crop.bottom).roundToInt()
    val height = (bitmap.height - top - bottom).coerceIn(1, bitmap.height - top)
    val cropped = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
    if (cropped !== bitmap) bitmap.recycle()
    return cropped
}
