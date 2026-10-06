package com.example.ui.subscriptions

import androidx.compose.runtime.Composable
import com.example.data.local.SubscriptionEntity
import com.example.domain.subscription.PotentialSubscription
import com.example.domain.subscription.SubscriptionPriceChange
import com.example.permission.AccessStatus
import com.example.permission.SubscriptionUsageInfo

/**
 * Backward compatibility delegator: routes legacy SubscriptionKillerScreen calls
 * to the overhauled, production-quality SubscriptionScreen.
 */
@Composable
fun SubscriptionKillerScreen(
    subscriptions: List<SubscriptionEntity> = emptyList(),
    potentialSubscriptions: List<PotentialSubscription> = emptyList(),
    priceChangeAlerts: List<SubscriptionPriceChange> = emptyList(),
    usageAccessStatus: AccessStatus = AccessStatus.NOT_CONNECTED,
    subscriptionUsageMap: Map<Int, SubscriptionUsageInfo> = emptyMap(),
    selectedFilter: String = "ALL",
    onSelectFilter: (String) -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onConfirmDetected: (PotentialSubscription) -> Unit = {},
    onRejectDetected: (PotentialSubscription) -> Unit = {},
    onOpenApp: (SubscriptionEntity) -> Unit = {},
    onLogPayment: (SubscriptionEntity) -> Unit = {},
    onSelectSubscriptionDetail: (SubscriptionEntity) -> Unit = {},
    onPauseSubscription: (SubscriptionEntity) -> Unit = {},
    onCancelSubscription: (SubscriptionEntity) -> Unit = {},
    onReactivateSubscription: (SubscriptionEntity) -> Unit = {},
    onArchiveSubscription: (SubscriptionEntity) -> Unit = {},
    onDeleteSubscription: (Int) -> Unit = {},
    onAddSubscriptionClick: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    SubscriptionScreen(
        onBack = onBack
    )
}
