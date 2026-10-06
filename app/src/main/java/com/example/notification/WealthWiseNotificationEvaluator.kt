package com.example.notification

import android.content.Context
import android.util.Log
import com.example.data.local.WealthWiseDatabase
import com.example.data.repository.WealthWiseRepository
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object WealthWiseNotificationEvaluator {

    private const val TAG = "WealthWiseEvaluator"

    /**
     * Evaluates all 5 local financial notification rules against real Room data.
     * Respects user settings and deduplication to prevent notification spam.
     */
    suspend fun evaluateFinancialAlerts(
        context: Context,
        database: WealthWiseDatabase,
        repository: WealthWiseRepository
    ): List<String> {
        val triggeredAlerts = mutableListOf<String>()

        // Verify active user session to prevent processing stale data
        if (!repository.isLoggedIn && !repository.hasCompletedSetup) {
            Log.d(TAG, "No active user session - skipping notification evaluation")
            return emptyList()
        }

        // Check master device notification toggle
        if (!repository.pushNotificationsEnabled) {
            Log.d(TAG, "Device notifications are disabled in user settings")
            return emptyList()
        }

        val deduplicator = WealthWiseNotificationDeduplicator(context)
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val transactions = database.transactionDao().getAllTransactions().firstOrNull() ?: emptyList()
        val budgets = database.budgetDao().getAllBudgets().firstOrNull() ?: emptyList()
        val loans = database.loanDao().getAllLoans().firstOrNull() ?: emptyList()
        val bills = database.billDao().getAllBills().firstOrNull() ?: emptyList()
        val goals = database.goalDao().getAllGoals().firstOrNull() ?: emptyList()

        // ==========================================
        // 1 & 2. BUDGET NOTIFICATIONS (Exceeded & Almost Exhausted)
        // ==========================================
        if (repository.budgetAlertsEnabled) {
            for (budget in budgets) {
                if (budget.monthlyLimit <= 0) continue

                // Actual expenses in this category for current budget period
                val spent = transactions
                    .filter {
                        it.type == "EXPENSE" &&
                        it.category.equals(budget.category, ignoreCase = true) &&
                        (it.date.startsWith(currentMonth) || it.date.isBlank())
                    }
                    .sumOf { it.amount }

                if (spent > budget.monthlyLimit) {
                    // Budget exceeded
                    val exceededKey = WealthWiseNotificationDeduplicator.budgetExceededKey(budget.category, currentMonth)
                    if (!deduplicator.hasBeenSent(exceededKey)) {
                        WealthWiseNotificationManager.postBudgetExceeded(
                            context,
                            budget.category,
                            spent,
                            budget.monthlyLimit
                        )
                        deduplicator.recordSent(exceededKey)
                        triggeredAlerts.add("Budget exceeded: ${budget.category}")
                    }
                } else if (spent >= (budget.monthlyLimit * repository.budgetThresholdPercent)) {
                    // Budget almost exhausted (80-90% threshold, default 90%)
                    val almostKey = WealthWiseNotificationDeduplicator.budgetAlmostExhaustedKey(budget.category, currentMonth)
                    if (!deduplicator.hasBeenSent(almostKey)) {
                        WealthWiseNotificationManager.postBudgetAlmostExhausted(
                            context,
                            budget.category,
                            spent,
                            budget.monthlyLimit
                        )
                        deduplicator.recordSent(almostKey)
                        triggeredAlerts.add("Budget almost exhausted: ${budget.category}")
                    }
                }
            }
        }

        // ==========================================
        // 3. UPCOMING BILL / EMI NOTIFICATIONS
        // ==========================================
        if (repository.billEmiAlertsEnabled) {
            val targetDaysAhead = repository.billReminderDaysAhead.toLong()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val todayCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Check real loans
            for (loan in loans) {
                if (loan.remaining <= 0 || loan.monthlyEmi <= 0 || loan.nextDueDate.isBlank()) continue

                val daysUntil = calculateDaysUntil(todayCal.time, loan.nextDueDate, dateFormat)
                if (daysUntil in 1..targetDaysAhead) {
                    val emiKey = WealthWiseNotificationDeduplicator.upcomingEmiKey(loan.id.toInt(), loan.nextDueDate)
                    if (!deduplicator.hasBeenSent(emiKey)) {
                        val emiAmount = if (loan.remaining < loan.monthlyEmi) loan.remaining else loan.monthlyEmi
                        WealthWiseNotificationManager.postUpcomingEmi(
                            context,
                            loan.id.toInt(),
                            loan.title,
                            emiAmount
                        )
                        deduplicator.recordSent(emiKey)
                        triggeredAlerts.add("Upcoming EMI: ${loan.title}")
                    }
                }
            }

            // Check real bills
            for (bill in bills) {
                if (bill.isPaid || bill.dueDate.isBlank()) continue

                val daysUntil = calculateDaysUntil(todayCal.time, bill.dueDate, dateFormat)
                if (daysUntil in 1..targetDaysAhead) {
                    val billKey = WealthWiseNotificationDeduplicator.upcomingBillKey(bill.id, bill.dueDate)
                    if (!deduplicator.hasBeenSent(billKey)) {
                        WealthWiseNotificationManager.postUpcomingBill(
                            context,
                            bill.id,
                            bill.title,
                            bill.amount
                        )
                        deduplicator.recordSent(billKey)
                        triggeredAlerts.add("Upcoming Bill: ${bill.title}")
                    }
                }
            }
        }

        // ==========================================
        // 4. GOAL FALLING BEHIND & AFFORDABILITY
        // ==========================================
        if (repository.goalAlertsEnabled) {
            val totalBudget = budgets.sumOf { it.monthlyLimit }
            val startingBalance = repository.startingBankBalance
            val totalInflow = transactions
                .filter { it.type == "INCOME" && (it.date.startsWith(currentMonth) || it.date.isBlank()) }
                .sumOf { it.amount }
            val availableBudgetFunds = (startingBalance + totalInflow).coerceAtLeast(0.0)
            val availableMonthlyAfterBudget = (availableBudgetFunds - totalBudget).coerceAtLeast(0.0)

            for (goal in goals) {
                if (goal.targetAmount <= 0 || goal.savedAmount >= goal.targetAmount) continue

                val requiredMonthly = if (goal.monthlyTarget > 0) {
                    goal.monthlyTarget
                } else {
                    ((goal.targetAmount - goal.savedAmount) / 10.0).coerceAtLeast(500.0)
                }

                if (availableMonthlyAfterBudget < requiredMonthly && requiredMonthly > 0) {
                    // Goal affordability shortfall warning
                    val shortfall = requiredMonthly - availableMonthlyAfterBudget
                    val affordabilityKey = WealthWiseNotificationDeduplicator.goalAffordabilityKey(goal.id, currentMonth)
                    if (!deduplicator.hasBeenSent(affordabilityKey)) {
                        WealthWiseNotificationManager.postGoalAffordability(
                            context,
                            goal.id,
                            goal.title,
                            availableMonthlyAfterBudget,
                            requiredMonthly,
                            shortfall
                        )
                        deduplicator.recordSent(affordabilityKey)
                        triggeredAlerts.add("Goal affordability: ${goal.title}")
                    }
                } else {
                    // Check if pace is falling behind
                    val progressPercent = ((goal.savedAmount / goal.targetAmount) * 100).toInt()
                    if (progressPercent < 30) {
                        val behindKey = WealthWiseNotificationDeduplicator.goalBehindKey(goal.id, currentMonth)
                        if (!deduplicator.hasBeenSent(behindKey)) {
                            val shortfallPace = (requiredMonthly * 0.4).coerceAtLeast(1000.0)
                            WealthWiseNotificationManager.postGoalBehind(
                                context,
                                goal.id,
                                goal.title,
                                shortfallPace
                            )
                            deduplicator.recordSent(behindKey)
                            triggeredAlerts.add("Goal falling behind: ${goal.title}")
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. SYNC REQUIRES ATTENTION
        // ==========================================
        if (repository.syncAlertsEnabled && repository.accountAggregatorSyncEnabled) {
            val isListenerGranted = NotificationAccessUtils.isNotificationListenerAccessGranted(context)
            val syncKey = WealthWiseNotificationDeduplicator.syncAttentionKey()

            if (!isListenerGranted) {
                if (!deduplicator.hasBeenSent(syncKey)) {
                    WealthWiseNotificationManager.postSyncRequiresAttention(context)
                    deduplicator.recordSent(syncKey)
                    triggeredAlerts.add("Sync requires attention")
                }
            } else {
                // If listener is granted, reset so future revoking will alert once again
                deduplicator.clearKey(syncKey)
            }
        }

        Log.d(TAG, "Completed notification evaluation. Triggered: ${triggeredAlerts.size} alerts.")
        return triggeredAlerts
    }

    private fun calculateDaysUntil(
        today: Date,
        dueDateStr: String,
        format: SimpleDateFormat
    ): Long {
        return try {
            val dueDate = format.parse(dueDateStr) ?: return -1
            val diffMs = dueDate.time - today.time
            TimeUnit.DAYS.convert(diffMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            -1
        }
    }
}
