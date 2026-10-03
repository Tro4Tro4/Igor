package com.igor.fridge.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptMetaTest {

    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun `il negozio e' l'insegna in testa, la data quella accanto all'ora`() {
        val meta = parseReceiptMeta(
            listOf(
                "DOCUMENTO COMMERCIALE",
                "ESSELUNGA S.P.A.",
                "VIA ROMA 12 MILANO",
                "P.IVA 01234567890",
                "LATTE  1,29",
                "TOTALE  1,29",
                "03-10-2026 18:22",
            ),
            today,
        )

        assertEquals("Esselunga S.p.a.", meta.store)
        assertEquals(LocalDate.of(2026, 10, 3), meta.date)
    }

    @Test
    fun `anni a due cifre e separatori diversi`() {
        assertEquals(LocalDate.of(2026, 9, 5), parseReceiptMeta(listOf("05.09.26 10:00"), today).date)
        assertEquals(LocalDate.of(2026, 9, 5), parseReceiptMeta(listOf("5/9/2026"), today).date)
    }

    @Test
    fun `date impossibili o nel futuro non valgono`() {
        assertNull(parseReceiptMeta(listOf("31/02/2026", "10/12/2026"), today).date)
        assertEquals(
            LocalDate.of(2026, 9, 1),
            parseReceiptMeta(listOf("99/99/2026", "01/09/2026"), today).date,
        )
    }

    @Test
    fun `senza intestazione il negozio resta da chiedere`() {
        assertNull(parseReceiptMeta(listOf("LATTE  1,29", "PANE  0,90"), today).store)
    }

    @Test
    fun `vale la data accanto all'ora, altrimenti l'ultima`() {
        assertEquals(
            LocalDate.of(2026, 10, 3),
            parseReceiptMeta(listOf("LOTTO 1.2.25", "LATTE  1,29", "03/10/2026"), today).date,
        )
        assertEquals(
            LocalDate.of(2026, 9, 30),
            parseReceiptMeta(listOf("SCONTRINO N. 1-10-26", "30/09/2026 18:22", "SCAD. 2/10/26"), today).date,
        )
    }

    @Test
    fun `date ISO`() {
        assertEquals(LocalDate.of(2026, 9, 28), parseReceiptMeta(listOf("2026-09-28 10:15"), today).date)
    }

    @Test
    fun `l'insegna di una catena nota vince su una riga generica`() {
        assertEquals("Coop Lombardia", parseReceiptMeta(listOf("Supermercato", "COOP LOMBARDIA"), today).store)
    }

    @Test
    fun `CAP, indirizzi e date non sono il negozio`() {
        assertEquals(
            "Bottega Rossi",
            parseReceiptMeta(
                listOf("20121 MILANO MI", "C.SO BUENOS AIRES 3", "Data 3/10/2026", "BOTTEGA ROSSI"),
                today,
            ).store,
        )
    }
}
