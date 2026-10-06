package com.example.domain.subscription

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Standard billing period options for subscriptions.
 */
enum class BillingPeriod(val label: String, val multiplierToYear: Double) {
    MONTHLY("Monthly", 12.0),
    YEARLY("Yearly", 1.0),
    WEEKLY("Weekly", 52.0),
    CUSTOM("Custom", 12.0);

    companion object {
        fun fromString(value: String): BillingPeriod {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MONTHLY
        }
    }
}

/**
 * Status lifecycle of a subscription record.
 */
enum class SubscriptionStatus(val label: String) {
    ACTIVE("Active"),
    PAUSED("Paused"),
    CANCELLED("Cancelled"),
    ARCHIVED("Archived");

    companion object {
        fun fromString(value: String): SubscriptionStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
        }
    }
}

/**
 * Android Usage Access state for a subscription service.
 * Never claims "Unused" if usage data is unavailable!
 */
enum class UsageTrackingState(val label: String) {
    USAGE_AVAILABLE("Usage Available"),
    NO_DETECTED_USAGE("No Detected Usage"),
    USAGE_UNAVAILABLE("Usage Data Unavailable")
}

/**
 * Detected recurring pattern candidate from real transactions.
 * Requires explicit user confirmation or rejection.
 */
data class PotentialSubscription(
    val merchant: String,
    val amount: Double,
    val billingPeriod: String = "MONTHLY",
    val intervalDays: Int = 30,
    val occurrences: Int = 2,
    val lastTransactionDate: String = "",
    val confidence: Double = 0.85,
    val reason: String = "Recurring pattern detected • Approx. every 30 days",
    val suggestedPackage: String? = null
)

/**
 * Detected price change between historical payment and current subscription amount.
 */
data class SubscriptionPriceChange(
    val subscriptionId: Int,
    val name: String,
    val oldAmount: Double,
    val newAmount: Double,
    val difference: Double,
    val detectedDate: String
)

/**
 * User-configurable preferences for Subscription Management.
 */
data class SubscriptionSettings(
    val usageInsightsEnabled: Boolean = false,
    val upcomingAlertsEnabled: Boolean = true,
    val unusedAlertsEnabled: Boolean = true,
    val priceChangeAlertsEnabled: Boolean = true,
    val observationPeriodDays: Int = 30
)

/**
 * Utility functions for subscription financial and date calculations.
 * Guaranteed zero fake transactions: planning calculations only.
 */
object SubscriptionCalculations {

    /**
     * Calculates the monthly equivalent of a subscription for planning metrics.
     * MONTHLY: amount
     * YEARLY: amount / 12
     * WEEKLY: (amount * 52) / 12
     */
    fun calculateMonthlyEquivalent(amount: Double, billingPeriod: String): Double {
        if (amount <= 0.0) return 0.0
        val period = BillingPeriod.fromString(billingPeriod)
        return when (period) {
            BillingPeriod.MONTHLY -> amount
            BillingPeriod.YEARLY -> BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                .toDouble()
            BillingPeriod.WEEKLY -> BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(52))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                .toDouble()
            BillingPeriod.CUSTOM -> amount
        }
    }

    /**
     * Calculates the annual cost of a subscription for planning metrics.
     */
    fun calculateAnnualCost(amount: Double, billingPeriod: String): Double {
        if (amount <= 0.0) return 0.0
        val period = BillingPeriod.fromString(billingPeriod)
        return when (period) {
            BillingPeriod.MONTHLY -> amount * 12.0
            BillingPeriod.YEARLY -> amount
            BillingPeriod.WEEKLY -> amount * 52.0
            BillingPeriod.CUSTOM -> amount * 12.0
        }
    }

    /**
     * Estimates the next expected payment date based on last payment date and billing period.
     * Labelled clearly as EXPECTED, never confirmed.
     */
    fun estimateNextPaymentDate(lastPaymentDateStr: String, billingPeriod: String): String {
        if (lastPaymentDateStr.isBlank()) return ""
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val date = format.parse(lastPaymentDateStr) ?: return ""
            val cal = Calendar.getInstance().apply { time = date }
            val period = BillingPeriod.fromString(billingPeriod)
            when (period) {
                BillingPeriod.MONTHLY -> cal.add(Calendar.MONTH, 1)
                BillingPeriod.YEARLY -> cal.add(Calendar.YEAR, 1)
                BillingPeriod.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7)
                BillingPeriod.CUSTOM -> cal.add(Calendar.MONTH, 1)
            }
            format.format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }
}
