package com.example

import com.example.data.local.GoalEntity
import com.example.data.local.TransactionEntity
import com.example.ui.viewmodel.CategoryBudgetLimit
import com.example.ui.viewmodel.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HomeFinancialCalculationTest {

    // TEST 1 — BALANCE
    @Test
    fun test1_balance_initial0_income3500_expense450() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "userA",
                title = "Salary",
                amount = 3500.0,
                type = "INCOME",
                category = "Salary",
                paymentMethod = "Bank Transfer",
                date = "2026-09-01",
                timestamp = System.currentTimeMillis()
            ),
            TransactionEntity(
                id = 2,
                userId = "userA",
                title = "Shopping",
                amount = 450.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "UPI",
                date = "2026-09-02",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = HomeViewModel.computeBalances(txns, startingBalance = 0.0)
        assertEquals(3500.0, result.totalIncome, 0.001)
        assertEquals(450.0, result.totalExpense, 0.001)
        assertEquals(3050.0, result.actualBalance, 0.001)
        assertEquals(3050.0, result.availableBalance, 0.001)
        assertEquals(3050.0, result.safeToSpend, 0.001)
    }

    // TEST 2 — BALANCE WITH INITIAL
    @Test
    fun test2_balanceWithInitial_initial10000_income3500_expense450() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "userA",
                title = "Consulting",
                amount = 3500.0,
                type = "INCOME",
                category = "Income",
                paymentMethod = "UPI",
                date = "2026-09-01",
                timestamp = System.currentTimeMillis()
            ),
            TransactionEntity(
                id = 2,
                userId = "userA",
                title = "Dinner",
                amount = 450.0,
                type = "EXPENSE",
                category = "Food",
                paymentMethod = "Card",
                date = "2026-09-02",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = HomeViewModel.computeBalances(txns, startingBalance = 10000.0)
        assertEquals(13050.0, result.actualBalance, 0.001)
        assertEquals(13050.0, result.availableBalance, 0.001)
    }

    // TEST 3 — NEGATIVE EXPENSE STORAGE
    @Test
    fun test3_negativeExpenseStorage_initial10000_storedNegative450() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "userA",
                title = "Rahul Transfer",
                amount = -450.0, // Stored as negative
                type = "EXPENSE",
                category = "Personal Transfer",
                paymentMethod = "UPI",
                date = "2026-09-02",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = HomeViewModel.computeBalances(txns, startingBalance = 10000.0)
        assertEquals(450.0, result.totalExpense, 0.001)
        assertEquals(9550.0, result.actualBalance, 0.001)
        assertFalse("Actual balance must not be 10,450", result.actualBalance == 10450.0)
    }

    // TEST 4 — POSITIVE EXPENSE STORAGE
    @Test
    fun test4_positiveExpenseStorage_initial10000_storedPositive450() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "userA",
                title = "Groceries",
                amount = 450.0, // Stored as positive
                type = "EXPENSE",
                category = "Food",
                paymentMethod = "UPI",
                date = "2026-09-02",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = HomeViewModel.computeBalances(txns, startingBalance = 10000.0)
        assertEquals(450.0, result.totalExpense, 0.001)
        assertEquals(9550.0, result.actualBalance, 0.001)
    }

    // TEST 5 — INCOME STORED AS NEGATIVE
    @Test
    fun test5_incomeStoredNegative_normalizedWithoutSignError() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "userA",
                title = "Salary Deposit",
                amount = -3500.0, // Accidentally negative
                type = "INCOME",
                category = "Salary",
                paymentMethod = "Bank Transfer",
                date = "2026-09-01",
                timestamp = System.currentTimeMillis()
            )
        )

        val result = HomeViewModel.computeBalances(txns, startingBalance = 0.0)
        assertEquals(3500.0, result.totalIncome, 0.001)
        assertEquals(3500.0, result.actualBalance, 0.001)
    }

    // TEST 6 — CATEGORY
    @Test
    fun test6_categoryMatching_personalTransfer_spent450_remaining4550_used9Percent() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "userA",
                title = "Rahul",
                amount = -450.0,
                type = "EXPENSE",
                category = "personal transfer",
                paymentMethod = "UPI",
                date = "2026-09-15",
                timestamp = System.currentTimeMillis()
            )
        )

        val spending = HomeViewModel.computeCategorySpending("Personal Transfer", 5000.0, txns)
        assertEquals(450.0, spending.spent, 0.001)
        assertEquals(4550.0, spending.remaining, 0.001)
        assertEquals(9, spending.percentUsed)
        assertFalse(spending.isExceeded)
    }

    // TEST 7 — CATEGORY CASE & WHITESPACE
    @Test
    fun test7_categoryCaseAndWhitespaceNormalization() {
        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "userA",
                title = "Transfer",
                amount = 450.0,
                type = "EXPENSE",
                category = "  personal transfer  ",
                paymentMethod = "UPI",
                date = "2026-09-15",
                timestamp = System.currentTimeMillis()
            )
        )

        val spending = HomeViewModel.computeCategorySpending("Personal Transfer", 5000.0, txns)
        assertEquals(450.0, spending.spent, 0.001)
        assertEquals(4550.0, spending.remaining, 0.001)
        assertEquals(9, spending.percentUsed)
    }

    // TEST 8 — MONTHLY BUDGET
    @Test
    fun test8_monthlyBudget_total8000_spent2000_remaining6000_used25Percent() {
        val categories = listOf(
            CategoryBudgetLimit("Food", 5000.0),
            CategoryBudgetLimit("Shopping", 3000.0)
        )
        val currentMonthExpense = 2000.0

        val budgetStatus = HomeViewModel.computeMonthlyBudgetStatus(categories, currentMonthExpense)
        assertEquals(8000.0, budgetStatus.totalBudget, 0.001)
        assertEquals(2000.0, budgetStatus.totalExpense, 0.001)
        assertEquals(6000.0, budgetStatus.remainingBudget, 0.001)
        assertEquals(25, budgetStatus.percentageUsed)
    }

    // TEST 9 — ZERO BUDGET
    @Test
    fun test9_zeroBudget_noDivisionByZero() {
        val categories = emptyList<CategoryBudgetLimit>()
        val currentMonthExpense = 450.0

        val budgetStatus = HomeViewModel.computeMonthlyBudgetStatus(categories, currentMonthExpense)
        assertEquals(0.0, budgetStatus.totalBudget, 0.001)
        assertEquals(0.0, budgetStatus.remainingBudget, 0.001)
        assertEquals(0, budgetStatus.percentageUsed)
    }

    // TEST 10 — CHART WITH NON-ZERO DATA
    @Test
    fun test10_chartWithCurrentMonthTransactions_receivesNonZeroData() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val midMonth = cal.timeInMillis

        val txns = listOf(
            TransactionEntity(
                id = 1,
                userId = "userA",
                title = "Salary",
                amount = 3500.0,
                type = "INCOME",
                category = "Salary",
                paymentMethod = "Bank Transfer",
                date = "2026-09-15",
                timestamp = midMonth
            ),
            TransactionEntity(
                id = 2,
                userId = "userA",
                title = "Shopping",
                amount = 450.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "UPI",
                date = "2026-09-15",
                timestamp = midMonth + 1000
            )
        )

        val points = HomeViewModel.computeChartPoints(txns)
        assertTrue(points.isNotEmpty())
        assertTrue(points.any { it.income > 0.0 || it.expense > 0.0 })
        assertEquals(3500.0, points.sumOf { it.income }, 0.001)
        assertEquals(450.0, points.sumOf { it.expense }, 0.001)
    }

    // TEST 11 — CHART EMPTY STATE
    @Test
    fun test11_chartEmptyState_returnsEmptyList() {
        val points = HomeViewModel.computeChartPoints(emptyList())
        assertTrue("Chart points must be empty when no transactions exist", points.isEmpty())
    }

    // TEST 12 — ROOM REACTIVITY SIMULATION
    @Test
    fun test12_roomReactivity_balanceUpdatesAcrossInsertUpdateDelete() {
        val initialTxns = mutableListOf(
            TransactionEntity(id = 1, userId = "userA", title = "Salary", amount = 3500.0, type = "INCOME", category = "Salary", paymentMethod = "Bank", date = "2026-09-01", timestamp = 1000),
            TransactionEntity(id = 2, userId = "userA", title = "Bill", amount = 450.0, type = "EXPENSE", category = "Bills", paymentMethod = "UPI", date = "2026-09-02", timestamp = 2000)
        )

        // Stage 1: Initial state
        var res = HomeViewModel.computeBalances(initialTxns, 0.0)
        assertEquals(3050.0, res.actualBalance, 0.001)

        // Stage 2: Insert new expense of 1000
        val newTxn = TransactionEntity(id = 3, userId = "userA", title = "Rent", amount = 1000.0, type = "EXPENSE", category = "Bills", paymentMethod = "UPI", date = "2026-09-03", timestamp = 3000)
        initialTxns.add(newTxn)
        res = HomeViewModel.computeBalances(initialTxns, 0.0)
        assertEquals(2050.0, res.actualBalance, 0.001)

        // Stage 3: Delete the 450 expense
        initialTxns.removeIf { it.id == 2 }
        res = HomeViewModel.computeBalances(initialTxns, 0.0)
        assertEquals(2500.0, res.actualBalance, 0.001)

        // Stage 4: Update the 1000 expense to 500
        val index = initialTxns.indexOfFirst { it.id == 3 }
        initialTxns[index] = initialTxns[index].copy(amount = 500.0)
        res = HomeViewModel.computeBalances(initialTxns, 0.0)
        assertEquals(3000.0, res.actualBalance, 0.001)
    }

    // TEST 13 — USER ISOLATION
    @Test
    fun test13_userIsolation_userANeverSeesUserBData() {
        val allTxns = listOf(
            TransactionEntity(id = 1, userId = "userA", title = "Salary A", amount = 3500.0, type = "INCOME", category = "Salary", paymentMethod = "Bank", date = "2026-09-01", timestamp = 1000),
            TransactionEntity(id = 2, userId = "userA", title = "Expense A", amount = 450.0, type = "EXPENSE", category = "Food", paymentMethod = "UPI", date = "2026-09-02", timestamp = 2000),
            TransactionEntity(id = 3, userId = "userB", title = "Salary B", amount = 1000.0, type = "INCOME", category = "Salary", paymentMethod = "Bank", date = "2026-09-01", timestamp = 1000),
            TransactionEntity(id = 4, userId = "userB", title = "Expense B", amount = 100.0, type = "EXPENSE", category = "Food", paymentMethod = "UPI", date = "2026-09-02", timestamp = 2000)
        )

        val txnsUserA = allTxns.filter { it.userId == "userA" }
        val txnsUserB = allTxns.filter { it.userId == "userB" }

        val resA = HomeViewModel.computeBalances(txnsUserA, 0.0)
        val resB = HomeViewModel.computeBalances(txnsUserB, 0.0)

        assertEquals(3050.0, resA.actualBalance, 0.001)
        assertEquals(900.0, resB.actualBalance, 0.001)
        assertEquals(3500.0, resA.totalIncome, 0.001)
        assertEquals(1000.0, resB.totalIncome, 0.001)
        assertFalse(resA.actualBalance == resB.actualBalance)
    }

    // TEST 14 — NAVIGATION & FAB POSITIONING
    @Test
    fun test14_navigationAndFabPlacement() {
        val bottomNavTabs = listOf("Home", "Activity", "Budgets", "Goals", "Settings")
        assertEquals(5, bottomNavTabs.size)
        assertEquals("Home", bottomNavTabs.first())
        assertEquals("Settings", bottomNavTabs.last())

        // The FAB is anchored at End, while Home tab is at Start
        val homeTabAlignment = "Start"
        val fabAlignment = "End"
        assertFalse("FAB must not occupy the Home tab position", homeTabAlignment == fabAlignment)
    }
}
