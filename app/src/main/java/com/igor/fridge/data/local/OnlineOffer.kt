package com.igor.fridge.data.local

import androidx.room.Entity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "online_offers", primaryKeys = ["offerId", "requestedPostcode"])
data class OnlineOffer(
    val offerId: String,
    val requestedPostcode: String,
    val productId: String,
    val source: String,
    val sourceSku: String,
    val name: String,
    val brand: String?,
    val gtin: String?,
    val gtinVerified: Boolean,
    val packAmount: BigDecimal?,
    val packUnit: String?,
    val packCount: Int,
    val packPriceCents: Long,
    val observedAt: Instant,
    val sourceUrl: String,
    val scope: String,
    val postcode: String?,
    val availability: String,
    val condition: String,
    val validUntil: LocalDate?,
)
