package com.igor.fridge.domain.receipt

import com.igor.fridge.data.local.QuantityUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    /** Uno scontrino tipico, con intestazione, dettagli sotto il prodotto, sconto e totale. */
    private val receipt = """
        SUPERMERCATO ESEMPIO S.P.A.
        VIA ROMA 12 - 20100 MILANO
        P.IVA 01234567890
        DOCUMENTO COMMERCIALE
        di vendita o prestazione
        DESCRIZIONE              IVA    Prezzo(€)
        LATTE PS GRANAROLO 1L    4%     1,29
        YOGURT BIANCO            10%    1,78
        2 x 0,89
        BANANE                   10%    1,45
        0,725 kg x 2,00 €/kg
        PASTA DE CECCO 500G      4%     1,15
        SCONTO PASTA                   -0,20
        MOZZ. FIOR DI LATTE      4%     0,99
        DETERSIVO PIATTI         22%    2,49
        SUBTOTALE                       9,15
        TOTALE COMPLESSIVO              8,95
        DI CUI IVA                      0,61
        PAGAMENTO ELETTRONICO           8,95
        03-10-2026 18:22  DOC.N. 0042-0117
    """.trimIndent().lines()

    @Test
    fun `legge i prodotti e ignora intestazione, sconti e totali`() {
        val names = parseReceipt(receipt).map { it.name }

        assertEquals(
            listOf("Latte ps granarolo", "Yogurt bianco", "Banane", "Pasta de cecco", "Mozz. fior di latte", "Detersivo piatti"),
            names,
        )
    }

    @Test
    fun `quantita' dai dettagli, dal peso e dal formato`() {
        val entries = parseReceipt(receipt).associateBy { it.name }

        val latte = entries.getValue("Latte ps granarolo")
        assertEquals(1.0, latte.quantity!!, 0.001)
        assertEquals(QuantityUnit.L, latte.unit)
        assertEquals(1.29, latte.price!!, 0.001)

        val yogurt = entries.getValue("Yogurt bianco")
        assertEquals(2.0, yogurt.quantity!!, 0.001)
        assertEquals(QuantityUnit.PZ, yogurt.unit)

        val banane = entries.getValue("Banane")
        assertEquals(0.725, banane.quantity!!, 0.001)
        assertEquals(QuantityUnit.KG, banane.unit)

        val pasta = entries.getValue("Pasta de cecco")
        assertEquals(500.0, pasta.quantity!!, 0.001)
        // Lo sconto sotto la pasta ne riduce l'importo: conta il prezzo pagato.
        assertEquals(0.95, pasta.price!!, 0.001)
        assertEquals(QuantityUnit.G, pasta.unit)

        assertNull(entries.getValue("Mozz. fior di latte").quantity)
    }

    @Test
    fun `un dettaglio stampato sopra il prodotto va a quel prodotto`() {
        val entries = parseReceipt(
            listOf(
                "PANE  1,20",
                "3 x 0,50",
                "UOVA FRESCHE  1,50",
                "ACQUA  0,30",
            ),
        )

        assertNull(entries[0].quantity)
        assertEquals(3.0, entries[1].quantity!!, 0.001)
        assertNull(entries[2].quantity)
    }

    @Test
    fun `un dettaglio che non torna con nessuno va al prodotto sopra`() {
        val entries = parseReceipt(listOf("MELE  2,00", "1,000 kg x 1,50", "PERE  3,10"))

        assertEquals(1.0, entries[0].quantity!!, 0.001)
        assertEquals(QuantityUnit.KG, entries[0].unit)
        assertNull(entries[1].quantity)
    }

    @Test
    fun `piu' pezzi di un formato moltiplicano il formato`() {
        val entries = parseReceipt(listOf("ACQUA NATURALE 1,5L  1,80", "6 x 0,30"))

        assertEquals(9.0, entries.single().quantity!!, 0.001)
        assertEquals(QuantityUnit.L, entries.single().unit)
    }

    @Test
    fun `codici articolo, lettere di reparto e simboli non entrano nel nome`() {
        val entry = parseReceipt(listOf("8001234567890 CAFFE MACINATO  B  3,49 €")).single()

        assertEquals("Caffe macinato", entry.name)
        assertEquals(3.49, entry.price!!, 0.001)
    }

    @Test
    fun `carta igienica e' un prodotto, il pagamento con carta no`() {
        val names = parseReceipt(listOf("CARTA IGIENICA 4 ROTOLI  2,10", "CARTA DI CREDITO  2,10")).map { it.name }

        assertEquals(listOf("Carta igienica 4 rotoli"), names)
    }

    @Test
    fun `dopo il totale non ci sono prodotti`() {
        val entries = parseReceipt(listOf("LATTE  1,00", "TOT. 1,00", "RESTO  0,00", "GRAZIE E ARRIVEDERCI  0,00"))

        assertEquals(listOf("Latte"), entries.map { it.name })
    }

    @Test
    fun `uno scontrino illeggibile non inventa prodotti`() {
        assertTrue(parseReceipt(listOf("", "   ", "12/10/2026", "***", "1,00")).isEmpty())
    }

    @Test
    fun `uno sconto col meno in fondo vale come col meno davanti`() {
        val entries = parseReceipt(listOf("BISCOTTI  2,50", "PROMO  0,50-", "LATTE  1,00"))

        assertEquals(2.0, entries[0].price!!, 0.001)
        assertEquals(1.0, entries[1].price!!, 0.001)
    }

    @Test
    fun `le confezioni restano separate dal formato`() {
        val entries = parseReceipt(
            listOf("LATTE 1L  2,58", "2 x 1,29", "PASTA 500G  1,15", "YOGURT  0,99", "MELE  2,00", "1,000 kg x 2,00"),
        ).associateBy { it.name }

        assertEquals(2, entries.getValue("Latte").pieces)
        assertEquals(2.0, entries.getValue("Latte").quantity!!, 0.001)
        assertEquals(1, entries.getValue("Pasta").pieces)
        assertNull(entries.getValue("Yogurt").pieces)
        assertNull(entries.getValue("Mele").pieces)
    }

    @Test
    fun `una parola di sconto senza segno a meta' riga non e' uno sconto`() {
        val entries = parseReceipt(listOf("PASTA 1,29", "OFFERTA SPECIALE BISCOTTI 2,50"))

        assertEquals(listOf("Pasta", "Offerta speciale biscotti"), entries.map { it.name })
        assertEquals(1.29, entries[0].price!!, 0.001)
        assertEquals(2.50, entries[1].price!!, 0.001)
        assertEquals(listOf("Biscotti offerta"), parseReceipt(listOf("BISCOTTI OFFERTA 2,50")).map { it.name })
    }

    @Test
    fun `un prodotto in promozione non azzera quello sopra`() {
        val entries = parseReceipt(listOf("PASTA 1,29", "PROMO PASTA 1,29"))

        assertEquals(2, entries.size)
        assertEquals(1.29, entries[0].price!!, 0.001)
        assertEquals("Pasta", entries[1].name)
        assertEquals(1.29, entries[1].price!!, 0.001)
    }

    @Test
    fun `uno sconto senza segno vale se apre la riga ed e' minore del prezzo sopra`() {
        val entries = parseReceipt(listOf("PASTA 1,29", "SCONTO 0,30", "SC.FIDATY -0,30", "LATTE 1,00"))

        assertEquals(listOf("Pasta", "Latte"), entries.map { it.name })
        assertEquals(0.69, entries[0].price!!, 0.001)
    }

    @Test
    fun `un dettaglio col totale dopo un nome senza prezzo crea il prodotto`() {
        val banane = parseReceipt(listOf("BANANE", "1,234 kg x 1,49 €/kg  1,84")).single()
        assertEquals("Banane", banane.name)
        assertEquals(1.84, banane.price!!, 0.001)
        assertEquals(1.234, banane.quantity!!, 0.001)
        assertEquals(QuantityUnit.KG, banane.unit)
        assertNull(banane.pieces)

        val yogurt = parseReceipt(listOf("PANE  1,20", "YOGURT", "2 x 0,89  1,78")).last()
        assertEquals("Yogurt", yogurt.name)
        assertEquals(1.78, yogurt.price!!, 0.001)
        assertEquals(2.0, yogurt.quantity!!, 0.001)
        assertEquals(2, yogurt.pieces)
    }

    @Test
    fun `il totale letto male, il sottototale e l'importo chiudono i prodotti`() {
        assertEquals(listOf("Latte"), parseReceipt(listOf("LATTE 1,00", "T0TALE 12,50", "PANE 1,20")).map { it.name })
        assertEquals(listOf("Latte"), parseReceipt(listOf("LATTE 1,00", "SOTTOTOTALE 2,00", "PANE 1,20")).map { it.name })
        assertEquals(listOf("Latte"), parseReceipt(listOf("LATTE 1,00", "IMPORTO 12,50", "PANE 1,20")).map { it.name })
    }

    @Test
    fun `la riga dell'IVA non e' un prodotto`() {
        assertEquals(listOf("Latte"), parseReceipt(listOf("LATTE 1,00", "DI CUI IVA 1,23")).map { it.name })
    }

    @Test
    fun `formati multipli, staccati e con la O al posto dello zero`() {
        fun one(line: String) = parseReceipt(listOf(line)).single()

        val mozzarella = one("MOZZARELLA 125G X3 2,49")
        assertEquals("Mozzarella", mozzarella.name)
        assertEquals(375.0, mozzarella.quantity!!, 0.001)
        assertEquals(QuantityUnit.G, mozzarella.unit)
        assertEquals(3, mozzarella.pieces)

        val acqua = one("ACQUA 6X1,5L 2,10")
        assertEquals("Acqua", acqua.name)
        assertEquals(9.0, acqua.quantity!!, 0.001)
        assertEquals(QuantityUnit.L, acqua.unit)
        assertEquals(6, acqua.pieces)

        val tonno = one("TONNO 3X80G 3,99")
        assertEquals("Tonno", tonno.name)
        assertEquals(240.0, tonno.quantity!!, 0.001)
        assertEquals(3, tonno.pieces)

        val prosciutto = one("PROSCIUTTO 100 G 2,30")
        assertEquals("Prosciutto", prosciutto.name)
        assertEquals(100.0, prosciutto.quantity!!, 0.001)
        assertEquals(QuantityUnit.G, prosciutto.unit)
        assertEquals(1, prosciutto.pieces)

        val olio = one("OLIO EVO 1 L 6,99")
        assertEquals("Olio evo", olio.name)
        assertEquals(1.0, olio.quantity!!, 0.001)
        assertEquals(QuantityUnit.L, olio.unit)

        val spremuta = one("SPREMUTA O,5L 1,99")
        assertEquals("Spremuta", spremuta.name)
        assertEquals(0.5, spremuta.quantity!!, 0.001)
        assertEquals(QuantityUnit.L, spremuta.unit)
    }
}
