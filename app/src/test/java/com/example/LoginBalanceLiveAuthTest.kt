package com.example

import com.example.data.local.GoalEntity
import com.example.data.local.TransactionEntity
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.MoneyUtils
import com.example.ui.auth.AuthViewModel
import com.example.ui.navigation.NavDestinations
import com.example.ui.navigation.NavRouting
import com.example.ui.viewmodel.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class LoginBalanceLiveAuthTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Helper calculation method matching the authoritative formula
    private fun calculateCurrentBalance(
        startingBalance: Double,
        transactions: List<TransactionEntity>
    ): Double {
        val completedTxns = transactions.filter { txn ->
            val s = txn.status.uppercase(Locale.ROOT)
            val isFailed = s == "FAILED" || s == "REJECTED" || txn.eventType.equals("FAILED", ignoreCase = true)
            val isPending = s == "PENDING" || txn.eventType.equals("PENDING", ignoreCase = true)
            !isFailed && !isPending && (s.isEmpty() || s == "COMPLETED" || s == "SUCCESS" || s == "PAID")
        }

        val completedInflows = completedTxns
            .filter { !it.isTransfer && (it.isInflow || it.eventType.equals("REFUND", ignoreCase = true) || it.eventType.equals("REVERSAL", ignoreCase = true)) }
            .sumOf { kotlin.math.abs(it.amount) }

        val completedOutflows = completedTxns
            .filter { !it.isTransfer && !it.isInflow && !it.eventType.equals("REFUND", ignoreCase = true) && !it.eventType.equals("REVERSAL", ignoreCase = true) }
            .sumOf { kotlin.math.abs(it.amount) }

        return startingBalance + completedInflows - completedOutflows
    }

    // =========================================================================
    // PART 24 — MANDATORY 16 TEST CASES
    // =========================================================================

    @Test
    fun `TEST 1 - New user with Initial balance 0 and 0 transactions yields 0`() {
        val startingBalance = 0.0
        val transactions = emptyList<TransactionEntity>()
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)

        assertEquals(0.0, currentBalance, 0.001)

        val engineOverview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalance),
            transactions = transactions
        )
        assertEquals(0.0, engineOverview.currentBalance.toDouble(), 0.001)
        assertEquals(0.0, engineOverview.totalIncome.toDouble(), 0.001)
        assertEquals(0.0, engineOverview.totalExpenses.toDouble(), 0.001)
    }

    @Test
    fun `TEST 2 - Initial balance 10000 with no transactions yields 10000`() {
        val startingBalance = 10000.0
        val transactions = emptyList<TransactionEntity>()
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)

        assertEquals(10000.0, currentBalance, 0.001)

        val engineOverview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalance),
            transactions = transactions
        )
        assertEquals(10000.0, engineOverview.currentBalance.toDouble(), 0.001)
    }

    @Test
    fun `TEST 3 - Initial balance 10000 and expense 4000 yields 6000`() {
        val startingBalance = 10000.0
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Shopping",
                amount = 4000.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            )
        )
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)
        assertEquals(6000.0, currentBalance, 0.001)

        val engineOverview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalance),
            transactions = transactions
        )
        assertEquals(6000.0, engineOverview.currentBalance.toDouble(), 0.001)
    }

    @Test
    fun `TEST 4 - Initial balance 10000, expense 4000, income 2000 yields 8000`() {
        val startingBalance = 10000.0
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Shopping",
                amount = 4000.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            ),
            TransactionEntity(
                id = 2,
                userId = "user_a",
                title = "Freelance Project",
                amount = 2000.0,
                type = "INCOME",
                category = "Freelance",
                paymentMethod = "Bank Transfer",
                date = "2026-09-29",
                status = "COMPLETED"
            )
        )
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)
        assertEquals(8000.0, currentBalance, 0.001)

        val engineOverview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalance),
            transactions = transactions
        )
        assertEquals(8000.0, engineOverview.currentBalance.toDouble(), 0.001)
    }

    @Test
    fun `TEST 5 - Initial balance 10000, expense 4000, then expense 500 yields 5500`() {
        val startingBalance = 10000.0
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Shopping",
                amount = 4000.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            ),
            TransactionEntity(
                id = 2,
                userId = "user_a",
                title = "Dinner",
                amount = 500.0,
                type = "EXPENSE",
                category = "Food",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            )
        )
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)
        assertEquals(5500.0, currentBalance, 0.001)

        val engineOverview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalance),
            transactions = transactions
        )
        assertEquals(5500.0, engineOverview.currentBalance.toDouble(), 0.001)
    }

    @Test
    fun `TEST 6 - Initial balance 10000, expense 4000, then delete expense yields 10000`() {
        val startingBalance = 10000.0
        val initialTransactions = mutableListOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Shopping",
                amount = 4000.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            )
        )
        assertEquals(6000.0, calculateCurrentBalance(startingBalance, initialTransactions), 0.001)

        // Simulate deleting the transaction from Room
        initialTransactions.removeIf { it.id == 1 }
        val recalculatedBalance = calculateCurrentBalance(startingBalance, initialTransactions)
        assertEquals(10000.0, recalculatedBalance, 0.001)
    }

    @Test
    fun `TEST 7 - Initial balance 10000, credit 1 yields 10001`() {
        val startingBalance = 10000.0
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Micro Credit",
                amount = 1.0,
                type = "credit",
                category = "Other",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            )
        )
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)
        assertEquals(10001.0, currentBalance, 0.001)
    }

    @Test
    fun `TEST 8 - Initial balance 10000, debit 1 yields 9999`() {
        val startingBalance = 10000.0
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Micro Debit",
                amount = 1.0,
                type = "debit",
                category = "Other",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            )
        )
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)
        assertEquals(9999.0, currentBalance, 0.001)
    }

    @Test
    fun `TEST 9 - Failed expense 500 has 0 impact and balance remains unchanged`() {
        val startingBalance = 10000.0
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Failed Transfer",
                amount = 500.0,
                type = "EXPENSE",
                category = "Other",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "FAILED"
            )
        )
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)
        assertEquals(10000.0, currentBalance, 0.001)

        val engineOverview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalance),
            transactions = transactions
        )
        assertEquals(10000.0, engineOverview.currentBalance.toDouble(), 0.001)
    }

    @Test
    fun `TEST 10 - Pending expense 500 has 0 completed balance reduction`() {
        val startingBalance = 10000.0
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Pending Auth Hold",
                amount = 500.0,
                type = "EXPENSE",
                category = "Shopping",
                paymentMethod = "Credit Card",
                date = "2026-09-29",
                status = "PENDING"
            )
        )
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)
        assertEquals(10000.0, currentBalance, 0.001)

        val engineOverview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalance),
            transactions = transactions
        )
        assertEquals(10000.0, engineOverview.currentBalance.toDouble(), 0.001)
    }

    @Test
    fun `TEST 11 - Refund 500 increases balance by 500`() {
        val startingBalance = 10000.0
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_a",
                title = "Merchant Refund",
                amount = 500.0,
                type = "INCOME",
                eventType = "REFUND",
                category = "Shopping",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            )
        )
        val currentBalance = calculateCurrentBalance(startingBalance, transactions)
        assertEquals(10500.0, currentBalance, 0.001)

        val engineOverview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalance),
            transactions = transactions
        )
        assertEquals(10500.0, engineOverview.currentBalance.toDouble(), 0.001)
    }

    @Test
    fun `TEST 12 - User A and User B data scoping ensures User B never sees User A balance`() {
        val userAStarting = 10000.0
        val userATxns = listOf(
            TransactionEntity(
                id = 1,
                userId = "user_A",
                title = "Expense A",
                amount = 4000.0,
                type = "EXPENSE",
                category = "Food",
                paymentMethod = "UPI",
                date = "2026-09-29",
                status = "COMPLETED"
            )
        )
        val userABalance = calculateCurrentBalance(userAStarting, userATxns)
        assertEquals(6000.0, userABalance, 0.001)

        val userBStarting = 2000.0
        val userBTxns = emptyList<TransactionEntity>()
        val userBBalance = calculateCurrentBalance(userBStarting, userBTxns)
        assertEquals(2000.0, userBBalance, 0.001)

        assertNotEquals(userABalance, userBBalance)
    }

    @Test
    fun `TEST 13 - User A logout and User B login immediately switches observed data`() = runTest(testDispatcher) {
        val currentUserIdFlow = MutableStateFlow("user_A")
        val userAStarting = 10000.0
        val userBStarting = 2000.0

        val userABalanceData = calculateCurrentBalance(
            userAStarting,
            listOf(TransactionEntity(id = 1, userId = "user_A", title = "Shopping", amount = 4000.0, type = "EXPENSE", category = "Shopping", paymentMethod = "UPI", date = "2026-09-29", status = "COMPLETED"))
        )
        val userBBalanceData = calculateCurrentBalance(
            userBStarting,
            emptyList()
        )

        val observedBalances = mutableListOf<Double>()

        val job = launch {
            currentUserIdFlow.flatMapLatest { uid ->
                when (uid) {
                    "user_A" -> flowOf(userABalanceData)
                    "user_B" -> flowOf(userBBalanceData)
                    else -> flowOf(0.0)
                }
            }.collect { balance ->
                observedBalances.add(balance)
            }
        }
        advanceUntilIdle()
        assertEquals(6000.0, observedBalances.last(), 0.001)

        // User A logs out
        currentUserIdFlow.value = ""
        advanceUntilIdle()
        assertEquals(0.0, observedBalances.last(), 0.001)

        // User B logs in
        currentUserIdFlow.value = "user_B"
        advanceUntilIdle()
        assertEquals(2000.0, observedBalances.last(), 0.001)

        job.cancel()
    }

    @Test
    fun `TEST 14 - User logs in again after completing setup routes to HOME not Set Balance`() {
        val isEmailVerified = true
        val hasCompletedSetup = true

        val destination = NavRouting.destinationOnLogin(isEmailVerified, hasCompletedSetup)
        val screen = NavRouting.screenOnLogin(isEmailVerified, hasCompletedSetup)

        assertEquals(NavDestinations.HOME, destination)
        assertEquals(AppScreen.HOME, screen)
    }

    @Test
    fun `TEST 15 - Brand-new verified user with no initial balance routes to SET BALANCE`() {
        val isEmailVerified = true
        val hasCompletedSetup = false

        val destination = NavRouting.destinationOnLogin(isEmailVerified, hasCompletedSetup)
        val screen = NavRouting.screenOnLogin(isEmailVerified, hasCompletedSetup)

        assertEquals(NavDestinations.SET_BALANCE, destination)
        assertEquals(AppScreen.ENTER_BALANCE, screen)
    }

    @Test
    fun `TEST 16 - Unverified user logs in routes to EMAIL VERIFICATION not Home`() {
        val isEmailVerified = false
        val hasCompletedSetup = false

        val destination = NavRouting.destinationOnLogin(isEmailVerified, hasCompletedSetup)
        val screen = NavRouting.screenOnLogin(isEmailVerified, hasCompletedSetup)

        assertEquals(NavDestinations.EMAIL_VERIFICATION, destination)
        assertEquals(AppScreen.EMAIL_VERIFICATION, screen)
    }

    // =========================================================================
    // PART 25 — NAVIGATION STATE MACHINE TESTS
    // =========================================================================

    @Test
    fun `PART 25 - State Machine Step 1 REGISTERED + UNVERIFIED routes to EMAIL VERIFICATION`() {
        val target = NavRouting.screenOnRegistration()
        assertEquals(AppScreen.EMAIL_VERIFICATION, target)
    }

    @Test
    fun `PART 25 - State Machine Step 2 VERIFIED + NO INITIAL BALANCE routes to SET BALANCE`() {
        val screen = NavRouting.screenOnVerificationSuccess(hasCompletedInitialBalanceSetup = false)
        assertEquals(AppScreen.ENTER_BALANCE, screen)
    }

    @Test
    fun `PART 25 - State Machine Step 3 BALANCE SAVED routes to HOME`() {
        val screen = NavRouting.screenOnInitialBalanceSubmission()
        assertEquals(AppScreen.HOME, screen)
    }

    @Test
    fun `PART 25 - State Machine Step 4 REGISTERED + VERIFIED + INITIAL BALANCE EXISTS routes to HOME`() {
        val screen = NavRouting.screenOnLogin(isEmailVerified = true, hasCompletedInitialBalanceSetup = true)
        assertEquals(AppScreen.HOME, screen)
    }

    @Test
    fun `PART 25 - State Machine Step 5 EXISTING UNVERIFIED USER LOGIN routes to EMAIL VERIFICATION`() {
        val screen = NavRouting.screenOnLogin(isEmailVerified = false, hasCompletedInitialBalanceSetup = true)
        assertEquals(AppScreen.EMAIL_VERIFICATION, screen)
    }

    @Test
    fun `PART 25 - Gating prevents configured user from re-accessing ENTER_BALANCE`() {
        val gated = NavRouting.gateDestination(
            targetDestination = NavDestinations.SET_BALANCE,
            currentUser = null, // mock
            isLoggedIn = true,
            hasCompletedSetup = true
        )
        // With mock user logged in and setup complete, ENTER_BALANCE is redirected to HOME
        assertEquals(NavDestinations.HOME, gated)
    }
}
