package com.igor.fridge.data.openprices

import android.net.Uri
import com.igor.fridge.data.photos.PhotoStore
import com.igor.fridge.data.photos.VerticalCrop
import java.time.LocalDate

/** Un prezzo da condividere: prodotto con codice a barre, prezzo di un pezzo e pezzi comprati. */
data class ContributionItem(
    val name: String,
    val barcode: String,
    val unitPriceCents: Long,
    val quantity: Int,
)

/** [unauthorized]: il server ha rifiutato l'accesso, va ripetuto il login. */
data class ContributionResult(
    val sent: Int,
    val failed: Int,
    val lastError: String? = null,
    val unauthorized: Boolean = false,
)

/**
 * Condivide su Open Prices i prezzi di uno scontrino: prima la foto come prova, poi un
 * prezzo per prodotto riferito a quella prova, nel negozio e nella data dello scontrino
 * (il server rifiuta prezzi che non coincidono con la prova).
 */
class ReceiptContributor(
    private val client: OpenPricesClient,
    private val photoStore: PhotoStore,
) {

    /**
     * @throws OpenPricesException se la foto non si legge o la prova non si carica: in quel
     * caso nessun prezzo e' stato inviato. Gli errori dei singoli prezzi sono nel risultato.
     */
    suspend fun contribute(
        token: String,
        photo: Uri,
        location: CommunityLocation,
        date: LocalDate,
        items: List<ContributionItem>,
        receiptPriceCount: Int,
        crop: VerticalCrop = VerticalCrop(),
    ): ContributionResult {
        if (items.isEmpty()) return ContributionResult(0, 0)
        val jpeg = photoStore.compressForUpload(photo, crop)
            ?: throw OpenPricesException("Impossibile leggere la foto dello scontrino")
        val proofId = client.uploadReceipt(
            token = token,
            jpeg = jpeg,
            location = location,
            date = date,
            priceCount = receiptPriceCount,
            totalCents = null,
        )
        var sent = 0
        var failed = 0
        var lastError: String? = null
        var unauthorized = false
        for (item in items) {
            try {
                client.addPrice(
                    token = token,
                    proofId = proofId,
                    location = location,
                    date = date,
                    barcode = item.barcode,
                    priceCents = item.unitPriceCents,
                    quantity = item.quantity,
                )
                sent++
            } catch (e: OpenPricesException) {
                failed++
                lastError = e.message
                // Accesso scaduto: anche i prezzi successivi fallirebbero.
                if (e.isUnauthorized) {
                    unauthorized = true
                    failed += items.size - sent - failed
                    break
                }
            }
        }
        return ContributionResult(sent, failed, lastError, unauthorized)
    }
}
