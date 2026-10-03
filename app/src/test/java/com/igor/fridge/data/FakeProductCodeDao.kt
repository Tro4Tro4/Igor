package com.igor.fridge.data

import com.igor.fridge.data.local.ProductCode
import com.igor.fridge.data.local.ProductCodeDao

class FakeProductCodeDao : ProductCodeDao {
    val codes = mutableMapOf<String, ProductCode>()

    override suspend fun find(productKey: String): ProductCode? = codes[productKey]

    override suspend fun all(): List<ProductCode> = codes.values.toList()

    override suspend fun upsert(code: ProductCode) {
        codes[code.productKey] = code
    }

    override suspend fun delete(productKey: String) {
        codes.remove(productKey)
    }
}
