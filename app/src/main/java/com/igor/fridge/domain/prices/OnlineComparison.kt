package com.igor.fridge.domain.prices

import com.igor.fridge.data.local.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

data class OnlineQuote(val shoppingUuid: String, val source: String, val offerId: String, val totalCents: Long?, val freshness: OnlineFreshness)
enum class OnlineFreshness { CURRENT, OLD, EXPIRED }
data class OnlineEstimate(val source: String, val covered: Int, val commonTotalCents: Long?)
data class OnlineComparison(val quotes: List<OnlineQuote> = emptyList(), val estimates: List<OnlineEstimate> = emptyList(), val commonItemUuids: Set<String> = emptySet())

fun packCost(required: BigDecimal, pack: BigDecimal, priceCents: Long): Long {
    require(required > BigDecimal.ZERO && pack > BigDecimal.ZERO && priceCents > 0)
    return Math.multiplyExact(required.divide(pack,0,RoundingMode.CEILING).longValueExact(),priceCents)
}

fun bindingMatches(binding: OnlineBinding, item: ShoppingItem): Boolean =
    binding.selectedNameKey == item.nameKey && binding.selectedBrandKey == nameKeyOf(item.brand.orEmpty())

fun compareOnline(items: List<ShoppingItem>, bindings: List<OnlineBinding>, offers: List<OnlineOffer>, now: Instant): OnlineComparison {
    val quotes = bindings.mapNotNull { binding ->
        val item = items.firstOrNull { it.uuid == binding.shoppingUuid && !it.isChecked } ?: return@mapNotNull null
        if (!bindingMatches(binding,item) || !binding.equivalenceConfirmed) return@mapNotNull null
        val offer = offers.filter { it.productId == binding.productId && it.source == binding.source }.maxByOrNull { it.observedAt } ?: return@mapNotNull null
        val age = Duration.between(offer.observedAt,now)
        if (age.isNegative || offer.validUntil?.isBefore(now.atZone(ZoneOffset.UTC).toLocalDate()) == true) return@mapNotNull null
        val freshness = when {
            age <= Duration.ofHours(48) -> OnlineFreshness.CURRENT
            age <= Duration.ofDays(7) -> OnlineFreshness.OLD
            else -> OnlineFreshness.EXPIRED
        }
        if (freshness == OnlineFreshness.EXPIRED) return@mapNotNull null
        val formatSame = binding.selectedPackAmount != null && offer.packAmount != null && binding.selectedPackAmount.compareTo(offer.packAmount) == 0 && binding.selectedPackUnit == offer.packUnit
        val cents = if (freshness != OnlineFreshness.CURRENT || offer.condition != "ordinary" || offer.availability == "unavailable" || !formatSame || !item.quantity.isFinite() || item.quantity <= 0) null else runCatching {
            val quantity = BigDecimal.valueOf(item.quantity)
            when (item.unit) {
                QuantityUnit.PZ,QuantityUnit.CONF -> packCost(quantity,BigDecimal.ONE,offer.packPriceCents)
                QuantityUnit.KG -> if (offer.packUnit == "KG") packCost(quantity,offer.packAmount!!,offer.packPriceCents) else null
                QuantityUnit.G -> if (offer.packUnit == "KG") packCost(quantity.movePointLeft(3),offer.packAmount!!,offer.packPriceCents) else null
                QuantityUnit.L -> if (offer.packUnit == "L") packCost(quantity,offer.packAmount!!,offer.packPriceCents) else null
                QuantityUnit.ML -> if (offer.packUnit == "L") packCost(quantity.movePointLeft(3),offer.packAmount!!,offer.packPriceCents) else null
            }
        }.getOrNull()
        OnlineQuote(item.uuid,offer.source,offer.offerId,cents,freshness)
    }
    val bySource = quotes.groupBy { it.source }.toSortedMap()
    val covered = bySource.mapValues { (_,qs) -> qs.filter { it.totalCents != null }.map { it.shoppingUuid }.toSet() }
    val common = (if (covered.size >= 2) covered.values.reduce { a,b -> a.intersect(b) } else emptySet()).filter { uuid ->
        val item = items.first { it.uuid == uuid }
        if (item.unit !in listOf(QuantityUnit.PZ,QuantityUnit.CONF)) true else {
            val packs = quotes.filter { it.shoppingUuid == uuid && it.totalCents != null }.mapNotNull { quote ->
                offers.firstOrNull { it.offerId == quote.offerId }?.let { it.packAmount?.stripTrailingZeros()?.toPlainString() to it.packUnit }
            }
            packs.distinct().size == 1
        }
    }.toSet()
    val estimates = bySource.map { (source,qs) ->
        val total = if (common.isEmpty()) null else runCatching { qs.filter { it.shoppingUuid in common }.fold(0L) { sum,q -> Math.addExact(sum,q.totalCents!!) } }.getOrNull()
        OnlineEstimate(source,covered.getValue(source).size,total)
    }
    return OnlineComparison(quotes,estimates,common)
}
