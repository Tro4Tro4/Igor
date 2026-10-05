package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "online_sources")
data class OnlineSource(
    @PrimaryKey val source: String,
    val status: String,
    val priority: Int,
    val lastSuccessfulAt: Instant?,
    val limitation: String = "",
)
