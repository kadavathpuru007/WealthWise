package com.example.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.BillEntity
import com.example.data.local.GoalEntity
import com.example.data.local.SubscriptionEntity
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.WealthWiseUiState

/**
 * HomeScreen observes authoritative financial state from HomeViewModel
 * and delegates to HomeDashboardScreen for presentation and interaction.
 */
@Composable
fun HomeScreen(
    state: WealthWiseUiState,
    homeViewModel: HomeViewModel = viewModel(),
    onAddIncome: () -> Unit = {},
    onAddExpense: () -> Unit = {},
    onViewReports: () -> Unit = {},
    onAdjustBudget: () -> Unit = {},
    onReviewSubscriptions: () -> Unit = {},
    onCancelSubscription: (SubscriptionEntity) -> Unit = {},
    onPayBill: (BillEntity) -> Unit = {},
    onFundGoal: (GoalEntity) -> Unit = {},
    onAddGoal: () -> Unit = {},
    onViewFullLedger: () -> Unit = {},
    onDismissNotification: (String, Int?) -> Unit = { _, _ -> },
    onOpenDrawer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onOpenUniversalSearch: () -> Unit = {},
    onOpenTotalBalance: () -> Unit = {},
    onOpenIncome: () -> Unit = {},
    onOpenExpenses: () -> Unit = {},
    onOpenSavings: () -> Unit = {},
    onOpenInvestments: () -> Unit = {},
    onOpenLoans: () -> Unit = {}
) {
    val homeState by homeViewModel.homeState.collectAsState()

    HomeDashboardScreen(
        state = state,
        homeState = homeState,
        onAddIncome = onAddIncome,
        onAddExpense = onAddExpense,
        onViewReports = onViewReports,
        onAdjustBudget = onAdjustBudget,
        onReviewSubscriptions = onReviewSubscriptions,
        onCancelSubscription = onCancelSubscription,
        onPayBill = onPayBill,
        onFundGoal = onFundGoal,
        onAddGoal = onAddGoal,
        onViewFullLedger = onViewFullLedger,
        onDismissNotification = onDismissNotification,
        onOpenDrawer = onOpenDrawer,
        onOpenSettings = onOpenSettings,
        onOpenBudgets = onOpenBudgets,
        onOpenGoals = onOpenGoals,
        onSyncNow = onSyncNow,
        onOpenUniversalSearch = onOpenUniversalSearch,
        onOpenTotalBalance = onOpenTotalBalance,
        onOpenIncome = onOpenIncome,
        onOpenExpenses = onOpenExpenses,
        onOpenSavings = onOpenSavings,
        onOpenInvestments = onOpenInvestments,
        onOpenLoans = onOpenLoans
    )
}
