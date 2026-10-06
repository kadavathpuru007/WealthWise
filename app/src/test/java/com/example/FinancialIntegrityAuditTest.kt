package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialValidationEngine
import com.example.domain.financial.MoneyUtils
import com.example.domain.financial.ValidationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinancialIntegrityAuditTest {

    private lateinit var database: WealthWiseDatabase
    private lateinit var repository: WealthWiseRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WealthWiseRepository(database, context)
        repository.startingBankBalance = 10000.0
        repository.isLoggedIn = true
        repository.hasCompletedSetup = true
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -------------------------------------------------------------------------
    // 33.1: DO NOT SILENTLY ABS FINANCIAL INPUT
    // -------------------------------------------------------------------------
    @Test
    fun `test non-positive transaction amounts are rejected by repository and engine`() = runBlocking {
        // Engine validation must block negative amount
        val validationNeg = FinancialValidationEngine.validateTransaction(
            amount = BigDecimal("-500.00"),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Store",
            currentBalance = BigDecimal("10000.00")
        )
        assertTrue("Negative amount must be blocked", validationNeg is ValidationResult.Blocked)

        // Engine validation must block zero amount
        val validationZero = FinancialValidationEngine.validateTransaction(
            amount = BigDecimal("0.00"),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Store",
            currentBalance = BigDecimal("10000.00")
        )
        assertTrue("Zero amount must be blocked", validationZero is ValidationResult.Blocked)

        // Repository insertion must NOT silently repair negative amount - it must throw IllegalArgumentException
        try {
            repository.insertTransaction(
                TransactionEntity(
                    title = "Invalid Txn",
                    amount = -500.0,
                    type = "EXPENSE",
                    category = "Food",
                    paymentMethod = "UPI",
                    date = "2026-09-28"
                )
            )
            fail("Repository must reject negative amount with IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }

        // Repository insertion must reject zero amount
        try {
            repository.insertTransaction(
                TransactionEntity(
                    title = "Zero Txn",
                    amount = 0.0,
                    type = "EXPENSE",
                    category = "Food",
                    paymentMethod = "UPI",
                    date = "2026-09-28"
                )
            )
            fail("Repository must reject zero amount with IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }

        // Positive transaction accepted
        repository.insertTransaction(
            TransactionEntity(
                title = "Salary Deposit",
                amount = 25000.0,
                type = "INCOME",
                category = "Salary",
                paymentMethod = "Bank Transfer",
                date = "2026-09-28"
            )
        )
        val allTx = database.transactionDao().getAllTransactions().first()
        assertEquals(1, allTx.size)
        assertEquals(25000.0, allTx[0].amount, 0.001)

        // Verify credit increases balance
        val overviewCredit = com.example.domain.financial.FinancialCalculationEngine.calculateOverview(
            startingBalance = BigDecimal("10000.00"),
            transactions = allTx
        )
        assertEquals(35000.0, overviewCredit.currentBalance.toDouble(), 0.001)

        // Debit decreases balance
        repository.insertTransaction(
            TransactionEntity(
                title = "Groceries",
                amount = 2000.0,
                type = "EXPENSE",
                category = "Food",
                paymentMethod = "UPI",
                date = "2026-09-28"
            )
        )
        val allTxAfterDebit = database.transactionDao().getAllTransactions().first()
        val overviewDebit = com.example.domain.financial.FinancialCalculationEngine.calculateOverview(
            startingBalance = BigDecimal("10000.00"),
            transactions = allTxAfterDebit
        )
        assertEquals(33000.0, overviewDebit.currentBalance.toDouble(), 0.001)
    }

    // -------------------------------------------------------------------------
    // 33.6: BILL PAYMENT SAFETY
    // -------------------------------------------------------------------------
    @Test
    fun `test bill payment safety - already paid bill cannot be paid again`() = runBlocking {
        database.billDao().insertBill(
            BillEntity(
                id = 1,
                title = "Electricity",
                amount = 1500.0,
                dueDate = "2026-10-05",
                category = "Electricity",
                isPaid = false
            )
        )

        // First payment succeeds
        repository.payBill(1)

        val billAfter = database.billDao().getBillById(1)
        assertNotNull(billAfter)
        assertTrue(billAfter!!.isPaid)

        // Second payment must throw IllegalStateException (already paid)
        try {
            repository.payBill(1)
            fail("Paying an already paid bill must fail")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("already") == true)
        }
    }

    @Test
    fun `test bill payment safety - insufficient balance blocks bill payment`() = runBlocking {
        repository.startingBankBalance = 100.0 // Only ₹100 available
        database.billDao().insertBill(
            BillEntity(
                id = 2,
                title = "Rent",
                amount = 15000.0,
                dueDate = "2026-10-01",
                category = "Rent",
                isPaid = false
            )
        )

        val validation = FinancialValidationEngine.validateBillPayment(
            bill = database.billDao().getBillById(2)!!,
            availableBalance = BigDecimal("100.00")
        )
        assertTrue(validation is ValidationResult.Blocked)

        try {
            repository.payBill(2)
            fail("Repository must reject paying bill with insufficient balance")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Insufficient balance") == true)
        }

        // Bill must remain unpaid and no transaction logged
        val bill = database.billDao().getBillById(2)
        assertFalse(bill!!.isPaid)
        val txns = database.transactionDao().getAllTransactions().first()
        assertTrue(txns.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 33.7: GOAL CONTRIBUTION SAFETY
    // -------------------------------------------------------------------------
    @Test
    fun `test goal funding safety - cannot fund fully funded goal or with insufficient balance`() = runBlocking {
        database.goalDao().insertGoal(
            GoalEntity(
                id = 1,
                title = "Car Fund",
                targetAmount = 5000.0,
                savedAmount = 5000.0 // already fully funded
            )
        )

        val validation = FinancialValidationEngine.validateGoalContribution(
            goal = database.goalDao().getGoalById(1)!!,
            contributionAmount = BigDecimal("500.00"),
            availableBalance = BigDecimal("10000.00")
        )
        assertTrue("Fully funded goal must be blocked", validation is ValidationResult.Blocked)

        try {
            repository.fundGoal(1, 500.0)
            fail("Repository must reject funding already completed goal")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("reached its target") == true)
        }
    }

    @Test
    fun `test goal funding atomicity - successful contribution updates goal and inserts ledger transaction`() = runBlocking {
        database.goalDao().insertGoal(
            GoalEntity(
                id = 2,
                title = "Emergency Fund",
                targetAmount = 20000.0,
                savedAmount = 2000.0
            )
        )

        repository.fundGoal(2, 3000.0)

        // Verify goal updated
        val updatedGoal = database.goalDao().getGoalById(2)
        assertEquals(5000.0, updatedGoal!!.savedAmount, 0.001)

        // Verify ledger transaction created
        val txns = database.transactionDao().getAllTransactions().first()
        assertEquals(1, txns.size)
        assertTrue(txns[0].title == "Funded Goal: Emergency Fund" || txns[0].title == "Goal Contribution: Emergency Fund")
        assertEquals(3000.0, txns[0].amount, 0.001)
        assertTrue(txns[0].eventType == "GOAL_TRANSFER" || txns[0].eventType == "GOAL_ALLOCATION")
    }

    // -------------------------------------------------------------------------
    // 33.8: LOAN PAYMENT SAFETY
    // -------------------------------------------------------------------------
    @Test
    fun `test loan payment safety - overpayment and fully repaid loans are blocked`() = runBlocking {
        database.loanDao().insertLoan(
            LoanEntity(
                id = 1,
                title = "Bike Loan",
                lender = "HDFC Bank",
                principal = 50000.0,
                remaining = 2000.0,
                interestRate = 10.0,
                monthlyEmi = 2000.0,
                nextDueDate = "2026-10-10",
                tenureMonthsLeft = 1
            )
        )

        // Overpayment test: attempt to pay ₹5,000 when remaining is only ₹2,000
        val overpaymentVal = FinancialValidationEngine.validateLoanPayment(
            loan = database.loanDao().getLoanById(1)!!,
            paymentAmount = BigDecimal("5000.00"),
            availableBalance = BigDecimal("10000.00")
        )
        assertTrue("Overpayment beyond remaining principal must be blocked", overpaymentVal is ValidationResult.Blocked)

        try {
            repository.recordEmiPayment(1, 5000.0)
            fail("Repository must reject payment exceeding loan remaining principal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("exceeds remaining principal") == true)
        }

        // Pay exact ₹2000
        repository.recordEmiPayment(1, 2000.0)
        val loanAfter = database.loanDao().getLoanById(1)
        assertEquals(0.0, loanAfter!!.remaining, 0.001)

        // Now that loan is fully repaid, subsequent payment must be blocked
        val repaidVal = FinancialValidationEngine.validateLoanPayment(
            loan = database.loanDao().getLoanById(1)!!,
            paymentAmount = BigDecimal("500.00"),
            availableBalance = BigDecimal("10000.00")
        )
        assertTrue("Payment on fully repaid loan must be blocked", repaidVal is ValidationResult.Blocked)
    }

    // -------------------------------------------------------------------------
    // 33.3: USER DATA ISOLATION
    // -------------------------------------------------------------------------
    @Test
    fun `test user data isolation - clearUserLocalData purges all financial tables and reset session`() = runBlocking {
        // Seed diverse records
        repository.insertTransaction(
            TransactionEntity(title = "Coffee", amount = 120.0, type = "EXPENSE", category = "Food", paymentMethod = "UPI", date = "2026-09-28")
        )
        database.goalDao().insertGoal(
            GoalEntity(id = 1, title = "Vacation", targetAmount = 50000.0, savedAmount = 1000.0)
        )
        database.billDao().insertBill(
            BillEntity(id = 1, title = "Internet", amount = 999.0, dueDate = "2026-10-01", category = "Broadband", isPaid = false)
        )
        database.subscriptionDao().insertSubscription(
            SubscriptionEntity(id = 1, name = "Netflix", amount = 649.0)
        )

        // Verify data exists
        assertEquals(1, database.transactionDao().getAllTransactions().first().size)
        assertEquals(1, database.goalDao().getAllGoals().first().size)
        assertEquals(1, database.billDao().getAllBills().first().size)
        assertEquals(1, database.subscriptionDao().getAllSubscriptions().first().size)

        // Clear user local data
        repository.clearUserLocalData()

        // Verify completely wiped
        assertEquals(0, database.transactionDao().getAllTransactions().first().size)
        assertEquals(0, database.goalDao().getAllGoals().first().size)
        assertEquals(0, database.billDao().getAllBills().first().size)
        assertEquals(0, database.subscriptionDao().getAllSubscriptions().first().size)
        assertEquals(0.0, repository.startingBankBalance, 0.001)
        assertFalse(repository.isLoggedIn)
        assertFalse(repository.hasCompletedSetup)
    }

    // -------------------------------------------------------------------------
    // 33.10: DATABASE REPLACEMENT SAFETY
    // -------------------------------------------------------------------------
    @Test
    fun `test database replacement safety - inserting duplicate ID aborts rather than silently overwriting`() = runBlocking {
        database.goalDao().insertGoal(
            GoalEntity(id = 10, title = "Original Goal", targetAmount = 10000.0, savedAmount = 4000.0)
        )

        // Attempting to insert another goal with same ID must fail (OnConflictStrategy.ABORT)
        try {
            database.goalDao().insertGoal(
                GoalEntity(id = 10, title = "Malicious Overwrite", targetAmount = 20000.0, savedAmount = 0.0)
            )
            fail("Insert collision with OnConflictStrategy.ABORT must throw exception")
        } catch (_: Exception) {
            // Expected
        }

        // Original goal must be preserved intact
        val original = database.goalDao().getGoalById(10)
        assertEquals("Original Goal", original!!.title)
        assertEquals(4000.0, original.savedAmount, 0.001)
    }

    // -------------------------------------------------------------------------
    // SECTION 1 & 2: CRITICAL AUTHENTICATION & DEMO INTEGRITY
    // -------------------------------------------------------------------------
    @Test
    fun `test authentication - empty credentials and invalid credentials throw error without fallback`() = runBlocking {
        val result = com.example.data.firebase.FirebaseManager.loginUser("", "")
        assertTrue("Blank credentials must fail", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex?.message?.contains("empty") == true)
    }

    @Test
    fun `test authentication - demo email is not given automatic fallback session`() = runBlocking {
        val result = com.example.data.firebase.FirebaseManager.loginUser("demo@wealthwise.com", "random_wrong_pass")
        // In Robolectric environment with no live internet/backend, it fails with error rather than succeeding as demo
        assertTrue("Unauthenticated demo email must fail rather than automatically logging in as Puru (Demo)", result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
    }
}
