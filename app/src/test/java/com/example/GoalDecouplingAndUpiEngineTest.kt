package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.MoneyUtils
import com.wealthwise.app.utils.UpiPaymentHelper
import com.wealthwise.app.utils.UpiTargetApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verification test suite for:
 * 1. Goal-to-Budget Decoupling (Savings goal funding treated exclusively as asset allocation, NOT monthly expense)
 * 2. Fee-Free UPI Intent & Google Pay Payment Engine (NPCI deep linking, payee resolution, callback & SHA-256 fingerprinting)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalDecouplingAndUpiEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: WealthWiseDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var goalDao: GoalDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var repository: WealthWiseRepository

    private val testUserId = "test_user_financial_777"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        transactionDao = database.transactionDao()
        goalDao = database.goalDao()
        budgetDao = database.budgetDao()
        repository = WealthWiseRepository(database, context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ============================================================
    // 1. FINANCIAL LOGIC: GOAL-TO-BUDGET DECOUPLING
    // ============================================================

    @Test
    fun testGoalFunding_updatesTargetGoalSavedBalanceOnly_andLogsGoalTransfer() = runTest(testDispatcher) {
        // Set initial starting balance of ₹50,000
        repository.setInitialBalance(50000.0, testUserId)

        // Create a Savings Goal: "Car" target ₹1,00,000 with current saved ₹10,000
        val goal = GoalEntity(
            id = 55L,
            userId = testUserId,
            title = "Car",
            targetAmount = 100000.0,
            timelineMonths = 12,
            savedAmount = 10000.0,
            createdAt = System.currentTimeMillis()
        )
        goalDao.insertGoal(goal)

        // Create Monthly Budgets for "Bills" and "Shopping" with limits
        budgetDao.insertOrUpdate(
            BudgetEntity(
                userId = testUserId,
                category = "Bills",
                monthlyLimit = 15000.0
            )
        )
        budgetDao.insertOrUpdate(
            BudgetEntity(
                userId = testUserId,
                category = "Shopping",
                monthlyLimit = 10000.0
            )
        )

        // Fund the goal with ₹2,500 (e.g. quick-add chip)
        repository.fundGoal(55L, 2500.0, testUserId)

        // 1. Verify target goal's saved balance updated to ₹12,500
        val updatedGoal = goalDao.getGoalById(55L)
        assertNotNull(updatedGoal)
        assertEquals(12500.0, updatedGoal!!.savedAmount, 0.001)

        // 2. Verify transaction record logged in Activity ledger with GOAL_TRANSFER
        val txns = transactionDao.getAllTransactions(testUserId).first()
        val goalTxn = txns.firstOrNull { it.type == "GOAL_TRANSFER" }
        assertNotNull("Expected a transaction record with GOAL_TRANSFER", goalTxn)
        assertEquals("GOAL_TRANSFER", goalTxn!!.type)
        assertEquals(FinancialEventType.GOAL_TRANSFER.name, goalTxn.eventType)
        assertEquals(2500.0, goalTxn.amount, 0.001)
        assertTrue(goalTxn.isGoalTransfer)
        assertFalse("Goal transfer must not be flagged as operational outflow", goalTxn.isOutflow)

        // 3. Verify FinancialCalculationEngine does NOT increment operational expenses
        val allBudgets = budgetDao.getAllBudgets(testUserId).first()
        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(50000.0),
            transactions = txns,
            budgets = allBudgets
        )
        // Operational expenses should be strictly 0.0
        assertEquals(0.0, overview.totalExpenses.toDouble(), 0.001)

        // 4. Verify Budgets category spend: Bills & Shopping must remain 0.0
        for (catBudget in overview.categoryBudgets) {
            assertEquals("Category ${catBudget.category} must have 0.0 spent", 0.0, catBudget.spent.toDouble(), 0.001)
        }
        val billsSpent = txns.filter { it.category == "Bills" && it.isOutflow }.sumOf { it.amount }
        val shoppingSpent = txns.filter { it.category == "Shopping" && it.isOutflow }.sumOf { it.amount }
        assertEquals(0.0, billsSpent, 0.001)
        assertEquals(0.0, shoppingSpent, 0.001)
    }

    // ============================================================
    // 2. UPI INTENT & GOOGLE PAY ENGINE
    // ============================================================

    @Test
    fun testUpiIntent_deepLinkConstruction_andRecipientResolution() {
        val vpa = "merchant@okaxis"
        val resolvedName = UpiPaymentHelper.resolveRecipientName(vpa, "Swiggy Delivery")
        assertEquals("Swiggy Delivery", resolvedName)

        val intent = UpiPaymentHelper.createUpiIntent(
            vpa = vpa,
            name = resolvedName,
            amount = 450.0,
            note = "Lunch Order",
            transactionReference = "REF999888",
            targetApp = UpiTargetApp.GOOGLE_PAY
        )

        assertNotNull(intent)
        val dataUri = intent!!.data
        assertNotNull(dataUri)
        assertEquals("upi", dataUri!!.scheme)
        assertEquals("pay", dataUri.authority)
        assertEquals("merchant@okaxis", dataUri.getQueryParameter("pa"))
        assertEquals("Swiggy Delivery", dataUri.getQueryParameter("pn"))
        assertEquals("450.00", dataUri.getQueryParameter("am"))
        assertEquals("INR", dataUri.getQueryParameter("cu"))
        assertEquals(UpiTargetApp.GOOGLE_PAY.packageName, intent.`package`)
    }

    @Test
    fun testUpiCallback_parseResponseString() {
        val successResponse = "txnId=UPI123456789&responseCode=00&ApprovalRefNo=987654321&Status=SUCCESS&txnRef=WW_REF_101"
        val result = UpiPaymentHelper.parseUpiResponse(successResponse)

        assertTrue(result.isSuccess)
        assertEquals("SUCCESS", result.status)
        assertEquals("UPI123456789", result.txnId)
        assertEquals("00", result.responseCode)
        assertEquals("987654321", result.approvalRefNo)
        assertEquals("WW_REF_101", result.txnRef)
    }

    @Test
    fun testUpiFingerprintHash_andDuplicateDetectionInRoom() = runTest(testDispatcher) {
        val now = 1775376000000L
        val amount = 1250.0
        val merchant = "Coffee Shop"

        // Generate SHA-256 fingerprint hash
        val fpHash = UpiPaymentHelper.generateFingerprintHash(now, amount, merchant)
        assertNotNull(fpHash)
        assertEquals(64, fpHash.length) // Standard SHA-256 hex string length

        // Insert first transaction with fingerprint
        val txn1 = TransactionEntity(
            userId = testUserId,
            title = "Payment to $merchant",
            amount = amount,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-10-05",
            timestamp = now,
            merchant = merchant,
            fingerprint = fpHash
        )
        val id1 = repository.insertTransaction(txn1)
        assertTrue(id1 > 0)

        // Attempting to insert duplicate transaction with identical fingerprint
        val txnDuplicate = txn1.copy(id = 0)
        val id2 = repository.insertTransaction(txnDuplicate)

        // Repository must recognize duplicate and return the existing id without creating another row
        assertEquals(id1, id2)
        val allTxns = transactionDao.getAllTransactions(testUserId).first()
        assertEquals("Duplicate must be dropped, only 1 record should exist", 1, allTxns.size)
    }
}
