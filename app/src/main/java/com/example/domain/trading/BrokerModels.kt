package com.example.domain.trading

import java.text.NumberFormat
import java.util.Locale

/**
 * Regulatory categories recognized by Indian financial regulators (SEBI / RBI).
 */
enum class RegulatoryCategory {
    STOCK_BROKER,
    DEPOSITORY_PARTICIPANT,
    MUTUAL_FUND_DISTRIBUTOR,
    INVESTMENT_ADVISER,
    RESEARCH_ANALYST,
    UNKNOWN
}

/**
 * Centralized registry entry for Indian brokers.
 * Package names are configurable identifiers and not assumed to be immutable.
 */
data class BrokerRegistryEntry(
    val brokerName: String,
    val packageNames: Set<String>,
    val officialName: String,
    val regulatoryCategory: RegulatoryCategory,
    val registrationNumber: String? = null,
    val verifiedDate: Long? = null
)

/**
 * Security states reflecting device-detected broker apps and independent regulatory verification.
 * Installed package detection is separated strictly from authoritative SEBI / RBI status.
 */
sealed interface BrokerSecurityState {

    data class Verified(
        val brokerName: String,
        val lastVerified: Long?
    ) : BrokerSecurityState

    data class Alert(
        val platformName: String,
        val source: String,
        val lastUpdated: Long?
    ) : BrokerSecurityState

    data class DetectedUnverified(
        val brokerName: String
    ) : BrokerSecurityState

    data object Neutral : BrokerSecurityState

    data object Loading : BrokerSecurityState
}

/**
 * Capital metrics aggregated from the central transaction ledger.
 */
data class BrokerCapitalMetrics(
    val totalDeployedCapital: Double = 0.0,
    val totalPayoutsWithdrawn: Double = 0.0,
    val netCapitalInMarket: Double = 0.0
)

/**
 * Pure currency formatting utility adhering to Indian Rupee (Locale("en", "IN")) specifications.
 */
object TradingMoneyFormatter {
    private val inrLocale: Locale = Locale.forLanguageTag("en-IN")

    private val inrFormat: NumberFormat = NumberFormat.getCurrencyInstance(inrLocale).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    private val inrDecimalFormat: NumberFormat = NumberFormat.getCurrencyInstance(inrLocale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    fun format(amount: Double, includeDecimals: Boolean = false): String {
        if (amount.isNaN() || amount.isInfinite()) return "₹0"
        val formatter = if (includeDecimals) inrDecimalFormat else inrFormat
        return synchronized(formatter) {
            formatter.format(amount)
        }
    }

    fun formatPnl(pnl: Double, includeDecimals: Boolean = false): String {
        if (pnl.isNaN() || pnl.isInfinite()) return "₹0"
        val formatted = format(kotlin.math.abs(pnl), includeDecimals)
        return when {
            pnl > 0.0 -> "+$formatted"
            pnl < 0.0 -> "-$formatted"
            else -> formatted
        }
    }
}
