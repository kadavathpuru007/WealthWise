package com.example.domain.subscription

import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity

/**
 * Intelligent detector for recurring subscription patterns from real financial transactions.
 * Extends and delegates to the primary [SubscriptionDetector] engine.
 */
object SubscriptionRecurringDetector {

    fun detectPotentialSubscriptions(
        transactions: List<TransactionEntity>,
        existingSubscriptions: List<SubscriptionEntity>,
        rejectedMerchants: Set<String> = emptySet()
    ): List<PotentialSubscription> {
        return SubscriptionDetector.detectPotentialSubscriptions(
            transactions = transactions,
            existingSubscriptions = existingSubscriptions,
            rejectedMerchants = rejectedMerchants
        )
    }

    fun detectPriceChanges(
        subscriptions: List<SubscriptionEntity>,
        transactions: List<TransactionEntity>
    ): List<SubscriptionPriceChange> {
        return SubscriptionDetector.detectPriceChanges(
            subscriptions = subscriptions,
            transactions = transactions
        )
    }
}
