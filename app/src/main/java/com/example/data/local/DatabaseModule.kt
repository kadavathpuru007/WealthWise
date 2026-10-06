package com.example.data.local

import android.content.Context

/**
 * DatabaseModule provides centralized, crash-proof access to Room database instances and DAOs.
 * Enforces strict singleton reuse to prevent SQLite connection leaks and database locking.
 */
object DatabaseModule {

    /**
     * Authoritative application database (Version 11) with all 11 entities and migrations.
     */
    fun provideWealthWiseDatabase(context: Context): WealthWiseDatabase {
        return WealthWiseDatabase.getDatabase(context)
    }

    /**
     * Backward-compatible access to AppDatabase using its thread-safe singleton.
     */
    fun provideAppDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    // =========================================================================
    // AUTHORITATIVE DAO PROVIDERS (Single Source of Truth)
    // =========================================================================
    fun provideTransactionDao(context: Context): TransactionDao =
        provideWealthWiseDatabase(context).transactionDao()

    fun provideBudgetDao(context: Context): BudgetDao =
        provideWealthWiseDatabase(context).budgetDao()

    fun provideCategoryDao(context: Context): CategoryDao =
        provideWealthWiseDatabase(context).categoryDao()

    fun provideGoalDao(context: Context): GoalDao =
        provideWealthWiseDatabase(context).goalDao()

    fun provideBillDao(context: Context): BillDao =
        provideWealthWiseDatabase(context).billDao()

    fun provideSubscriptionDao(context: Context): SubscriptionDao =
        provideWealthWiseDatabase(context).subscriptionDao()

    fun provideNotificationDao(context: Context): NotificationDao =
        provideWealthWiseDatabase(context).notificationDao()

    fun provideLoanDao(context: Context): LoanDao =
        provideWealthWiseDatabase(context).loanDao()

    fun provideTradeDao(context: Context): TradeDao =
        provideWealthWiseDatabase(context).tradeDao()
}