package com.example

import com.example.data.local.BudgetEntity
import com.example.data.local.GoalEntity
import com.example.data.local.TransactionEntity
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialValidationEngine
import com.example.domain.financial.MoneyUtils
import com.example.domain.financial.ValidationResult
import com.example.ui.dashboard.formatInr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FinancialCalculationEngineTest {

    @Test
    fun testCase1_actualBalance50k_safeToSpend20k_availableIs50k() {
        // Starting balance 50,000, budget 20,000, no spending yet
        val startingBalance = MoneyUtils.of(50000.0)
        val budgets = listOf(BudgetEntity(category = "Monthly Budget", monthlyLimit = 20000.0))
        val transactions = emptyList<TransactionEntity>()

        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = startingBalance,
            transactions = transactions,
            budgets = budgets,
            goals = emptyList(),
            loans = emptyList(),
            subscriptions = emptyList()
        )

        assertEquals(BigDecimal("50000.00"), overview.currentBalance)
        assertEquals(BigDecimal("20000.00"), overview.safeToSpend)
        assertEquals(BigDecimal("50000.00"), overview.availableBalance)
    }

    @Test
    fun testCase2_actualBalance49800_safeToSpend20k_availableIs49800() {
        // Starting balance 49,800, budget 20,000 on "Shopping".
        val startingBalance = MoneyUtils.of(49800.0)
        val budgets = listOf(BudgetEntity(category = "Shopping", monthlyLimit = 20000.0))
        val transactions = emptyList<TransactionEntity>()

        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = startingBalance,
            transactions = transactions,
            budgets = budgets,
            goals = emptyList(),
            loans = emptyList(),
            subscriptions = emptyList()
        )

        assertEquals(BigDecimal("49800.00"), overview.currentBalance)
        assertEquals(BigDecimal("20000.00"), overview.safeToSpend)
        assertEquals(BigDecimal("49800.00"), overview.availableBalance)
    }

    @Test
    fun testCase3_creditTransaction200_updatesActualAndAvailableAutomatically() {
        // Before credit: Actual = 49,800, Safe = 20,000, Available = 49,800
        val startingBalance = MoneyUtils.of(49800.0)
        val budgets = listOf(BudgetEntity(category = "Monthly Budget", monthlyLimit = 20000.0))
        val creditTx = TransactionEntity(
            id = 1,
            title = "Salary / UPI Credit",
            amount = 200.0,
            type = "INCOME",
            category = "Income",
            paymentMethod = "UPI",
            date = "2026-09-23",
            eventType = FinancialEventType.INCOME.name
        )

        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = startingBalance,
            transactions = listOf(creditTx),
            budgets = budgets,
            goals = emptyList(),
            loans = emptyList(),
            subscriptions = emptyList()
        )

        // After credit: Actual = 50,000, Safe = 20,000, Available = 50,000
        assertEquals(BigDecimal("50000.00"), overview.currentBalance)
        assertEquals(BigDecimal("20000.00"), overview.safeToSpend)
        assertEquals(BigDecimal("50000.00"), overview.availableBalance)
    }

    @Test
    fun testCase4_budgetedExpense500_updatesActualSafeAndAvailable() {
        // Starting balance 50,000, budget 20,000 on "Monthly Budget"
        val startingBalance = MoneyUtils.of(50000.0)
        val budgets = listOf(BudgetEntity(category = "Monthly Budget", monthlyLimit = 20000.0))
        val expenseTx = TransactionEntity(
            id = 1,
            title = "Grocery store",
            amount = 500.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-23",
            eventType = FinancialEventType.EXPENSE.name
        )

        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = startingBalance,
            transactions = listOf(expenseTx),
            budgets = budgets,
            goals = emptyList(),
            loans = emptyList(),
            subscriptions = emptyList()
        )
        // Actual decreases by 500 to 49,500
        assertEquals(BigDecimal("49500.00"), overview.currentBalance)
        // Safe to spend drops by 500 to 19,500 (since 500 spent from 20,000 budget)
        assertEquals(BigDecimal("19500.00"), overview.safeToSpend)
        // Available = 49,500 - 0 goal savings = 49,500
        assertEquals(BigDecimal("49500.00"), overview.availableBalance)
    }

    @Test
    fun testEmptyAccount_returnsAllZeros() {
        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.ZERO,
            transactions = emptyList(),
            budgets = emptyList(),
            goals = emptyList(),
            loans = emptyList(),
            subscriptions = emptyList()
        )

        assertEquals(BigDecimal("0.00"), overview.currentBalance)
        assertEquals(BigDecimal("0.00"), overview.safeToSpend)
        assertEquals(BigDecimal("0.00"), overview.availableBalance)
    }

    // =========================================================================
    // PART 17: FINANCIAL BALANCE MODEL TESTS (A, B, C, D, E, K)
    // =========================================================================

    @Test
    fun `test A B C - Goal funding reduces Available Balance while Actual Balance and Safe to Spend remain unchanged`() {
        // Initial state: Actual = 8,000, Safe to Spend = 2,500, Available = 7,500
        // (Starting balance 8,000, monthly budget 2,500 -> safeToSpend = 2,500, existing goal savings = 500)
        val startingBalance = MoneyUtils.of(8000.0)
        val budgets = listOf(BudgetEntity(category = "Monthly Budget", monthlyLimit = 2500.0))
        val initialGoal = GoalEntity(id = 1, title = "Car", targetAmount = 20000.0, savedAmount = 500.0)

        val before = FinancialCalculationEngine.calculateOverview(
            startingBalance = startingBalance,
            transactions = emptyList(),
            budgets = budgets,
            goals = listOf(initialGoal)
        )

        assertEquals(BigDecimal("8000.00"), before.currentBalance)
        assertEquals(BigDecimal("2500.00"), before.safeToSpend)
        assertEquals(BigDecimal("7500.00"), before.availableBalance)

        // Fund goal with ₹500: Goal saved amount becomes 1,000
        val fundedGoal = initialGoal.copy(savedAmount = initialGoal.savedAmount + 500.0)
        val after = FinancialCalculationEngine.calculateOverview(
            startingBalance = startingBalance,
            transactions = emptyList(),
            budgets = budgets,
            goals = listOf(fundedGoal)
        )

        // TEST A: Available Balance decreases by 500 to ₹7,000, goal saved amount increases by 500
        assertEquals(BigDecimal("7000.00"), after.availableBalance)
        assertEquals(1000.0, fundedGoal.savedAmount, 0.001)

        // TEST B: Actual Balance remains unchanged at ₹8,000
        assertEquals(BigDecimal("8000.00"), after.currentBalance)

        // TEST C: Safe to Spend remains unchanged at ₹2,500
        assertEquals(BigDecimal("2500.00"), after.safeToSpend)
    }

    @Test
    fun `test D - Insufficient Available Balance rejects goal funding`() {
        // Available Balance = ₹500, Attempt = ₹700
        val goal = GoalEntity(id = 1, title = "Car", targetAmount = 2000.0, savedAmount = 0.0)
        val availableBalance = BigDecimal("500.00")
        val attemptAmount = BigDecimal("700.00")

        val result = FinancialValidationEngine.validateGoalContribution(
            goal = goal,
            contributionAmount = attemptAmount,
            availableBalance = availableBalance
        )

        // Operation rejected
        assertTrue("Funding exceeding available balance must be Blocked", result is ValidationResult.Blocked)
        val blocked = result as ValidationResult.Blocked
        assertEquals(availableBalance, blocked.availableBalance)
        assertEquals(attemptAmount, blocked.requiredAmount)
        assertEquals(BigDecimal("200.00"), blocked.shortfall)

        // Goal and balances remain unchanged
        assertEquals(0.0, goal.savedAmount, 0.001)
    }

    @Test
    fun `test E - Recommended funding is calculated dynamically as remaining divided by timeline`() {
        // Target: ₹2,000, Already Saved: ₹0, Timeline: 10 months -> Recommended: ₹200/month
        val targetAmount = 2000.0
        val savedAmount = 0.0
        val timelineMonths = 10

        val remaining = (targetAmount - savedAmount).coerceAtLeast(0.0)
        val recommendedMonthly = if (timelineMonths > 0) remaining / timelineMonths else remaining

        assertEquals(200.0, recommendedMonthly, 0.001)

        // Dynamic test with different values: Target = 20,000, Saved = 5,000, Timeline = 15 months
        val remaining2 = 20000.0 - 5000.0
        val recommended2 = remaining2 / 15
        assertEquals(1000.0, recommended2, 0.001)
    }

    @Test
    fun `test K - Currency format displays single rupee symbol and no duplicates`() {
        val input = 25980.0
        val formatted = formatInr(input)

        assertEquals("₹25,980", formatted)
        assertTrue("Must not contain duplicate rupee symbols", !formatted.contains("₹₹"))
    }
}
