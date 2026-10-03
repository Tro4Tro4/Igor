package com.igor.fridge.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Foto di una voce, letta da disco fuori dal thread principale e ridotta a [maxSidePx]:
 * in un elenco basta una miniatura, e decodificare la foto intera per ogni riga
 * rallenterebbe lo scorrimento. Il file di un nome non cambia mai (una foto nuova ha un
 * nome nuovo), quindi il nome basta come chiave.
 */
@Composable
fun PhotoThumbnail(
    file: File,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxSidePx: Int = 256,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.path, maxSidePx) {
        value = withContext(Dispatchers.IO) { decodeSampled(file, maxSidePx) }
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private fun decodeSampled(file: File, maxSidePx: Int): ImageBitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSidePx) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}
