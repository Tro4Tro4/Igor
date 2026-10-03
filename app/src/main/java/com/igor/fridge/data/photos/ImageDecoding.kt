package com.igor.fridge.data.photos

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Legge un'immagine da un Uri ridotta a [maxSidePx] sul lato lungo e raddrizzata secondo
 * l'EXIF. Non decodifica mai la risoluzione piena: una foto da 50 megapixel occuperebbe
 * centinaia di MB e chiuderebbe l'app. Un PDF (lo scontrino digitale di molti negozi)
 * diventa un'unica immagine con le pagine una sotto l'altra.
 *
 * @return null se l'Uri non e' leggibile, non e' un'immagine o la memoria non basta.
 */
internal fun ContentResolver.decodeUpright(source: Uri, maxSidePx: Int): Bitmap? = try {
    if (isPdf(source)) {
        renderPdf(source, maxSidePx)
    } else {
        decodeScaled(source, maxSidePx)?.let { transformUpright(it, readOrientation(source)) }
    }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/** Il tipo dichiarato non basta: un file:// non ne ha, e alcune app dichiarano il generico. */
private fun ContentResolver.isPdf(source: Uri): Boolean {
    if (getType(source) == PDF_MIME_TYPE) return true
    val header = ByteArray(PDF_MAGIC.size)
    val read = openInputStream(source)?.use { it.read(header) } ?: return false
    return read == header.size && header.contentEquals(PDF_MAGIC)
}

/**
 * Le pagine una sotto l'altra, su fondo bianco (un PDF e' trasparente dove non c'e' nulla
 * e l'OCR leggerebbe nero su nero). Un PDF protetto da password non si apre: null.
 */
private fun ContentResolver.renderPdf(source: Uri, maxSidePx: Int): Bitmap? {
    val descriptor = openFileDescriptor(source, "r") ?: return null
    // Il renderer chiude il descrittore, ma solo se riesce ad aprirsi.
    val opened = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        throw e
    }
    return opened.use { renderer ->
        if (renderer.pageCount == 0) return@use null
        val pages = (0 until minOf(renderer.pageCount, MAX_PDF_PAGES)).map { index ->
            renderer.openPage(index).use { PageSize(it.width, it.height) }
        }
        val layout = stackPages(pages, maxSidePx)
        val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        // Le pagine si disegnano su bitmap separate: PdfRenderer scrive solo su tutta la
        // bitmap che riceve, e la sua matrice non basta a spostarle in giu'.
        val canvas = Canvas(bitmap)
        layout.pageHeights.forEachIndexed { index, pageHeight ->
            val page = Bitmap.createBitmap(layout.width, pageHeight, Bitmap.Config.ARGB_8888)
            page.eraseColor(Color.WHITE)
            renderer.openPage(index).use {
                it.render(page, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            canvas.drawBitmap(page, 0f, layout.pageTops[index].toFloat(), null)
            page.recycle()
        }
        bitmap
    }
}

internal data class PageSize(val width: Int, val height: Int)

/** Dove va ogni pagina nell'immagine finale; le pagine piu' strette restano a sinistra. */
internal data class PdfLayout(val width: Int, val height: Int, val pageTops: List<Int>, val pageHeights: List<Int>)

/**
 * Una scala unica per tutte le pagine, la piu' grande per cui l'insieme sta in [maxSidePx]
 * in larghezza e in altezza. Ingrandisce anche: un PDF misura le pagine in punti (72 per
 * pollice) e uno scontrino largo 8 cm sarebbe altrimenti di 226 pixel, illeggibile.
 */
internal fun stackPages(pages: List<PageSize>, maxSidePx: Int): PdfLayout {
    require(pages.isNotEmpty())
    val widest = pages.maxOf { it.width }.coerceAtLeast(1)
    val total = pages.sumOf { it.height }.coerceAtLeast(1)
    val scale = minOf(maxSidePx.toFloat() / widest, maxSidePx.toFloat() / total)
    // Arrotondare, non troncare: 842 * (1000 / 842) in virgola mobile fa 999,99...
    val heights = pages.map { (it.height * scale).roundToInt().coerceAtLeast(1) }
    val tops = heights.runningFold(0) { top, height -> top + height }
    return PdfLayout(
        width = (widest * scale).roundToInt().coerceIn(1, maxSidePx),
        height = tops.last(),
        pageTops = tops.dropLast(1),
        pageHeights = heights,
    )
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

private const val PDF_MIME_TYPE = "application/pdf"
private val PDF_MAGIC = "%PDF".toByteArray(Charsets.US_ASCII)

/** Uno scontrino non ha piu' pagine di cosi'; un documento lungo si leggerebbe minuscolo. */
private const val MAX_PDF_PAGES = 5
