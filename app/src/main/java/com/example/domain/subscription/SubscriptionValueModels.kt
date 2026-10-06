package com.example.domain.subscription

import java.text.NumberFormat
import java.util.Locale

/**
 * Value for Money rating of a subscription based on measured device app usage.
 * Strict rule: If usage cannot be measured, UNKNOWN is used (never falsely marked WASTED).
 */
enum class ValueRating(val label: String) {
    GREAT_VALUE("Great Value"),
    MODERATE("Underutilized"),
    WASTED("Money Drain / Unused"),
    UNKNOWN("Usage Unavailable")
}

/**
 * Observable usage state separating zero usage from inability to measure usage.
 */
enum class UsageState(val label: String) {
    ACTIVE_USAGE("Active Usage"),
    LOW_USAGE("Low Usage"),
    NO_USAGE("No Measurable Usage"),
    UNAVAILABLE("Usage Data Unavailable"),
    APP_NOT_INSTALLED("App Not Installed on Device"),
    ERROR("Usage Query Error")
}

/**
 * Production UI model representing a subscription enriched with usage insights.
 */
data class SubscriptionUiModel(
    val id: Long,
    val serviceName: String,
    val monthlyPrice: Double,
    val packageName: String?,
    val nextDueDate: Long,
    val usageMinutesLast30Days: Long?,
    val costPerHour: Double?,
    val valueStatus: ValueRating,
    val usageState: UsageState,
    val category: String = "Subscription",
    val billingPeriod: String = "MONTHLY",
    val originalAmount: Double = monthlyPrice,
    val status: String = "ACTIVE",
    val isAutoDetected: Boolean = false,
    val lastChargedDate: Long = 0L
)

/**
 * Summary aggregate metrics derived from real database records.
 * Zero hardcoded or sample numbers.
 */
data class SubscriptionSummary(
    val monthlyRecurringTotal: Double = 0.0,
    val annualRecurringTotal: Double = 0.0,
    val wastedAnnualTotal: Double = 0.0,
    val activeServicesCount: Int = 0
)

/**
 * Domain engine for subscription financial metrics and usage value evaluation.
 */
object SubscriptionValueEvaluator {

    private val inrFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
        maximumFractionDigits = 0
    }

    /**
     * Formats amount in INR using Indian numbering system.
     */
    fun formatInr(amount: Double): String {
        return try {
            inrFormatter.format(amount)
        } catch (_: Throwable) {
            "₹${amount.toLong()}"
        }
    }

    /**
     * Calculates cost per hour of measured app usage.
     * Guaranteed safe: Never divides by zero, never produces NaN or Infinity.
     * Returns null if usage is 0 or unavailable.
     */
    fun calculateCostPerHour(monthlyPrice: Double, usageMinutes: Long?): Double? {
        if (usageMinutes == null || usageMinutes <= 0L || monthlyPrice <= 0.0) {
            return null
        }
        val hours = usageMinutes / 60.0
        if (hours <= 0.0 || hours.isNaN() || hours.isInfinite()) {
            return null
        }
        val cost = monthlyPrice / hours
        return if (cost.isNaN() || cost.isInfinite()) null else cost
    }

    /**
     * Formats cost per hour for user display.
     * If null/zero: returns explanatory text (never ₹0/hour).
     */
    fun formatCostPerHour(costPerHour: Double?): String {
        if (costPerHour == null || costPerHour <= 0.0 || costPerHour.isNaN() || costPerHour.isInfinite()) {
            return "No measurable usage"
        }
        return "${formatInr(costPerHour)}/hr"
    }

    /**
     * Evaluates ValueRating based on product heuristics and 30-day usage hours.
     * Rules:
     * - Unknown if usage permission not granted, package empty, or app not installed.
     * - Great Value: >= 8.0 hours
     * - Moderate: >= 1.0 and < 8.0 hours
     * - Wasted: < 1.0 hour (including 0 minutes), ONLY when usage data is actually available.
     */
    fun evaluateValueRating(
        usageGranted: Boolean,
        packageName: String?,
        isAppInstalled: Boolean,
        usageMinutes: Long?
    ): ValueRating {
        if (!usageGranted || packageName.isNullOrBlank() || !isAppInstalled || usageMinutes == null) {
            return ValueRating.UNKNOWN
        }
        val usageHours = usageMinutes / 60.0
        return when {
            usageHours >= 8.0 -> ValueRating.GREAT_VALUE
            usageHours >= 1.0 -> ValueRating.MODERATE
            else -> ValueRating.WASTED
        }
    }

    /**
     * Evaluates explicit UsageState.
     */
    fun evaluateUsageState(
        usageGranted: Boolean,
        packageName: String?,
        isAppInstalled: Boolean,
        usageMinutes: Long?
    ): UsageState {
        if (!usageGranted || packageName.isNullOrBlank()) return UsageState.UNAVAILABLE
        if (!isAppInstalled) return UsageState.APP_NOT_INSTALLED
        if (usageMinutes == null) return UsageState.ERROR
        val usageHours = usageMinutes / 60.0
        return when {
            usageHours >= 1.0 -> UsageState.ACTIVE_USAGE
            usageHours > 0.0 -> UsageState.LOW_USAGE
            else -> UsageState.NO_USAGE
        }
    }

    /**
     * Calculates the monthly equivalent planning metric for different billing cycles.
     * Note: This does NOT alter the actual transaction expense ledger amount.
     */
    fun calculateMonthlyEquivalent(amount: Double, billingFrequency: String): Double {
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) return 0.0
        return when (billingFrequency.uppercase(Locale.ROOT)) {
            "MONTHLY" -> amount
            "QUARTERLY" -> amount / 3.0
            "ANNUAL", "YEARLY" -> amount / 12.0
            "WEEKLY" -> (amount * 52.0) / 12.0
            else -> amount
        }
    }

    /**
     * Calculates annualized projection from monthly price.
     */
    fun calculateAnnualCost(monthlyPrice: Double): Double {
        if (monthlyPrice <= 0.0 || monthlyPrice.isNaN() || monthlyPrice.isInfinite()) return 0.0
        return monthlyPrice * 12.0
    }

    /**
     * Computes summary metrics from active subscription UI models.
     * Only subscriptions with valueStatus == WASTED and valid usage are counted as wasted.
     */
    fun calculateSummary(subscriptions: List<SubscriptionUiModel>): SubscriptionSummary {
        val activeSubs = subscriptions.filter { it.status.equals("ACTIVE", ignoreCase = true) }
        val monthlyTotal = activeSubs.sumOf { it.monthlyPrice }
        val annualTotal = monthlyTotal * 12.0
        val wastedAnnualTotal = activeSubs
            .filter { it.valueStatus == ValueRating.WASTED }
            .sumOf { it.monthlyPrice * 12.0 }

        return SubscriptionSummary(
            monthlyRecurringTotal = monthlyTotal,
            annualRecurringTotal = annualTotal,
            wastedAnnualTotal = wastedAnnualTotal,
            activeServicesCount = activeSubs.size
        )
    }

    /**
     * List of popular subscription suggestions for quick auto-fill in Add Subscription sheet.
     */
    val POPULAR_SUGGESTIONS = listOf(
        SubscriptionSuggestion("Netflix", "com.netflix.mediaclient", "Entertainment", 649.0, "MONTHLY"),
        SubscriptionSuggestion("Spotify", "com.spotify.music", "Music", 119.0, "MONTHLY"),
        SubscriptionSuggestion("Amazon Prime", "com.amazon.avod.thirdpartyclient", "Entertainment", 1499.0, "ANNUAL"),
        SubscriptionSuggestion("Disney+ Hotstar", "in.startv.hotstar", "Entertainment", 1499.0, "ANNUAL"),
        SubscriptionSuggestion("YouTube Premium", "com.google.android.youtube", "Entertainment", 149.0, "MONTHLY"),
        SubscriptionSuggestion("Swiggy One", "in.swiggy.android", "Food & Dining", 299.0, "QUARTERLY"),
        SubscriptionSuggestion("Zomato Gold", "com.application.zomato", "Food & Dining", 299.0, "QUARTERLY"),
        SubscriptionSuggestion("Cult.fit", "in.cure.fit", "Fitness", 1250.0, "MONTHLY"),
        SubscriptionSuggestion("ChatGPT Plus", "com.openai.chatgpt", "Productivity", 1999.0, "MONTHLY"),
        SubscriptionSuggestion("Google One", "com.google.android.apps.subscriptions.red", "Cloud Storage", 130.0, "MONTHLY")
    )
}

data class SubscriptionSuggestion(
    val name: String,
    val packageName: String,
    val category: String,
    val defaultPrice: Double,
    val defaultBillingPeriod: String
)
