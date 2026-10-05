package com.igor.fridge.data.local

import androidx.room.Entity
import java.math.BigDecimal

@Entity(tableName = "online_bindings", primaryKeys = ["shoppingUuid", "source"])
data class OnlineBinding(
    val shoppingUuid: String,
    val source: String,
    val productId: String,
    val selectedNameKey: String,
    val selectedBrandKey: String,
    val selectedPackAmount: BigDecimal?,
    val selectedPackUnit: String?,
    val equivalenceConfirmed: Boolean,
)
