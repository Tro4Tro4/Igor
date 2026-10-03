package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Un acquisto con il suo prezzo, letto da uno scontrino: la storia da cui nasce il
 * monitoraggio dei prezzi.
 *
 * [productKey] raggruppa gli acquisti dello stesso prodotto: e' il nome normalizzato
 * (minuscole, senza accenti e punteggiatura), cosi' "Caffè" e "CAFFE" sono lo stesso
 * articolo. [productName] e' il nome come l'utente l'ha confermato, da mostrare.
 *
 * [totalCents] e' quanto si e' pagato per la riga, sconti compresi. [unitPriceCents] e' il
 * prezzo per unita' di riferimento ([referenceUnit]): al kg per grammi e chili, al litro
 * per millilitri e litri, al pezzo o alla confezione altrimenti. Solo cosi' si confrontano
 * acquisti di formati diversi (500 g e 1 kg di pasta).
 */
@Entity(
    tableName = "price_records",
    indices = [Index("productKey"), Index("purchasedOn")],
)
data class PriceRecord(
    @PrimaryKey
    val uuid: String,
    val productKey: String,
    val productName: String,
    val purchasedOn: LocalDate,
    val store: String? = null,
    val quantity: Double,
    val unit: QuantityUnit,
    val totalCents: Long,
    val unitPriceCents: Long,
    val referenceUnit: QuantityUnit,
    val createdAt: Instant = Instant.now(),
)
