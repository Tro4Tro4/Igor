package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Una voce della lista della spesa. */
@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val quantity: Double = 1.0,
    val unit: QuantityUnit = QuantityUnit.PZ,
    val isChecked: Boolean = false,
    val createdAt: LocalDate = LocalDate.now(),
)
