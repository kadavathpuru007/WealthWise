package com.example

import com.example.data.local.BudgetEntity
import com.example.data.local.GoalEntity
import com.example.data.local.TransactionEntity
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.MoneyUtils
import com.example.ui.navigation.NavDestinations
import com.example.ui.navigation.NavRouting
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.BalanceViewModel
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class MultiUserAndDecoupledBalanceTest {

    // =========================================================================
    // 1. STRICT BALANCE DECOUPLING TESTS
    // =========================================================================

    @Test
    fun `Rule 1 - Actual Balance tracks physical cash and is never reduced by goal funding or budget caps`() {
        val initialBalance = 50000.0
        val totalIncome = 15000.0
        val totalRealExpenses = 8000.0

        val actual = BalanceViewModel.calculateActualBalance(initialBalance, totalIncome, totalRealExpenses)
        assertEquals(57000.0, actual, 0.001)

        // Ensure with FinancialCalculationEngine
        val starting = MoneyUtils.of(initialBalance)
        val incomeTx = TransactionEntity(
            id = 1,
            title = "Salary",
            amount = 15000.0,
            type = "INCOME",
            category = "Salary",
            paymentMethod = "Bank Transfer",
            date = "2026-09-29",
            eventType = FinancialEventType.INCOME.name
        )
        val expenseTx = TransactionEntity(
            id = 2,
            title = "Groceries",
            amount = 8000.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-29",
            eventType = FinancialEventType.EXPENSE.name
        )
        val goalSavingTx = TransactionEntity(
            id = 3,
            title = "Goal Contribution",
            amount = 5000.0,
            type = "SAVINGS",
            category = "Goal Allocation",
            paymentMethod = "Bank Transfer",
            date = "2026-09-29",
            eventType = FinancialEventType.GOAL_ALLOCATION.name
        )
        val budgets = listOf(BudgetEntity(category = "All", monthlyLimit = 25000.0))
        val goals = listOf(GoalEntity(id = 1, title = "Emergency Fund", targetAmount = 50000.0, savedAmount = 5000.0))

        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = starting,
            transactions = listOf(incomeTx, expenseTx, goalSavingTx),
            budgets = budgets,
            goals = goals
        )

        // Physical cash: 50,000 + 15,000 - 8,000 = 57,000. Goal funding & 25,000 budget cap do NOT reduce actual balance.
        assertEquals(BigDecimal("57000.00"), overview.currentBalance)
    }

    @Test
    fun `Rule 2 - Available Balance is strictly dedicated to Goals and active budget caps never reduce it`() {
        val actualBalance = 57000.0
        val totalSavedGoals = 12000.0

        val available = BalanceViewModel.calculateAvailableBalance(actualBalance, totalSavedGoals)
        assertEquals(45000.0, available, 0.001)

        // Verify with engine
        val starting = MoneyUtils.of(57000.0)
        val goal = GoalEntity(id = 1, title = "Trip", targetAmount = 20000.0, savedAmount = 12000.0)
        // High budget cap of 40,000
        val budgets = listOf(BudgetEntity(category = "Overall", monthlyLimit = 40000.0))

        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = starting,
            transactions = emptyList(),
            budgets = budgets,
            goals = listOf(goal)
        )

        // Available Balance = 57,000 - 12,000 = 45,000. (40,000 budget cap does NOT reduce available balance)
        assertEquals(BigDecimal("45000.00"), overview.availableBalance)
    }

    @Test
    fun `Rule 3 - Safe to Spend is strictly dedicated to Budgets and goal savings never reduce it`() {
        val budgetCap = 20000.0
        val currentMonthExpenses = 6000.0

        val safe = BalanceViewModel.calculateSafeToSpend(budgetCap, currentMonthExpenses)
        assertEquals(14000.0, safe, 0.001)

        // Verify with engine when goals exist
        val starting = MoneyUtils.of(50000.0)
        val expenseTx = TransactionEntity(
            id = 1,
            title = "Dining",
            amount = 6000.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-29",
            eventType = FinancialEventType.EXPENSE.name
        )
        val budgets = listOf(BudgetEntity(category = "Food", monthlyLimit = 20000.0))
        val bigGoal = GoalEntity(id = 1, title = "House", targetAmount = 500000.0, savedAmount = 40000.0)

        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = starting,
            transactions = listOf(expenseTx),
            budgets = budgets,
            goals = listOf(bigGoal)
        )

        // Safe to spend = 20,000 - 6,000 = 14,000. Goal savings of 40,000 NEVER reduce Safe to Spend.
        assertEquals(BigDecimal("14000.00"), overview.safeToSpend)
    }

    // =========================================================================
    // 2. NAVIGATION ROUTING & ONBOARDING RESTRICTION TESTS
    // =========================================================================

    @Test
    fun `On Registration routes to EmailVerificationScreen then on verification success routes to SetBalanceScreen`() {
        assertEquals(NavDestinations.EMAIL_VERIFICATION, NavRouting.destinationOnRegistration())
        assertEquals(AppScreen.EMAIL_VERIFICATION, NavRouting.screenOnRegistration())
        assertEquals(NavDestinations.SET_BALANCE, NavRouting.destinationOnVerificationSuccess(hasCompletedInitialBalanceSetup = false))
        assertEquals(AppScreen.ENTER_BALANCE, NavRouting.screenOnVerificationSuccess(hasCompletedInitialBalanceSetup = false))
    }

    @Test
    fun `On Initial Balance Submission routes to HomeScreen`() {
        assertEquals(NavDestinations.HOME, NavRouting.destinationOnInitialBalanceSubmission())
        assertEquals(AppScreen.HOME, NavRouting.screenOnInitialBalanceSubmission())
    }

    @Test
    fun `On Login when initial balance is already completed routes directly to HomeScreen`() {
        val destination = NavRouting.destinationOnLogin(hasCompletedInitialBalanceSetup = true)
        val screen = NavRouting.screenOnLogin(hasCompletedInitialBalanceSetup = true)

        assertEquals(NavDestinations.HOME, destination)
        assertEquals(AppScreen.HOME, screen)
    }

    @Test
    fun `On Login when user has never set initial balance routes to SetBalanceScreen`() {
        val destination = NavRouting.destinationOnLogin(hasCompletedInitialBalanceSetup = false)
        val screen = NavRouting.screenOnLogin(hasCompletedInitialBalanceSetup = false)

        assertEquals(NavDestinations.SET_BALANCE, destination)
        assertEquals(AppScreen.ENTER_BALANCE, screen)
    }

    // =========================================================================
    // 3. MULTI-USER DATA SCOPING ENTITY TESTS
    // =========================================================================

    @Test
    fun `Room entities support userId scoping with backwards-compatible defaults`() {
        val user1Tx = TransactionEntity(
            userId = "user_abc",
            title = "Coffee",
            amount = 150.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-29"
        )
        val user2Tx = TransactionEntity(
            userId = "user_xyz",
            title = "Book",
            amount = 450.0,
            type = "EXPENSE",
            category = "Shopping",
            paymentMethod = "Card",
            date = "2026-09-29"
        )

        assertEquals("user_abc", user1Tx.userId)
        assertEquals("user_xyz", user2Tx.userId)
        assertNotEquals(user1Tx.userId, user2Tx.userId)

        val budget = BudgetEntity(userId = "user_abc", category = "Food & Dining", monthlyLimit = 10000.0)
        assertEquals("user_abc", budget.userId)
        assertEquals("Food & Dining", budget.category)

        val goal = GoalEntity(userId = "user_abc", title = "Bike", targetAmount = 100000.0, savedAmount = 25000.0)
        assertEquals("user_abc", goal.userId)
    }
}
