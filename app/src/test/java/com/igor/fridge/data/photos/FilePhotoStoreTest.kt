package com.igor.fridge.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Decodifica vera (grafica nativa di Robolectric): e' il test che sarebbe mancato quando
 * la lettura delle sole dimensioni veniva scambiata per un errore e nessuna foto passava.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilePhotoStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = FilePhotoStore(context)

    private fun image(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val file = File(context.cacheDir, "sorgente-$width-$height.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun `una foto si importa ridotta a 1280 px sul lato lungo`() = runTest {
        val name = store.import(image(3000, 2000))

        assertNotNull(name)
        val saved = store.fileOf(name!!)
        assertTrue(saved.exists())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(saved.path, bounds)
        assertEquals(1280, bounds.outWidth)
        assertEquals(853, bounds.outHeight)
    }

    @Test
    fun `una foto piccola resta com'e'`() = runTest {
        val name = store.import(image(400, 300))!!

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(store.fileOf(name).path, bounds)
        assertEquals(400, bounds.outWidth)
    }

    @Test
    fun `la prova per Open Prices e' un JPEG`() = runTest {
        val bytes = store.compressForUpload(image(2000, 3000))

        assertNotNull(bytes)
        assertEquals(0xFF.toByte(), bytes!![0])
        assertEquals(0xD8.toByte(), bytes[1])
    }

    @Test
    fun `il ritaglio toglie le strisce in alto e in basso`() = runTest {
        val bytes = store.compressForUpload(image(800, 1000), VerticalCrop(top = 0.1f, bottom = 0.3f))!!

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertEquals(800, bounds.outWidth)
        assertEquals(600, bounds.outHeight)
    }

    @Test
    fun `un nome con un percorso non esce dalla cartella delle foto`() {
        assertEquals(store.fileOf("a.jpg").parentFile, store.fileOf("../../a.jpg").parentFile)
    }

    @Test
    fun `un file che non e' un'immagine non si importa`() = runTest {
        val file = File(context.cacheDir, "non-immagine.png").apply { writeText("ciao") }

        assertNull(store.import(Uri.fromFile(file)))
        assertNull(store.compressForUpload(Uri.fromFile(file)))
    }
}
