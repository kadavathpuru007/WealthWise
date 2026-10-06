package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.MoneyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlin.math.max

data class BalanceUiState(
    val initialBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalRealExpenses: Double = 0.0,
    val actualBalance: Double = 0.0,
    val totalSavedAcrossGoals: Double = 0.0,
    val availableBalance: Double = 0.0,
    val totalMonthlyBudgetCap: Double = 0.0,
    val currentMonthBudgetedExpenses: Double = 0.0,
    val safeToSpend: Double = 0.0,
    val errorMessage: String? = null
)

/**
 * BalanceViewModel enforces the strict decoupled balance mathematical rules:
 *
 * 1. Actual Balance:
 *    - Tracks physical cash: Initial Balance + Total Income - Total Real Expenses.
 *    - Goal funding allocations and budget caps must NEVER reduce the Actual Balance.
 *
 * 2. Available Balance (Dedicated strictly to Goals):
 *    - Equals: Actual Balance - Total Saved Across Goals.
 *    - Active budget caps must NEVER reduce Available Balance.
 *
 * 3. Safe to Spend (Dedicated strictly to Budgets):
 *    - Equals: max(0.0, Total Monthly Budget Cap - Current Month Budgeted Expenses).
 *    - Goal savings must NEVER reduce Safe to Spend.
 */
class BalanceViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WealthWiseRepository = WealthWiseRepository(
        WealthWiseDatabase.getDatabase(application),
        application
    )
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BalanceUiState())
    val uiState: StateFlow<BalanceUiState> = _uiState.asStateFlow()

    init {
        combine(
            repository.allTransactions,
            repository.allBudgets,
            repository.allGoals
        ) { txns, budgets, goals ->
            val initial = repository.startingBankBalance
            val completedTxns = txns.filter { it.status.uppercase() != "FAILED" }

            val income = completedTxns
                .filter { FinancialCalculationEngine.determineEventType(it) == FinancialEventType.INCOME }
                .sumOf { kotlin.math.abs(it.amount) }

            val realExpenses = completedTxns
                .filter {
                    val type = FinancialCalculationEngine.determineEventType(it)
                    type == FinancialEventType.EXPENSE || type == FinancialEventType.SUBSCRIPTION_PAYMENT
                }
                .sumOf { kotlin.math.abs(it.amount) }

            val refunds = completedTxns
                .filter {
                    val type = FinancialCalculationEngine.determineEventType(it)
                    type == FinancialEventType.REFUND || type == FinancialEventType.REVERSAL
                }
                .sumOf { kotlin.math.abs(it.amount) }

            val netRealExpenses = max(0.0, realExpenses - refunds)

            // Rule 1: Actual Balance = Initial Balance + Total Income - Total Real Expenses
            val actual = calculateActualBalance(initial, income, netRealExpenses)

            // Total Saved Across Goals
            val totalSavedGoals = goals.sumOf { it.savedAmount }

            // Rule 2: Available Balance = max(0.0, Actual Balance - Total Saved Across Goals)
            // Active budget caps must NEVER reduce Available Balance.
            val available = calculateAvailableBalance(actual, totalSavedGoals)

            // Rule 3: Safe to Spend = max(0.0, Total Monthly Budget Cap - Current Month Budgeted Expenses)
            // Goal savings must NEVER reduce Safe to Spend.
            val budgetCap = budgets.sumOf { it.monthlyLimit }
            val safeSpend = calculateSafeToSpend(budgetCap, netRealExpenses)

            _uiState.update {
                it.copy(
                    initialBalance = initial,
                    totalIncome = income,
                    totalRealExpenses = netRealExpenses,
                    actualBalance = actual,
                    totalSavedAcrossGoals = totalSavedGoals,
                    availableBalance = available,
                    totalMonthlyBudgetCap = budgetCap,
                    currentMonthBudgetedExpenses = netRealExpenses,
                    safeToSpend = safeSpend
                )
            }
        }.launchIn(viewModelScope)
    }

    companion object {
        /**
         * Rule 1: Actual Balance = Initial Balance + Total Income - Total Real Expenses.
         * Goal funding and budget caps NEVER reduce Actual Balance.
         */
        fun calculateActualBalance(
            initialBalance: Double,
            totalIncome: Double,
            totalRealExpenses: Double
        ): Double {
            return initialBalance + totalIncome - totalRealExpenses
        }

        /**
         * Rule 2: Available Balance = max(0.0, Actual Balance - Total Saved Across Goals).
         * Active budget caps NEVER reduce Available Balance.
         */
        fun calculateAvailableBalance(
            actualBalance: Double,
            totalSavedAcrossGoals: Double
        ): Double {
            return max(0.0, actualBalance - totalSavedAcrossGoals)
        }

        /**
         * Rule 3: Safe to Spend = max(0.0, Total Monthly Budget Cap - Current Month Budgeted Expenses).
         * Goal savings NEVER reduce Safe to Spend.
         */
        fun calculateSafeToSpend(
            totalMonthlyBudgetCap: Double,
            currentMonthBudgetedExpenses: Double
        ): Double {
            return max(0.0, totalMonthlyBudgetCap - currentMonthBudgetedExpenses)
        }
    }
}
