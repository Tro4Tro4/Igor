package com.igor.fridge.data.export

import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.ProductCode
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.RemovalReason
import com.igor.fridge.data.local.SavedListItem
import com.igor.fridge.data.local.SavedListSummary
import com.igor.fridge.data.local.ShoppingItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DataExportTest {

    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val day = LocalDate.of(2026, 10, 3)

    @Test
    fun `esporta cache e associazioni prezzi online`() {
        val offer = com.igor.fridge.data.onlineprices.OnlinePricesJson.parseOffers(com.igor.fridge.data.onlineEnvelope(),"20125").items.single()
        val content = ExportContent(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),
            onlineOffers=listOf(offer),onlineBindings=listOf(com.igor.fridge.data.local.OnlineBinding("s","conad",offer.productId,"latte","",offer.packAmount,offer.packUnit,true)))
        val root = Json.parseToJsonElement(exportJson(content,now)).jsonObject
        assertEquals("0000080050865",root["onlineOffers"]!!.jsonArray.single().jsonObject["gtin"]!!.jsonPrimitive.content)
        assertEquals("s",root["onlineBindings"]!!.jsonArray.single().jsonObject["shoppingUuid"]!!.jsonPrimitive.content)
    }

    @Test
    fun `l'esportazione contiene tutti i dati, compresi gli alimenti usciti`() {
        val json = exportJson(
            ExportContent(
                food = listOf(
                    FoodItem(uuid = "f1", name = "Latte \"intero\"", addedAt = day),
                    FoodItem(
                        uuid = "f2",
                        name = "Pane",
                        addedAt = day,
                        removedAt = now,
                        removalReason = RemovalReason.CONSUMATO,
                    ),
                ),
                shopping = listOf(ShoppingItem(uuid = "s1", name = "Uova", store = "Coop")),
                savedLists = listOf(
                    SavedListSummary("l1", "Settimanale", now, 1) to
                        listOf(SavedListItem(uuid = "i1", listUuid = "l1", position = 0, name = "Riso")),
                ),
                prices = listOf(
                    PriceRecord(
                        uuid = "p1",
                        productKey = "latte",
                        productName = "Latte",
                        purchasedOn = day,
                        quantity = 1.0,
                        unit = QuantityUnit.L,
                        totalCents = 129,
                        unitPriceCents = 129,
                        referenceUnit = QuantityUnit.L,
                    ),
                ),
                barcodes = listOf(ProductCode("latte", "4006381333931", now)),
            ),
            exportedAt = now,
        )

        val root = Json.parseToJsonElement(json).jsonObject
        val inventory = root.getValue("inventory").jsonArray
        assertEquals("Latte \"intero\"", inventory[0].jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals("CONSUMATO", inventory[1].jsonObject.getValue("removalReason").jsonPrimitive.content)
        assertFalse(inventory[0].jsonObject.containsKey("removedAt"))
        assertEquals("Coop", root.getValue("shoppingList").jsonArray[0].jsonObject.getValue("store").jsonPrimitive.content)
        val saved = root.getValue("savedLists").jsonArray[0].jsonObject
        assertEquals("Riso", saved.getValue("items").jsonArray[0].jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals("129", root.getValue("prices").jsonArray[0].jsonObject.getValue("totalCents").jsonPrimitive.content)
        assertEquals(1, root.getValue("barcodes").jsonArray.size)
    }
}
