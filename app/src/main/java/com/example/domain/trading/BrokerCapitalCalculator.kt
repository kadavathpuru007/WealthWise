package com.example.domain.trading

import com.example.data.local.TransactionEntity
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialStatus
import java.util.Locale

/**
 * Pure calculation engine for automated broker capital tracking.
 * Integrates directly with the central WealthWise Transaction ledger without duplicating financial records.
 */
object BrokerCapitalCalculator {

    val BROKER_KEYWORDS = listOf(
        "ZERODHA",
        "GROWW",
        "ANGELONE",
        "ANGEL ONE",
        "UPSTOX",
        "DHAN",
        "PAYTM MONEY",
        "5PAISA",
        "ICICI DIRECT",
        "KITE",
        "INDMONEY"
    )

    private val DISQUALIFYING_PROMO_KEYWORDS = listOf(
        "ADVERTISEMENT",
        "PROMO",
        "OFFER",
        "REWARD",
        "REWARDS",
        "CASHBACK AD",
        "INVITE",
        "REFERRAL",
        "TIPS",
        "NEWS",
        "SURVEY",
        "CLAIM"
    )

    /**
     * Determines whether a transaction is a completed, valid financial transaction.
     * PENDING, FAILED, and non-completed statuses are excluded.
     */
    fun isCompleted(txn: TransactionEntity): Boolean {
        val s = txn.status.trim().uppercase(Locale.ROOT)
        return s.isEmpty() || s == FinancialStatus.COMPLETED.name || s == "SUCCESS" || s == "PAID"
    }

    /**
     * Identifies whether a transaction text or title contains promotional or non-financial content.
     */
    fun isPromotional(txn: TransactionEntity): Boolean {
        val combined = "${txn.title} ${txn.merchant} ${txn.note}".uppercase(Locale.ROOT)
        return DISQUALIFYING_PROMO_KEYWORDS.any { combined.contains(it) }
    }

    /**
     * Matches normalized fields against known broker identifiers.
     */
    fun matchesBroker(txn: TransactionEntity): Boolean {
        val categoryNorm = txn.category.trim().uppercase(Locale.ROOT)
        val merchantNorm = txn.merchant.trim().uppercase(Locale.ROOT)
        val titleNorm = txn.title.trim().uppercase(Locale.ROOT)
        val noteNorm = txn.note.trim().uppercase(Locale.ROOT)
        val destNorm = txn.destinationAccount.trim().uppercase(Locale.ROOT)
        val sourceNorm = txn.sourceAccount.trim().uppercase(Locale.ROOT)

        val matchesKeyword = BROKER_KEYWORDS.any { kw ->
            merchantNorm.contains(kw) ||
                    titleNorm.contains(kw) ||
                    noteNorm.contains(kw) ||
                    destNorm.contains(kw) ||
                    sourceNorm.contains(kw)
        }

        if (matchesKeyword) return true

        // Category is "Investment" and does not indicate standard bank fixed deposit / savings
        if (categoryNorm == "INVESTMENT") {
            val isBanking = titleNorm.contains("FIXED DEPOSIT") || titleNorm.contains("FD") || titleNorm.contains("RD")
            return !isBanking
        }

        return false
    }

    /**
     * Computes reactive capital metrics from completed transactions in the central ledger.
     */
    fun calculateMetrics(transactions: List<TransactionEntity>): BrokerCapitalMetrics {
        var totalDeployed = 0.0
        var totalPayouts = 0.0

        for (txn in transactions) {
            // Must be completed
            if (!isCompleted(txn)) continue

            // Must not be promotional/advertisement
            if (isPromotional(txn)) continue

            // Must match broker / investment
            if (!matchesBroker(txn)) continue

            val amount = kotlin.math.abs(txn.amount)
            if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) continue

            val eventTypeNorm = txn.eventType.trim().uppercase(Locale.ROOT)
            val typeNorm = txn.type.trim().uppercase(Locale.ROOT)

            val isRefundOrReversal = eventTypeNorm == FinancialEventType.REFUND.name ||
                    eventTypeNorm == FinancialEventType.REVERSAL.name ||
                    typeNorm == "REFUND" ||
                    typeNorm == "REVERSAL"

            val isInflow = txn.isInflow ||
                    typeNorm == "CREDIT" ||
                    typeNorm == "INCOME" ||
                    eventTypeNorm == FinancialEventType.INCOME.name ||
                    eventTypeNorm == FinancialEventType.INVESTMENT_SALE.name

            val isOutflow = txn.isOutflow ||
                    typeNorm == "DEBIT" ||
                    typeNorm == "EXPENSE" ||
                    eventTypeNorm == FinancialEventType.EXPENSE.name ||
                    eventTypeNorm == FinancialEventType.INVESTMENT_PURCHASE.name

            when {
                isRefundOrReversal -> {
                    // Reversal / refund reduces deployed capital
                    totalDeployed = (totalDeployed - amount).coerceAtLeast(0.0)
                }
                isInflow -> {
                    totalPayouts += amount
                }
                isOutflow -> {
                    totalDeployed += amount
                }
            }
        }

        val netCapital = (totalDeployed - totalPayouts).coerceAtLeast(0.0)

        return BrokerCapitalMetrics(
            totalDeployedCapital = totalDeployed,
            totalPayoutsWithdrawn = totalPayouts,
            netCapitalInMarket = netCapital
        )
    }
}
