package com.igor.fridge.data.export

import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.ProductCode
import com.igor.fridge.data.local.SavedListItem
import com.igor.fridge.data.local.SavedListSummary
import com.igor.fridge.data.local.ShoppingItem
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

/** Tutto cio' che Igor sa dell'utente, da esportare. */
data class ExportContent(
    /** Compresi gli alimenti usciti dal frigo: sono la storia dei consumi. */
    val food: List<FoodItem>,
    val shopping: List<ShoppingItem>,
    val savedLists: List<Pair<SavedListSummary, List<SavedListItem>>>,
    val prices: List<PriceRecord>,
    val barcodes: List<ProductCode>,
    val onlineOffers: List<com.igor.fridge.data.local.OnlineOffer> = emptyList(),
    val onlineBindings: List<com.igor.fridge.data.local.OnlineBinding> = emptyList(),
    val onlineSources: List<com.igor.fridge.data.local.OnlineSource> = emptyList(),
)

/**
 * I dati in un file JSON leggibile: una copia da tenere o da portare altrove, e il modo
 * di vedere tutto cio' che l'app conserva. Le foto restano fuori: nel file c'e' solo il
 * loro nome.
 */
fun exportJson(content: ExportContent, exportedAt: Instant): String = buildJsonObject {
    put("app", "Igor")
    put("format", 1)
    put("exportedAt", exportedAt.toString())
    putJsonArray("inventory") {
        content.food.forEach { item ->
            addJsonObject {
                put("name", item.name)
                put("category", item.category.name)
                put("location", item.location.name)
                put("quantity", item.quantity)
                put("unit", item.unit.name)
                putNullable("brand", item.brand)
                putNullable("barcode", item.barcode)
                putNullable("expiryDate", item.expiryDate?.toString())
                put("addedAt", item.addedAt.toString())
                putNullable("notes", item.notes)
                putNullable("removedAt", item.removedAt?.toString())
                putNullable("removalReason", item.removalReason?.name)
            }
        }
    }
    putJsonArray("shoppingList") {
        content.shopping.forEach { item ->
            addJsonObject {
                put("name", item.name)
                put("quantity", item.quantity)
                put("unit", item.unit.name)
                put("category", item.category.name)
                put("checked", item.isChecked)
                putNullable("brand", item.brand)
                putNullable("notes", item.notes)
                putNullable("photo", item.photoPath)
                putNullable("unitPriceCents", item.unitPriceCents)
                putNullable("store", item.store)
            }
        }
    }
    putJsonArray("savedLists") {
        content.savedLists.forEach { (list, items) ->
            addJsonObject {
                put("name", list.name)
                put("updatedAt", list.updatedAt.toString())
                putJsonArray("items") {
                    items.forEach { item ->
                        addJsonObject {
                            put("name", item.name)
                            put("quantity", item.quantity)
                            put("unit", item.unit.name)
                            put("category", item.category.name)
                            putNullable("brand", item.brand)
                            putNullable("notes", item.notes)
                            putNullable("store", item.store)
                        }
                    }
                }
            }
        }
    }
    putJsonArray("prices") {
        content.prices.forEach { record ->
            addJsonObject {
                put("product", record.productName)
                put("purchasedOn", record.purchasedOn.toString())
                putNullable("store", record.store)
                put("quantity", record.quantity)
                put("unit", record.unit.name)
                put("totalCents", record.totalCents)
                put("unitPriceCents", record.unitPriceCents)
                put("referenceUnit", record.referenceUnit.name)
            }
        }
    }
    putJsonArray("barcodes") {
        content.barcodes.forEach { code ->
            addJsonObject {
                put("productKey", code.productKey)
                put("barcode", code.barcode)
            }
        }
    }
    putJsonArray("onlineOffers") {
        content.onlineOffers.forEach { o -> addJsonObject {
            put("offerId",o.offerId); put("requestedPostcode",o.requestedPostcode)
            put("productId",o.productId); put("source",o.source); put("sourceSku",o.sourceSku)
            put("name",o.name); putNullable("brand",o.brand); putNullable("gtin",o.gtin); put("gtinVerified",o.gtinVerified)
            putNullable("packAmount",o.packAmount?.toPlainString()); putNullable("packUnit",o.packUnit); put("packCount",o.packCount)
            put("packPriceCents",o.packPriceCents); put("observedAt",o.observedAt.toString()); put("sourceUrl",o.sourceUrl)
            put("scope",o.scope); putNullable("postcode",o.postcode); put("availability",o.availability); put("condition",o.condition)
            putNullable("validUntil",o.validUntil?.toString())
        } }
    }
    putJsonArray("onlineBindings") {
        content.onlineBindings.forEach { b -> addJsonObject {
            put("shoppingUuid",b.shoppingUuid); put("source",b.source); put("productId",b.productId)
            put("selectedNameKey",b.selectedNameKey); put("selectedBrandKey",b.selectedBrandKey)
            putNullable("selectedPackAmount",b.selectedPackAmount?.toPlainString()); putNullable("selectedPackUnit",b.selectedPackUnit)
            put("equivalenceConfirmed",b.equivalenceConfirmed)
        } }
    }
    putJsonArray("onlineSources") {
        content.onlineSources.forEach { s -> addJsonObject {
            put("source",s.source); put("status",s.status); put("priority",s.priority)
            putNullable("lastSuccessfulAt",s.lastSuccessfulAt?.toString()); put("limitation",s.limitation)
        } }
    }
}.toString()

private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
    if (value != null) put(key, value)
}

private fun JsonObjectBuilder.putNullable(key: String, value: Long?) {
    if (value != null) put(key, value)
}
