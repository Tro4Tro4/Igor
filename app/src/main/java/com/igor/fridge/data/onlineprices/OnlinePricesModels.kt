package com.igor.fridge.data.onlineprices

import com.igor.fridge.data.local.OnlineOffer

data class OnlinePage(val items: List<OnlineOffer>, val nextOffset: Int?, val catalogVersion: Long)
data class OnlinePricesSettings(val enabled: Boolean = false, val postcode: String = "20125")
class OnlinePricesException(message: String, val transient: Boolean = false) : Exception(message)
