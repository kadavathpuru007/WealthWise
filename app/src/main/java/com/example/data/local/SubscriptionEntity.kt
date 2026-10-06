package com.example.data.local

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Room entity representing tracked and detected subscriptions in WealthWise.
 *
 * Supports both standard schema fields and Section 15 required fields:
 * id, userId, title, amount, billingCycle, packageName, category, lastChargedDate, isAutoDetected, isInactive.
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String = "",
    val name: String = "",
    val merchantName: String = name,
    val amount: Double = 0.0,
    val currency: String = "INR",
    val billingPeriod: String = "MONTHLY", // "MONTHLY", "YEARLY", "ANNUAL", "WEEKLY", "CUSTOM"
    val category: String = "Subscription",
    val nextExpectedPaymentDate: String = "",
    val lastConfirmedPaymentDate: String = "",
    val lastConfirmedAmount: Double = amount,
    val source: String = "MANUAL", // "MANUAL", "DETECTED", "SMS", "NOTIFICATION"
    val linkedPackageName: String = "",
    val status: String = "ACTIVE", // "ACTIVE", "PAUSED", "CANCELLED", "ARCHIVED", "INACTIVE"
    val usageTrackingAvailable: Boolean = false,
    val usagePeriodDays: Int = 30,
    val usageDays: Int = 0,
    val monthlyCost: Double = amount,
    val daysUnused: Int = 0,
    val isUnusedFlagged: Boolean = false,
    val previousAmount: Double = 0.0,
    val lastChargedDate: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val title: String
        get() = name.ifBlank { merchantName }

    val billingCycle: String
        get() = billingPeriod

    val packageName: String?
        get() = linkedPackageName.ifBlank { null }

    val isAutoDetected: Boolean
        get() = source != "MANUAL"

    val isInactive: Boolean
        get() = status.equals("INACTIVE", ignoreCase = true) || isUnusedFlagged || daysUnused > 0

    @Ignore
    constructor(
        id: Int = 0,
        userId: String = "",
        title: String,
        amount: Double = 0.0,
        billingCycle: String = "MONTHLY",
        packageName: String? = null,
        category: String = "Subscription",
        lastChargedDate: Long = 0L,
        isAutoDetected: Boolean = false,
        isInactive: Boolean = false
    ) : this(
        id = id,
        userId = userId,
        name = title,
        merchantName = title,
        amount = amount,
        currency = "INR",
        billingPeriod = billingCycle,
        category = category,
        nextExpectedPaymentDate = "",
        lastConfirmedPaymentDate = "",
        lastConfirmedAmount = amount,
        source = if (isAutoDetected) "DETECTED" else "MANUAL",
        linkedPackageName = packageName ?: "",
        status = if (isInactive) "INACTIVE" else "ACTIVE",
        usageTrackingAvailable = !packageName.isNullOrBlank(),
        usagePeriodDays = 30,
        usageDays = 0,
        monthlyCost = if (billingCycle.equals("ANNUAL", ignoreCase = true) || billingCycle.equals("YEARLY", ignoreCase = true)) amount / 12.0 else amount,
        daysUnused = if (isInactive) 30 else 0,
        isUnusedFlagged = isInactive,
        previousAmount = 0.0,
        lastChargedDate = lastChargedDate,
        createdAt = if (lastChargedDate > 0) lastChargedDate else System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
