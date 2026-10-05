package com.igor.fridge.domain.prices

import com.igor.fridge.data.local.*
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class OnlineComparisonTest {
    private val now = Instant.parse("2026-10-05T10:00:00Z")
    private fun offer(source: String, item: String, cents: Long = 99, age: Long = 0) = OnlineOffer(
        offerId="$source:$item", requestedPostcode="20125", productId="gtin:8076800195057",source=source,sourceSku=item,name="Pasta",brand="Barilla",gtin="8076800195057",gtinVerified=true,packAmount=BigDecimal("0.5"),packUnit="KG",packCount=1,packPriceCents=cents,observedAt=now.minusSeconds(age),sourceUrl="https://www.carrefour.it/p/a",scope="generic",postcode=null,availability="unknown",condition="ordinary",validUntil=null,
    )
    private fun binding(item: ShoppingItem, source: String) = OnlineBinding(item.uuid,source,"gtin:8076800195057",item.nameKey,nameKeyOf(item.brand.orEmpty()),BigDecimal("0.5"),"KG",true)
    @Test fun `confezioni intere e overflow`() {
        assertEquals(198L,packCost(BigDecimal("0.75"),BigDecimal("0.5"),99))
        assertTrue(runCatching { packCost(BigDecimal("2"),BigDecimal.ONE,Long.MAX_VALUE) }.isFailure)
    }
    @Test fun `un pezzo non rende equivalenti confezioni di dimensione diversa`() {
        val item=ShoppingItem("a","Pasta",1.0,QuantityUnit.PZ)
        val small=offer("carrefour","a",100)
        val large=offer("conad","a",150).copy(packAmount=BigDecimal.ONE)
        val result=compareOnline(listOf(item),listOf(binding(item,"carrefour"),binding(item,"conad").copy(selectedPackAmount=BigDecimal.ONE)),listOf(small,large),now)
        assertTrue(result.commonItemUuids.isEmpty())
        assertTrue(result.estimates.all { it.commonTotalCents == null })
    }
    @Test fun `totali usano insieme comune anche con copertura diversa`() {
        val a=ShoppingItem("a","Pasta",0.75,QuantityUnit.KG)
        val b=ShoppingItem("b","Riso",1.0,QuantityUnit.KG)
        val result=compareOnline(listOf(a,b),listOf(binding(a,"carrefour"),binding(a,"conad"),binding(b,"conad")),listOf(offer("carrefour","a"),offer("conad","a",109)),now)
        assertEquals(setOf("a"),result.commonItemUuids)
        assertEquals(listOf(198L,218L),result.estimates.map { it.commonTotalCents })
    }
    @Test fun `cache vecchia carta formato mancante e binding modificato non contano`() {
        val item=ShoppingItem("a","Pasta",1.0,QuantityUnit.PZ)
        val bind=binding(item,"carrefour")
        listOf(offer("carrefour","a",age=172801),offer("carrefour","a").copy(condition="loyalty"),offer("carrefour","a").copy(packAmount=null,packUnit=null),offer("carrefour","a").copy(observedAt=now.plusSeconds(1))).forEach {
            assertNull(compareOnline(listOf(item),listOf(bind),listOf(it),now).estimates.firstOrNull()?.commonTotalCents)
        }
        assertTrue(compareOnline(listOf(item.copy(brand="Altro")),listOf(bind),listOf(offer("carrefour","a")),now).quotes.isEmpty())
    }
}
