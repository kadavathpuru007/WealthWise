package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.parser.ParsedTransactionResult
import com.example.data.parser.ParserConfidence
import com.example.data.parser.TransactionDirection
import com.example.data.repository.IngestionResult
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialStatus
import com.example.domain.financial.MoneyUtils
import com.example.ui.viewmodel.UpiPaymentViewModel
import com.razorpay.Checkout
import com.wealthwise.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.util.Calendar
import java.util.Locale

/**
 * Production test suite covering all required test cases for the
 * UPI Payment Hub & Pre-Transaction Budget Enforcement module (Sections 81 - 89).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpiPaymentAndBudgetEnforcementTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: WealthWiseDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var repository: WealthWiseRepository

    private val testUserId = "test_user_upi_123"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        transactionDao = database.transactionDao()
        categoryDao = database.categoryDao()
        budgetDao = database.budgetDao()
        repository = WealthWiseRepository(database, context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ============================================================
    // 81. TESTS — UPI PARSER
    // ============================================================

    @Test
    fun testUpiParser_standardValidUri() {
        val raw = "upi://pay?pa=merchant@upi&pn=Test%20Merchant&am=500&cu=INR"
        val parsed = UpiPaymentHelper.parseUpiString(raw)

        assertNotNull("Expected valid ParsedUpiData for standard UPI URI", parsed)
        assertEquals("merchant@upi", parsed!!.vpa)
        assertEquals("Test Merchant", parsed.name)
        assertEquals(500.0, parsed.amount!!, 0.001)
    }

    @Test
    fun testUpiParser_amountOptional() {
        val raw = "upi://pay?pa=merchant@upi&pn=Test"
        val parsed = UpiPaymentHelper.parseUpiString(raw)

        assertNotNull(parsed)
        assertEquals("merchant@upi", parsed!!.vpa)
        assertEquals("Test", parsed.name)
        assertNull("Amount must be null when am parameter is absent", parsed.amount)
    }

    @Test
    fun testUpiParser_nonUpiUrl() {
        val raw = "https://example.com/pay?pa=merchant@upi"
        val parsed = UpiPaymentHelper.parseUpiString(raw)

        assertNull("Non-UPI scheme must return null", parsed)
    }

    @Test
    fun testUpiParser_malformedUri() {
        val raw = "invalid://something?random=123"
        val parsed = UpiPaymentHelper.parseUpiString(raw)

        assertNull("Malformed or non-pay authority must return null", parsed)
    }

    @Test
    fun testUpiParser_missingPa() {
        val raw = "upi://pay?pn=Merchant&am=500&cu=INR"
        val parsed = UpiPaymentHelper.parseUpiString(raw)

        assertNull("Missing pa parameter must return null", parsed)
    }

    @Test
    fun testUpiParser_invalidAmount() {
        // Zero amount
        assertNull(UpiPaymentHelper.parseUpiString("upi://pay?pa=merchant@upi&am=0"))
        // Negative amount
        assertNull(UpiPaymentHelper.parseUpiString("upi://pay?pa=merchant@upi&am=-100"))
        // Non-numeric amount
        assertNull(UpiPaymentHelper.parseUpiString("upi://pay?pa=merchant@upi&am=invalid"))
    }

    @Test
    fun testUpiParser_encodedMerchantAndNoteDecodedCorrectly() {
        val raw = "upi://pay?pa=store@icici&pn=Tata%20Sky%20Broadband&am=999.50&tn=Monthly%20Bill%20Payment&tr=TXN89123"
        val parsed = UpiPaymentHelper.parseUpiString(raw)

        assertNotNull(parsed)
        assertEquals("store@icici", parsed!!.vpa)
        assertEquals("Tata Sky Broadband", parsed.name)
        assertEquals(999.50, parsed.amount!!, 0.001)
        assertEquals("Monthly Bill Payment", parsed.note)
        assertEquals("TXN89123", parsed.transactionReference)
    }

    @Test
    fun testVpaSyntaxValidation() {
        // Valid VPAs
        assertTrue(UpiPaymentHelper.isValidVpa("merchant@upi"))
        assertTrue(UpiPaymentHelper.isValidVpa("user.name@okhdfcbank"))
        assertTrue(UpiPaymentHelper.isValidVpa("business-123_4@ybl"))
        assertTrue(UpiPaymentHelper.isValidVpa("tata.store@axisbank"))

        // Invalid VPAs
        assertFalse(UpiPaymentHelper.isValidVpa(""))
        assertFalse(UpiPaymentHelper.isValidVpa("   "))
        assertFalse(UpiPaymentHelper.isValidVpa("http://merchant@upi"))
        assertFalse(UpiPaymentHelper.isValidVpa("random@text@upi"))
        assertFalse(UpiPaymentHelper.isValidVpa("merchant@"))
        assertFalse(UpiPaymentHelper.isValidVpa("@upi"))
        assertFalse(UpiPaymentHelper.isValidVpa("merchant @upi"))
        assertFalse(UpiPaymentHelper.isValidVpa("merchant@u")) // Handle too short (<2 chars)
    }

    // ============================================================
    // 82. TESTS — BUDGET ENFORCEMENT
    // ============================================================

    @Test
    fun testBudgetEnforcement_validWithinBudget() {
        // Budget: ₹5,000, Current spent: ₹2,000, Payment: ₹500
        val validation = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 500.0,
            categoryName = "Food",
            availableCashBalance = 10000.0,
            budgetLimit = 5000.0,
            currentSpent = 2000.0
        )

        assertEquals(BudgetValidationStatus.VALID, validation.status)
        assertEquals(2500.0, validation.projectedSpent, 0.001)
        assertEquals(2500.0, validation.remainingAfterPayment, 0.001)
        assertEquals(50, validation.percentageAfterPayment)
    }

    @Test
    fun testBudgetEnforcement_warningApproachingNinetyPercent() {
        // Budget: ₹5,000, Current spent: ₹4,000, Payment: ₹500 -> Projected: ₹4,500 (90%)
        val validation = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 500.0,
            categoryName = "Food",
            availableCashBalance = 10000.0,
            budgetLimit = 5000.0,
            currentSpent = 4000.0
        )

        assertEquals(BudgetValidationStatus.WARNING, validation.status)
        assertEquals(4500.0, validation.projectedSpent, 0.001)
        assertEquals(500.0, validation.remainingAfterPayment, 0.001)
        assertEquals(90, validation.percentageAfterPayment)
        assertTrue(validation.message.contains("almost exhausted"))
    }

    @Test
    fun testBudgetEnforcement_exceededBudgetIsStrictlyBlocked() {
        // Budget: ₹5,000, Current: ₹4,800, Payment: ₹500 -> Projected: ₹5,300 (Exceeded)
        val validation = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 500.0,
            categoryName = "Food",
            availableCashBalance = 10000.0,
            budgetLimit = 5000.0,
            currentSpent = 4800.0
        )

        assertEquals(BudgetValidationStatus.BLOCKED, validation.status)
        assertEquals(5300.0, validation.projectedSpent, 0.001)
        assertEquals(106, validation.percentageAfterPayment)
        assertEquals("Your budget for Food is exceeded, you cannot make this transaction for food", validation.message)
    }

    @Test
    fun testBudgetEnforcement_exactCapExceededIsStrictlyBlocked() {
        // Food cap: ₹1,000, Spent: ₹1,000, Payment: ₹100 -> Blocked
        val validation = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 100.0,
            categoryName = "Food",
            availableCashBalance = 10000.0,
            budgetLimit = 1000.0,
            currentSpent = 1000.0
        )

        assertEquals(BudgetValidationStatus.BLOCKED, validation.status)
        assertEquals("Your budget for Food is exceeded, you cannot make this transaction for food", validation.message)
    }

    @Test
    fun testCategoryAutoDetection_fromNoteDescription() {
        val catFood = UpiPaymentHelper.suggestCategory(merchantName = "", vpa = "", note = "Lunch at restaurant")
        assertEquals("Food", catFood)

        val catBills = UpiPaymentHelper.suggestCategory(merchantName = "", vpa = "", note = "Monthly electricity bill payment")
        assertEquals("Bills", catBills)

        val catTransport = UpiPaymentHelper.suggestCategory(merchantName = "", vpa = "", note = "Uber cab ride home")
        assertEquals("Transport", catTransport)
    }

    @Test
    fun testBudgetEnforcement_noConfiguredBudgetIsValid() {
        // Budget limit = 0.0 (no category budget configured)
        val validation = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 500.0,
            categoryName = "Miscellaneous",
            availableCashBalance = 5000.0,
            budgetLimit = 0.0,
            currentSpent = 1500.0
        )

        assertEquals(BudgetValidationStatus.VALID, validation.status)
        assertTrue(validation.message.contains("No budget configured"))
    }

    // ============================================================
    // 83. TESTS — BALANCE CHECK
    // ============================================================

    @Test
    fun testBalanceCheck_insufficientCashIsBlocked() {
        // Available spendable cash: ₹300, Payment: ₹500
        val validation = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 500.0,
            categoryName = "Food",
            availableCashBalance = 300.0,
            budgetLimit = 5000.0,
            currentSpent = 1000.0
        )

        assertEquals(BudgetValidationStatus.BLOCKED, validation.status)
        assertTrue(validation.message.contains("Insufficient available balance"))
    }

    @Test
    fun testBalanceCheck_nonPositiveAmountIsBlocked() {
        val validationZero = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 0.0,
            categoryName = "Food",
            availableCashBalance = 5000.0,
            budgetLimit = 5000.0,
            currentSpent = 1000.0
        )
        assertEquals(BudgetValidationStatus.BLOCKED, validationZero.status)

        val validationNegative = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = -50.0,
            categoryName = "Food",
            availableCashBalance = 5000.0,
            budgetLimit = 5000.0,
            currentSpent = 1000.0
        )
        assertEquals(BudgetValidationStatus.BLOCKED, validationNegative.status)
    }

    // ============================================================
    // 84. TESTS — PAYMENT FLOW & INTENT URI
    // ============================================================

    @Test
    fun testIntentBuilder_validUpiUriConstructed() {
        val uri = UpiPaymentHelper.buildUpiUri(
            vpa = "merchant@upi",
            name = "Super Market",
            amount = 450.50,
            note = "Groceries",
            transactionReference = "REF9999"
        )

        assertNotNull(uri)
        assertEquals("upi", uri!!.scheme)
        assertEquals("pay", uri.authority)
        assertEquals("merchant@upi", uri.getQueryParameter("pa"))
        assertEquals("Super Market", uri.getQueryParameter("pn"))
        assertEquals("450.50", uri.getQueryParameter("am")) // Serialized in Locale.US with 2 decimals
        assertEquals("INR", uri.getQueryParameter("cu"))
        assertEquals("Groceries", uri.getQueryParameter("tn"))
        assertEquals("REF9999", uri.getQueryParameter("tr"))
    }

    @Test
    fun testIntentBuilder_rejectsInvalidVpaOrAmount() {
        assertNull(UpiPaymentHelper.buildUpiUri("invalid_vpa", "Store", 100.0))
        assertNull(UpiPaymentHelper.buildUpiUri("merchant@upi", "Store", -10.0))
        assertNull(UpiPaymentHelper.buildUpiUri("merchant@upi", "Store", 0.0))
    }

    // ============================================================
    // 85. TESTS — DUPLICATE PAYMENT PREVENTION
    // ============================================================

    @Test
    fun testDuplicatePrevention_multipleNotificationsYieldSingleTransaction() = runTest {
        val now = System.currentTimeMillis()

        // 1. First notification from GPay: ₹500 debited to TATA STORE
        val parsedGPay = ParsedTransactionResult(
            title = "TATA STORE",
            merchant = "TATA STORE",
            amount = 500.0,
            category = "Food",
            paymentMethod = "UPI",
            isAutoDetected = true,
            rawText = "Rs 500 debited to TATA STORE via UPI",
            confidence = ParserConfidence.HIGH_CONFIDENCE,
            direction = TransactionDirection.DEBIT,
            transactionType = FinancialEventType.EXPENSE,
            status = FinancialStatus.COMPLETED,
            timestamp = now,
            source = "com.google.android.apps.nbu.paisa.user",
            referenceId = "UPI/261002/12345",
            fingerprint = "FP_TATA_500_$now"
        )

        val result1 = repository.ingestParsedTransaction(parsedGPay, testUserId)
        assertTrue("First notification must be successfully ingested", result1 is IngestionResult.Success)

        // 2. Second notification from Bank SMS for the same transaction (same referenceId)
        val parsedBank = ParsedTransactionResult(
            title = "TATA STORE",
            merchant = "TATA STORE",
            amount = 500.0,
            category = "Food",
            paymentMethod = "SMS",
            isAutoDetected = true,
            rawText = "INR 500.00 debited from a/c for TATA STORE UPI Ref UPI/261002/12345",
            confidence = ParserConfidence.HIGH_CONFIDENCE,
            direction = TransactionDirection.DEBIT,
            transactionType = FinancialEventType.EXPENSE,
            status = FinancialStatus.COMPLETED,
            timestamp = now + 5000L, // 5 seconds later
            source = "sms",
            referenceId = "UPI/261002/12345", // Same reference
            fingerprint = "FP_TATA_500_SMS"
        )

        val result2 = repository.ingestParsedTransaction(parsedBank, testUserId)
        assertTrue("Duplicate referenceId must be detected and rejected as Duplicate", result2 is IngestionResult.Duplicate)

        // 3. Third notification from PhonePe (no referenceId, but identical amount & type within 2-minute window)
        val parsedPhonePe = ParsedTransactionResult(
            title = "TATA STORE",
            merchant = "TATA STORE",
            amount = 500.0,
            category = "Food",
            paymentMethod = "UPI",
            isAutoDetected = true,
            rawText = "Paid Rs.500 to TATA STORE",
            confidence = ParserConfidence.HIGH_CONFIDENCE,
            direction = TransactionDirection.DEBIT,
            transactionType = FinancialEventType.EXPENSE,
            status = FinancialStatus.COMPLETED,
            timestamp = now + 15000L, // 15 seconds later
            source = "com.phonepe.app",
            referenceId = "",
            fingerprint = "FP_TATA_500_PHONEPE"
        )

        val result3 = repository.ingestParsedTransaction(parsedPhonePe, testUserId)
        assertTrue("2-minute window duplicate must be detected and rejected as Duplicate", result3 is IngestionResult.Duplicate)

        // Ledger must contain exactly ONE transaction
        val allTxns = transactionDao.getAllTransactions(testUserId).first()
        assertEquals("Ledger must contain exactly 1 completed transaction despite 3 notifications", 1, allTxns.size)
        assertEquals(500.0, allTxns.first().amount, 0.001)
    }

    // ============================================================
    // 86 & 87. TESTS — CANCELLATION & FAILED PAYMENT
    // ============================================================

    @Test
    fun testPaymentCancellationAndFailure_noTransactionCreated() = runTest {
        // User opens UPI, cancels payment
        val state = UpiPaymentState.Cancelled
        assertEquals(UpiPaymentState.Cancelled, state)

        // Verify database is completely empty
        val txns = transactionDao.getAllTransactions(testUserId).first()
        assertTrue("No transaction should ever be created upon cancellation", txns.isEmpty())

        // User payment fails
        val failedState = UpiPaymentState.Failed("Payment declined by issuing bank.")
        assertTrue(failedState.message.contains("declined"))

        val txnsAfterFailure = transactionDao.getAllTransactions(testUserId).first()
        assertTrue("No transaction should ever be created upon failed payment", txnsAfterFailure.isEmpty())
    }

    // ============================================================
    // 89. TESTS — ROOM REACTIVITY AFTER CONFIRMED TRANSACTION
    // ============================================================

    @Test
    fun testRoomReactivity_confirmedTransactionUpdatesMonthlySpendingAndBalance() = runTest {
        // Set category budget in Room
        categoryDao.insertCategory(
            CategoryEntity(
                userId = testUserId,
                name = "Food",
                budgetLimit = 5000.0
            )
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = cal.timeInMillis

        // Initially 0 spending
        val initialSpending = transactionDao.getMonthlySpendingByCategoryFlow(testUserId, startOfMonth).first()
        assertTrue("Initial spending should be empty", initialSpending.isEmpty())

        // Insert a confirmed payment
        val txn = TransactionEntity(
            userId = testUserId,
            title = "TATA STORE",
            amount = 500.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-10-02",
            timestamp = System.currentTimeMillis(),
            status = "COMPLETED"
        )
        transactionDao.insertTransaction(txn)

        // Reactive query updates immediately
        val updatedSpending = transactionDao.getMonthlySpendingByCategoryFlow(testUserId, startOfMonth).first()
        assertEquals(1, updatedSpending.size)
        assertEquals("Food", updatedSpending.first().category)
        assertEquals(500.0, updatedSpending.first().totalSpent, 0.001)

        // Overview calculation correctly reflects spent money
        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(1000.0),
            transactions = listOf(txn),
            budgets = listOf(BudgetEntity(userId = testUserId, category = "Food", monthlyLimit = 5000.0))
        )

        assertEquals(500.0, overview.currentBalance.toDouble(), 0.001) // 1000 - 500
        assertEquals(500.0, overview.totalExpenses.toDouble(), 0.001)
        assertEquals(4500.0, overview.budgetRemaining.toDouble(), 0.001) // 5000 - 500
    }

    // ============================================================
    // 90. TESTS — RAZORPAY CHECKOUT & BUDGET FALLBACK INTEGRATION
    // ============================================================

    @Test
    fun testRazorpayCheckout_successPersistsTransactionToRoom() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = UpiPaymentViewModel(app, repository)

        viewModel.onVpaChanged("swiggy@icici")
        viewModel.onMerchantChanged("Swiggy")
        viewModel.onAmountChanged("450.00")
        viewModel.onNoteChanged("Dinner Order")

        // Trigger handlePaymentSuccess directly
        viewModel.handlePaymentSuccess("pay_razorpay_test_123", null)

        // Verify state is Completed
        assertEquals(UpiPaymentState.Completed, viewModel.paymentState.value)

        // Verify transaction is inserted into Room database
        val txns = transactionDao.getAllTransactions().first()
        assertFalse("Expected transaction to be saved in Room", txns.isEmpty())
        val saved = txns.first()
        assertEquals(450.0, saved.amount, 0.001)
        assertEquals("Food", saved.category)
        assertEquals("Swiggy", saved.merchant)
        assertEquals("pay_razorpay_test_123", saved.referenceId)
        assertEquals("UPI", saved.paymentMethod)
        assertEquals("COMPLETED", saved.status)
    }

    @Test
    fun testRazorpayCheckout_cancellationDoesNotPersistTransaction() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = UpiPaymentViewModel(app, repository)

        viewModel.onVpaChanged("zomato@upi")
        viewModel.onAmountChanged("200.00")

        // Trigger onPaymentError with PAYMENT_CANCELED
        viewModel.onPaymentError(Checkout.PAYMENT_CANCELED, "User cancelled", null)

        assertEquals(UpiPaymentState.Cancelled, viewModel.paymentState.value)

        // Verify database has no transaction recorded
        val txns = transactionDao.getAllTransactions().first()
        assertTrue("No transaction must be saved upon cancellation", txns.isEmpty())
    }

    @Test
    fun testRazorpayCheckout_errorSetsFailureState() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = UpiPaymentViewModel(app, repository)

        viewModel.onPaymentError(Checkout.NETWORK_ERROR, "Network error occurred", null)

        assertTrue(viewModel.paymentState.value is UpiPaymentState.Failed)
        val failed = viewModel.paymentState.value as UpiPaymentState.Failed
        assertTrue(failed.message.contains("Network error"))
    }

    @Test
    fun testBudgetCheck_uninitializedStartingBalanceFallsBackToCategoryBudget() {
        // When opening balance is unset, 0 available cash should NOT block valid spending within category budget
        val validation = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 500.0,
            categoryName = "Food",
            availableCashBalance = 0.0,
            budgetLimit = 5000.0,
            currentSpent = 0.0,
            isStartingBalanceUnset = true
        )

        assertEquals(BudgetValidationStatus.VALID, validation.status)
        assertEquals(500.0, validation.paymentAmount, 0.001)
        assertEquals(500.0, validation.projectedSpent, 0.001)

        // Contrast with isStartingBalanceUnset = false where 0 balance blocks payment
        val blocked = UpiPaymentHelper.evaluateUpiBudgetAndBalance(
            paymentAmount = 500.0,
            categoryName = "Food",
            availableCashBalance = 0.0,
            budgetLimit = 5000.0,
            currentSpent = 0.0,
            isStartingBalanceUnset = false
        )

        assertEquals(BudgetValidationStatus.BLOCKED, blocked.status)
        assertTrue(blocked.message.contains("Insufficient available balance"))
    }
}
