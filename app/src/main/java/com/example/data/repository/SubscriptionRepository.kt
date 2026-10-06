package com.example.data.repository

import android.content.Context
import com.example.data.local.SubscriptionDao
import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionDao
import com.example.data.local.TransactionEntity
import com.example.domain.subscription.PotentialSubscription
import com.example.domain.subscription.SubscriptionCalculations
import com.example.domain.subscription.SubscriptionDetector
import com.example.domain.subscription.SubscriptionPriceChange
import com.example.util.UsageHelper
import com.example.util.UsageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Repository responsible for Subscription Data, Detection, Usage Auditing, and Financial Metrics.
 * Encapsulates Room database access and business logic away from Compose UI.
 */
class SubscriptionRepository(
    private val subscriptionDao: SubscriptionDao,
    private val transactionDao: TransactionDao? = null
) {

    /**
     * Observes subscriptions for the given [userId] reactively as a Flow.
     */
    fun getSubscriptionsFlow(userId: String): Flow<List<SubscriptionEntity>> {
        return if (userId.isNotBlank()) {
            subscriptionDao.getSubscriptionsFlow(userId)
        } else {
            subscriptionDao.getAllSubscriptions()
        }
    }

    /**
     * Runs the subscription detector against user transactions.
     * Pure discovery: does not insert into database until confirmed or auto-synced.
     */
    suspend fun detectCandidates(
        userId: String,
        rejectedMerchants: Set<String> = emptySet()
    ): List<PotentialSubscription> = withContext(Dispatchers.IO) {
        val txns = if (transactionDao != null && userId.isNotBlank()) {
            transactionDao.getAllTransactions(userId).firstOrNull() ?: emptyList()
        } else {
            emptyList()
        }
        val existing = subscriptionDao.getAllSubscriptions(userId).firstOrNull() ?: emptyList()
        SubscriptionDetector.detectPotentialSubscriptions(
            transactions = txns,
            existingSubscriptions = existing,
            rejectedMerchants = rejectedMerchants
        )
    }

    /**
     * Detects price changes between transaction history and tracked subscriptions.
     */
    suspend fun detectPriceChanges(userId: String): List<SubscriptionPriceChange> = withContext(Dispatchers.IO) {
        val txns = if (transactionDao != null && userId.isNotBlank()) {
            transactionDao.getAllTransactions(userId).firstOrNull() ?: emptyList()
        } else {
            emptyList()
        }
        val existing = subscriptionDao.getAllSubscriptions(userId).firstOrNull() ?: emptyList()
        SubscriptionDetector.detectPriceChanges(
            subscriptions = existing,
            transactions = txns
        )
    }

    /**
     * Inserts or updates a subscription idempotently.
     * Prevents duplicate entries using (userId + normalized merchant + packageName).
     */
    suspend fun saveOrUpdateSubscription(subscription: SubscriptionEntity): Long = withContext(Dispatchers.IO) {
        if (subscription.amount < 0.0) {
            throw IllegalArgumentException("Subscription amount cannot be negative.")
        }

        val existingList = subscriptionDao.getAllSubscriptions(subscription.userId).firstOrNull() ?: emptyList()
        val existing = existingList.firstOrNull { existingSub ->
            if (subscription.id > 0 && existingSub.id == subscription.id) return@firstOrNull true
            val sameName = existingSub.name.equals(subscription.name, ignoreCase = true) ||
                    existingSub.title.equals(subscription.title, ignoreCase = true) ||
                    existingSub.merchantName.equals(subscription.merchantName, ignoreCase = true)
            val samePackage = !subscription.linkedPackageName.isNullOrBlank() &&
                    existingSub.linkedPackageName.equals(subscription.linkedPackageName, ignoreCase = true)
            sameName || samePackage
        }

        if (existing != null) {
            val updated = existing.copy(
                name = subscription.name.ifBlank { existing.name },
                amount = subscription.amount,
                billingPeriod = subscription.billingPeriod,
                category = subscription.category.ifBlank { existing.category },
                linkedPackageName = subscription.linkedPackageName.ifBlank { existing.linkedPackageName },
                monthlyCost = calculateMonthlyEquivalent(subscription.amount, subscription.billingPeriod),
                status = subscription.status,
                lastChargedDate = if (subscription.lastChargedDate > 0) subscription.lastChargedDate else existing.lastChargedDate,
                updatedAt = System.currentTimeMillis()
            )
            subscriptionDao.updateSubscription(updated)
            existing.id.toLong()
        } else {
            val monthly = calculateMonthlyEquivalent(subscription.amount, subscription.billingPeriod)
            val toInsert = subscription.copy(
                monthlyCost = monthly,
                createdAt = if (subscription.lastChargedDate > 0) subscription.lastChargedDate else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            subscriptionDao.insertSubscription(toInsert)
        }
    }

    /**
     * Audits app usage for subscriptions using [UsageHelper].
     *
     * Rules:
     * - If Usage Access permission is missing: DO NOT mark inactive. Set state USAGE_PERMISSION_REQUIRED.
     * - If Usage Access IS granted:
     *   - usageMinutes < 10: marks inactive (daysUnused = 30, isUnusedFlagged = true).
     *   - usageMinutes >= 10: marks active (daysUnused = 0, isUnusedFlagged = false).
     */
    suspend fun auditSubscriptionUsage(
        context: Context,
        subscriptions: List<SubscriptionEntity>
    ): Map<Int, UsageStatus> = withContext(Dispatchers.IO) {
        val hasPermission = UsageHelper.hasUsagePermission(context)
        val usageMap = mutableMapOf<Int, UsageStatus>()

        for (sub in subscriptions) {
            val pkg = sub.packageName ?: sub.linkedPackageName.ifBlank { null }
            if (pkg == null || !hasPermission) {
                usageMap[sub.id] = UsageStatus.USAGE_PERMISSION_REQUIRED
                continue
            }

            val minutes = UsageHelper.getAppUsageDurationLast30Days(context, pkg)
            if (minutes < 10L) {
                usageMap[sub.id] = UsageStatus.USAGE_AVAILABLE_INACTIVE
                if (!sub.isUnusedFlagged || sub.daysUnused == 0) {
                    subscriptionDao.updateUsage(
                        id = sub.id,
                        daysUnused = 30,
                        isUnusedFlagged = true,
                        usageDays = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } else {
                usageMap[sub.id] = UsageStatus.USAGE_AVAILABLE_ACTIVE
                if (sub.isUnusedFlagged) {
                    subscriptionDao.updateUsage(
                        id = sub.id,
                        daysUnused = 0,
                        isUnusedFlagged = false,
                        usageDays = (minutes / 60).toInt().coerceAtLeast(1),
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }

        usageMap
    }

    /**
     * Confirms and records cancellation of a subscription after reliable confirmation.
     */
    suspend fun recordCancellation(subscriptionId: Int) = withContext(Dispatchers.IO) {
        subscriptionDao.updateStatus(subscriptionId, "CANCELLED", System.currentTimeMillis())
    }

    /**
     * Updates subscription status (e.g. "ACTIVE", "PAUSED", "CANCELLED", "ARCHIVED").
     */
    suspend fun updateSubscriptionStatus(id: Int, status: String) = withContext(Dispatchers.IO) {
        subscriptionDao.updateStatus(id, status, System.currentTimeMillis())
    }

    /**
     * Records a confirmed subscription renewal/payment in the database.
     */
    suspend fun recordPayment(
        subscriptionId: Int,
        amount: Double,
        date: String,
        nextDate: String
    ) = withContext(Dispatchers.IO) {
        subscriptionDao.recordPayment(
            id = subscriptionId,
            date = date,
            amount = amount,
            nextDate = nextDate,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Deletes a subscription from tracking.
     */
    suspend fun deleteSubscription(id: Int) = withContext(Dispatchers.IO) {
        subscriptionDao.deleteSubscription(id)
    }

    // =========================================================================
    // Analytics Calculations (Section 20, 23, 27, 28, 29, 30)
    // =========================================================================

    /**
     * Monthly Recurring Commitment:
     * - Monthly subscription: amount
     * - Annual subscription: amount / 12.0
     * Only sums active / non-cancelled subscriptions.
     */
    fun calculateMonthlyRecurringCommitment(subscriptions: List<SubscriptionEntity>): Double {
        return subscriptions
            .filter { !it.status.equals("CANCELLED", ignoreCase = true) }
            .sumOf { calculateMonthlyEquivalent(it.amount, it.billingPeriod) }
    }

    /**
     * Active Services Count:
     * Counts detected subscriptions that are not inactive or cancelled.
     */
    fun calculateActiveServices(subscriptions: List<SubscriptionEntity>, usageGranted: Boolean): Int {
        return subscriptions.count { sub ->
            val isNotCancelled = !sub.status.equals("CANCELLED", ignoreCase = true) &&
                    !sub.status.equals("PAUSED", ignoreCase = true)
            if (usageGranted) {
                isNotCancelled && !sub.isInactive
            } else {
                isNotCancelled
            }
        }
    }

    /**
     * Wasted on Inactive Apps:
     * Sum annualized costs ONLY for subscriptions where isInactive == true AND usage permission was available.
     * - Monthly: amount * 12.0
     * - Annual: amount
     */
    fun calculateWastedOnInactiveApps(
        subscriptions: List<SubscriptionEntity>,
        usageGranted: Boolean
    ): Double {
        if (!usageGranted) return 0.0

        return subscriptions
            .filter { it.isInactive && !it.status.equals("CANCELLED", ignoreCase = true) }
            .sumOf { calculateAnnualizedCost(it.amount, it.billingPeriod) }
    }

    /**
     * Saved by Cancelling:
     * Only includes actual recorded cancellations (status == "CANCELLED").
     * Returns ₹0 if no cancellations have actually occurred.
     */
    fun calculateSavedByCancelling(subscriptions: List<SubscriptionEntity>): Double {
        return subscriptions
            .filter { it.status.equals("CANCELLED", ignoreCase = true) }
            .sumOf { calculateAnnualizedCost(it.amount, it.billingPeriod) }
    }

    /**
     * Helper to compute monthly equivalent:
     * Monthly: amount
     * Annual / Yearly: amount / 12.0
     */
    fun calculateMonthlyEquivalent(amount: Double, billingPeriod: String): Double {
        if (amount <= 0.0) return 0.0
        return if (billingPeriod.equals("ANNUAL", ignoreCase = true) || billingPeriod.equals("YEARLY", ignoreCase = true)) {
            amount / 12.0
        } else {
            amount
        }
    }

    /**
     * Helper to compute annual cost:
     * Monthly: amount * 12.0
     * Annual / Yearly: amount
     */
    fun calculateAnnualizedCost(amount: Double, billingPeriod: String): Double {
        if (amount <= 0.0) return 0.0
        return if (billingPeriod.equals("ANNUAL", ignoreCase = true) || billingPeriod.equals("YEARLY", ignoreCase = true)) {
            amount
        } else {
            amount * 12.0
        }
    }
}
