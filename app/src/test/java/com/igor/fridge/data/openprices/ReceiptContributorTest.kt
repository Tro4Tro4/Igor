package com.igor.fridge.data.openprices

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.FakePhotoStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Robolectric solo per costruire degli Uri veri. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReceiptContributorTest {

    private val transport = FakeTransport()
    private val photos = FakePhotoStore()
    private val contributor = ReceiptContributor(OpenPricesClient(transport, boundary = { "B" }), photos)
    private val shop = CommunityLocation(osmId = 1, osmType = "WAY", name = "Lidl")
    private val day = LocalDate.of(2026, 10, 1)
    private val items = listOf(
        ContributionItem("Latte", "8001234567890", 129, 1),
        ContributionItem("Yogurt", "4006381333931", 45, 4),
    )

    @Test
    fun `prima la prova, poi un prezzo per prodotto riferito a lei`() = runTest {
        transport.respond("POST", "/proofs/upload", HttpResponse(201, """{"id":7}"""))
        transport.respond("POST", "/prices", HttpResponse(201, """{"id":1}"""))

        val result = contributor.contribute("tok", Uri.parse("content://s"), shop, day, items, receiptPriceCount = 5)

        assertEquals(ContributionResult(sent = 2, failed = 0), result)
        assertEquals(listOf("/proofs/upload", "/prices", "/prices"), transport.requests.map { it.url.substringAfter("/api/v1").substringBefore('?') })
        assertTrue(transport.requests[2].bodyText!!.contains("\"proof_id\":7"))
        assertTrue(transport.requests[2].bodyText!!.contains("\"receipt_quantity\":4"))
    }

    @Test
    fun `un prezzo rifiutato non ferma gli altri, un accesso scaduto si'`() = runTest {
        transport.respond("POST", "/proofs/upload", HttpResponse(201, """{"id":7}"""))
        transport.respond("POST", "/prices", HttpResponse(400, """{"product_code":["Invalid"]}"""), HttpResponse(201, """{"id":2}"""))

        val partial = contributor.contribute("tok", Uri.parse("content://s"), shop, day, items, 2)
        assertEquals(1, partial.sent)
        assertEquals(1, partial.failed)
        assertEquals("product_code: Invalid", partial.lastError)

        val expired = FakeTransport().apply {
            respond("POST", "/proofs/upload", HttpResponse(201, """{"id":7}"""))
            respond("POST", "/prices", HttpResponse(401, """{"detail":"Invalid token"}"""))
        }
        val result = ReceiptContributor(OpenPricesClient(expired), photos)
            .contribute("tok", Uri.parse("content://s"), shop, day, items, 2)
        assertEquals(ContributionResult(sent = 0, failed = 2, lastError = "Invalid token"), result)
        assertEquals(2, expired.requests.size)
    }

    @Test
    fun `senza foto leggibile non si invia nulla`() = runTest {
        photos.uploadBytes = null
        try {
            contributor.contribute("tok", Uri.parse("content://s"), shop, day, items, 2)
            fail("doveva fallire")
        } catch (e: OpenPricesException) {
            assertEquals("Impossibile leggere la foto dello scontrino", e.message)
        }
        assertTrue(transport.requests.isEmpty())
    }
}
