package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LoanDao
import com.example.data.local.LoanEntity
import com.example.data.local.LoanRepaymentEntity
import com.example.data.local.TransactionDao
import com.example.data.local.WealthWiseDatabase
import com.example.data.parser.ParsedTransactionResult
import com.example.data.parser.ParserConfidence
import com.example.data.parser.TransactionParser
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialStatus
import com.example.domain.loan.LoanCalculations
import com.example.domain.loan.LoanMatchResult
import com.example.domain.loan.LoanMatchingEngine
import com.example.domain.loan.LoanValidationResult
import com.example.ui.viewmodel.LoansViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.LocalDate
import java.util.concurrent.Executor

/**
 * Production test suite covering all 22 required test cases for the
 * Loans & Debt Management module as specified in Section 55.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoansAndDebtManagementTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: WealthWiseDatabase
    private lateinit var loanDao: LoanDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var repository: WealthWiseRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .build()

        loanDao = database.loanDao()
        transactionDao = database.transactionDao()
        repository = WealthWiseRepository(database, context)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // =========================================================================
    // UNIT TESTS (1 - 9)
    // =========================================================================

    @Test
    fun test1_emiCalculator_atZeroPercentInterest() {
        val principal = 120000.0
        val rate = 0.0
        val tenure = 12
        val emi = LoanCalculations.calculateMonthlyEmi(principal, rate, tenure)
        assertEquals(10000.0, emi, 0.001)
    }

    @Test
    fun test2_emiCalculator_withInterest() {
        val principal = 100000.0
        val rate = 12.0 // 12% annual = 1% monthly
        val tenure = 12 // 12 months
        val emi = LoanCalculations.calculateMonthlyEmi(principal, rate, tenure)
        // Standard formula: 100000 * 0.01 * (1.01)^12 / ((1.01)^12 - 1) = 8884.878867
        assertEquals(8884.88, emi, 0.05)
    }

    @Test
    fun test3_invalidPrincipal_fails() {
        val zeroPrincipal = LoanCalculations.calculateMonthlyEmi(0.0, 10.0, 12)
        assertEquals(0.0, zeroPrincipal, 0.001)

        val negativePrincipal = LoanCalculations.calculateMonthlyEmi(-50000.0, 10.0, 12)
        assertEquals(0.0, negativePrincipal, 0.001)

        val nanPrincipal = LoanCalculations.calculateMonthlyEmi(Double.NaN, 10.0, 12)
        assertEquals(0.0, nanPrincipal, 0.001)
    }

    @Test
    fun test4_invalidTenure_fails() {
        val zeroTenure = LoanCalculations.calculateMonthlyEmi(100000.0, 10.0, 0)
        assertEquals(0.0, zeroTenure, 0.001)

        val negativeTenure = LoanCalculations.calculateMonthlyEmi(100000.0, 10.0, -12)
        assertEquals(0.0, negativeTenure, 0.001)
    }

    @Test
    fun test5_invalidDueDay_fails() = runTest {
        val vm = LoansViewModel(
            application = ApplicationProvider.getApplicationContext(),
            loanDao = loanDao,
            repository = repository,
            authUidProvider = { "user_test_5" }
        )

        var validationResult: LoanValidationResult? = null
        vm.addLoan(
            loanName = "Personal Loan",
            lenderName = "HDFC",
            loanType = LoanEntity.TYPE_PERSONAL,
            principalAmount = 100000.0,
            emiAmount = 5000.0,
            interestRate = 10.0,
            totalTenureMonths = 24,
            emiDueDayOfMonth = 0 // Invalid (< 1)
        ) { validationResult = it }

        advanceUntilIdle()
        assertTrue(validationResult is LoanValidationResult.Error)
        assertEquals("EMI due day must be between 1 and 31.", (validationResult as LoanValidationResult.Error).message)

        vm.addLoan(
            loanName = "Personal Loan",
            lenderName = "HDFC",
            loanType = LoanEntity.TYPE_PERSONAL,
            principalAmount = 100000.0,
            emiAmount = 5000.0,
            interestRate = 10.0,
            totalTenureMonths = 24,
            emiDueDayOfMonth = 32 // Invalid (> 31)
        ) { validationResult = it }

        advanceUntilIdle()
        assertTrue(validationResult is LoanValidationResult.Error)
    }

    @Test
    fun test6_loanProgress_calculation() {
        val principal = 100000.0
        val halfPaid = 50000.0
        val progressHalf = LoanCalculations.calculateProgress(principal, halfPaid)
        assertEquals(0.5f, progressHalf, 0.001f)

        val zeroPaid = 0.0
        val progressZero = LoanCalculations.calculateProgress(principal, zeroPaid)
        assertEquals(0.0f, progressZero, 0.001f)

        val fullPaid = 100000.0
        val progressFull = LoanCalculations.calculateProgress(principal, fullPaid)
        assertEquals(1.0f, progressFull, 0.001f)
    }

    @Test
    fun test7_remainingBalance_neverNegative() {
        val principal = 100000.0
        val overpaid = 120000.0
        val remaining = LoanCalculations.calculateRemainingAmount(principal, overpaid)
        assertEquals(0.0, remaining, 0.001)

        val progress = LoanCalculations.calculateProgress(principal, overpaid)
        assertEquals(1.0f, progress, 0.001f)
    }

    @Test
    fun test8_upcomingEmi_calculation() {
        val currentDate = LocalDate.of(2026, 10, 2)
        val dueDay = 15

        // Due day later this month, not paid yet -> 15 Oct 2026
        val nextDueDate = LoanCalculations.calculateNextDueDate(
            emiDueDayOfMonth = dueDay,
            currentDate = currentDate,
            isCurrentMonthPaid = false
        )
        assertEquals(LocalDate.of(2026, 10, 15), nextDueDate)
        val daysRemaining = LoanCalculations.calculateDaysRemaining(currentDate, nextDueDate)
        assertEquals(13, daysRemaining)

        // If today is due date and not paid -> today
        val todayDue = LoanCalculations.calculateNextDueDate(
            emiDueDayOfMonth = 2,
            currentDate = currentDate,
            isCurrentMonthPaid = false
        )
        assertEquals(LocalDate.of(2026, 10, 2), todayDue)
        assertEquals(0, LoanCalculations.calculateDaysRemaining(currentDate, todayDue))

        // If already paid for current month -> due next month (15 Nov 2026)
        val nextMonthDue = LoanCalculations.calculateNextDueDate(
            emiDueDayOfMonth = dueDay,
            currentDate = currentDate,
            isCurrentMonthPaid = true
        )
        assertEquals(LocalDate.of(2026, 11, 15), nextMonthDue)
    }

    @Test
    fun test9_monthBoundary_handling() {
        // Due day 31 in a 30-day month (April)
        val aprilDate = LocalDate.of(2026, 4, 10)
        val aprilDueDate = LoanCalculations.calculateNextDueDate(
            emiDueDayOfMonth = 31,
            currentDate = aprilDate,
            isCurrentMonthPaid = false
        )
        assertEquals(LocalDate.of(2026, 4, 30), aprilDueDate)

        // Due day 31 in February non-leap year (2025)
        val febNonLeap = LocalDate.of(2025, 2, 5)
        val febNonLeapDue = LoanCalculations.calculateNextDueDate(
            emiDueDayOfMonth = 31,
            currentDate = febNonLeap,
            isCurrentMonthPaid = false
        )
        assertEquals(LocalDate.of(2025, 2, 28), febNonLeapDue)

        // Due day 31 in February leap year (2024)
        val febLeap = LocalDate.of(2024, 2, 5)
        val febLeapDue = LoanCalculations.calculateNextDueDate(
            emiDueDayOfMonth = 31,
            currentDate = febLeap,
            isCurrentMonthPaid = false
        )
        assertEquals(LocalDate.of(2024, 2, 29), febLeapDue)
    }

    // =========================================================================
    // PARSER & CONFIDENCE TESTS (10 - 17)
    // =========================================================================

    @Test
    fun test10_validEmiDebit_detectedAndClassified() {
        val sms = "Rs. 8,500 debited from account towards HDFC loan EMI on 02-Oct-26."
        val parsed = TransactionParser.parse(sms)
        assertNotEquals(ParserConfidence.REJECTED, parsed.confidence)
        assertEquals(8500.0, parsed.amount, 0.001)
        assertEquals("Loans & Debt", parsed.category)
        assertEquals(FinancialEventType.LOAN_PAYMENT, parsed.transactionType)
        assertEquals(FinancialStatus.COMPLETED, parsed.status)
    }

    @Test
    fun test11_emiReminder_rejected() {
        val sms = "Your EMI of Rs. 8,500 is due on 05-Oct-2026. Please maintain sufficient balance."
        val parsed = TransactionParser.parse(sms)
        assertEquals("EMI reminder must be rejected", ParserConfidence.REJECTED, parsed.confidence)
    }

    @Test
    fun test12_loanOffer_rejected() {
        val sms = "Congratulations! You are eligible for a Rs. 5,00,000 pre-approved personal loan at low interest. Apply now."
        val parsed = TransactionParser.parse(sms)
        assertEquals("Loan offer must be rejected", ParserConfidence.REJECTED, parsed.confidence)
    }

    @Test
    fun test13_emiAdvertisement_rejected() {
        val sms = "Get the latest iPhone with no cost EMI starting at Rs. 4,999 on your HDFC credit card. Limited offer."
        val parsed = TransactionParser.parse(sms)
        assertEquals("EMI advertisement must be rejected", ParserConfidence.REJECTED, parsed.confidence)
    }

    @Test
    fun test14_failedEmi_rejected() {
        val sms = "Your EMI auto debit of Rs. 8,500 for loan account failed due to insufficient funds in account."
        val parsed = TransactionParser.parse(sms)
        assertTrue(
            "Failed EMI debit must be rejected or marked FAILED",
            parsed.confidence == ParserConfidence.REJECTED || parsed.status == FinancialStatus.FAILED
        )
    }

    @Test
    fun test15_duplicateNotification_idempotent() = runTest {
        val userId = "user_test_15"
        val loan = LoanEntity(
            userId = userId,
            loanName = "Car Loan",
            lenderName = "HDFC Bank",
            loanType = LoanEntity.TYPE_VEHICLE,
            principalAmount = 300000.0,
            emiAmount = 12000.0,
            interestRate = 9.5,
            totalTenureMonths = 36,
            emiDueDayOfMonth = 10,
            startDate = System.currentTimeMillis()
        )
        loanDao.insertLoan(loan)

        val sms = "Rs. 12,000 debited from account towards HDFC Bank loan EMI. Ref: UPI/628192837482"
        val parsed1 = TransactionParser.parse(sms)
        assertNotEquals(ParserConfidence.REJECTED, parsed1.confidence)

        // Process notification 1
        repository.ingestParsedTransaction(parsed1, userId)

        // Process notification 2 (duplicate)
        repository.ingestParsedTransaction(parsed1, userId)

        val txns = transactionDao.getAllTransactions(userId).first()
        val repayments = loanDao.getAllRepaymentsForUserFlow(userId).first()

        assertEquals("Duplicate transaction must be deduplicated to exactly 1", 1, txns.size)
        assertEquals("Duplicate repayment must be deduplicated to exactly 1", 1, repayments.size)
        assertEquals(12000.0, repayments.first().paidAmount, 0.001)
    }

    @Test
    fun test16_ambiguousLoanMatching_doesNotRandomlyAssociate() {
        val userId = "user_test_16"
        val loan1 = LoanEntity(
            id = 101L,
            userId = userId,
            loanName = "Personal Loan 1",
            lenderName = "Axis Bank",
            loanType = LoanEntity.TYPE_PERSONAL,
            principalAmount = 200000.0,
            emiAmount = 8500.0,
            interestRate = 12.0,
            totalTenureMonths = 36,
            emiDueDayOfMonth = 5,
            startDate = System.currentTimeMillis()
        )
        val loan2 = LoanEntity(
            id = 102L,
            userId = userId,
            loanName = "Personal Loan 2",
            lenderName = "ICICI Bank",
            loanType = LoanEntity.TYPE_PERSONAL,
            principalAmount = 200000.0,
            emiAmount = 8500.0,
            interestRate = 12.0,
            totalTenureMonths = 36,
            emiDueDayOfMonth = 15,
            startDate = System.currentTimeMillis()
        )

        // Neither lender name matches, amounts are identical -> ambiguous
        val matchResult = LoanMatchingEngine.matchEmiToLoan(
            detectedAmount = 8500.0,
            detectedMerchantOrLender = "Bank Auto Debit",
            detectedTitleOrText = "NACH debit of Rs 8500",
            activeLoans = listOf(loan1, loan2)
        )

        assertTrue(
            "Ambiguous match must not randomly assign a loan",
            matchResult is LoanMatchResult.Ambiguous
        )
    }

    @Test
    fun test17_userIsolation_userCannotSeeOtherUsersLoans() = runTest {
        val userA = "user_alpha"
        val userB = "user_beta"

        val loanA = LoanEntity(
            userId = userA,
            loanName = "User A Home Loan",
            lenderName = "SBI",
            loanType = LoanEntity.TYPE_HOME,
            principalAmount = 4000000.0,
            emiAmount = 35000.0,
            interestRate = 8.5,
            totalTenureMonths = 240,
            emiDueDayOfMonth = 10,
            startDate = System.currentTimeMillis()
        )
        val loanB = LoanEntity(
            userId = userB,
            loanName = "User B Education Loan",
            lenderName = "Canara Bank",
            loanType = LoanEntity.TYPE_EDUCATION,
            principalAmount = 800000.0,
            emiAmount = 15000.0,
            interestRate = 9.0,
            totalTenureMonths = 84,
            emiDueDayOfMonth = 1,
            startDate = System.currentTimeMillis()
        )

        val loanAId = loanDao.insertLoan(loanA)
        loanDao.insertLoan(loanB)

        // Insert repayment for User A
        loanDao.insertRepayment(
            LoanRepaymentEntity(
                loanId = loanAId,
                userId = userA,
                paidAmount = 35000.0,
                paymentDate = System.currentTimeMillis()
            )
        )

        val userALoans = loanDao.getActiveLoansFlow(userA).first()
        val userBLoans = loanDao.getActiveLoansFlow(userB).first()

        assertEquals(1, userALoans.size)
        assertEquals("User A Home Loan", userALoans.first().loanName)

        assertEquals(1, userBLoans.size)
        assertEquals("User B Education Loan", userBLoans.first().loanName)

        // Verify remaining debt isolation
        val userARemaining = loanDao.getTotalRemainingDebtFlow(userA).first()
        val userBRemaining = loanDao.getTotalRemainingDebtFlow(userB).first()

        assertEquals(3965000.0, userARemaining, 0.001)
        assertEquals(800000.0, userBRemaining, 0.001)

        // User B has zero repayments
        val userBRepayments = loanDao.getAllRepaymentsForUserFlow(userB).first()
        assertTrue(userBRepayments.isEmpty())
    }

    // =========================================================================
    // INTEGRATION TESTS (18 - 22)
    // =========================================================================

    @Test
    fun test18_insertLoan_loansFlowUpdates() = runTest {
        val userId = "user_test_18"
        val initialLoans = loanDao.getActiveLoansFlow(userId).first()
        assertTrue(initialLoans.isEmpty())

        val loan = LoanEntity(
            userId = userId,
            loanName = "Consumer EMI",
            lenderName = "Bajaj Finance",
            loanType = LoanEntity.TYPE_CONSUMER_EMI,
            principalAmount = 45000.0,
            emiAmount = 4500.0,
            interestRate = 0.0,
            totalTenureMonths = 10,
            emiDueDayOfMonth = 5,
            startDate = System.currentTimeMillis()
        )
        loanDao.insertLoan(loan)

        val updatedLoans = loanDao.getActiveLoansFlow(userId).first()
        assertEquals(1, updatedLoans.size)
        assertEquals("Consumer EMI", updatedLoans.first().loanName)
        assertEquals(45000.0, updatedLoans.first().principalAmount, 0.001)
    }

    @Test
    fun test19_insertRepayment_progressUpdates() = runTest {
        val userId = "user_test_19"
        val loanId = loanDao.insertLoan(
            LoanEntity(
                userId = userId,
                loanName = "Vehicle Loan",
                lenderName = "SBI",
                loanType = LoanEntity.TYPE_VEHICLE,
                principalAmount = 100000.0,
                emiAmount = 10000.0,
                interestRate = 8.0,
                totalTenureMonths = 12,
                emiDueDayOfMonth = 10,
                startDate = System.currentTimeMillis()
            )
        )

        val initialPaid = loanDao.getTotalPaidForLoanFlow(loanId, userId).first()
        assertEquals(0.0, initialPaid, 0.001)

        loanDao.insertRepayment(
            LoanRepaymentEntity(
                loanId = loanId,
                userId = userId,
                paidAmount = 10000.0,
                paymentDate = System.currentTimeMillis()
            )
        )

        val updatedPaid = loanDao.getTotalPaidForLoanFlow(loanId, userId).first()
        assertEquals(10000.0, updatedPaid, 0.001)

        val remainingDebt = loanDao.getTotalRemainingDebtFlow(userId).first()
        assertEquals(90000.0, remainingDebt, 0.001)
    }

    @Test
    fun test20_insertTransaction_homeLoanMetricsUpdate() = runTest {
        val userId = "user_test_20"
        val loanId = loanDao.insertLoan(
            LoanEntity(
                userId = userId,
                loanName = "Home Renovation",
                lenderName = "HDFC",
                loanType = LoanEntity.TYPE_PERSONAL,
                principalAmount = 500000.0,
                emiAmount = 25000.0,
                interestRate = 11.0,
                totalTenureMonths = 24,
                emiDueDayOfMonth = 5,
                startDate = System.currentTimeMillis()
            )
        )

        val activeLoansFlow = loanDao.getActiveLoansFlow(userId)
        val remainingDebtFlow = loanDao.getTotalRemainingDebtFlow(userId)

        assertEquals(1, activeLoansFlow.first().size)
        assertEquals(500000.0, remainingDebtFlow.first(), 0.001)

        // Make manual repayment
        repository.recordManualLoanRepayment(loanId, 25000.0, userId = userId)

        // Verified updated metrics
        assertEquals(475000.0, remainingDebtFlow.first(), 0.001)
        val txns = transactionDao.getAllTransactions(userId).first()
        assertEquals(1, txns.size)
        assertEquals("Loans & Debt", txns.first().category)
        assertEquals(25000.0, txns.first().amount, 0.001)
    }

    @Test
    fun test21_detectedEmi_transactionPlusRepaymentExactlyOnce() = runTest {
        val userId = "user_test_21"
        val loanId = loanDao.insertLoan(
            LoanEntity(
                userId = userId,
                loanName = "Personal Loan",
                lenderName = "HDFC Bank",
                loanType = LoanEntity.TYPE_PERSONAL,
                principalAmount = 200000.0,
                emiAmount = 8500.0,
                interestRate = 12.0,
                totalTenureMonths = 36,
                emiDueDayOfMonth = 5,
                startDate = System.currentTimeMillis()
            )
        )

        val sms = "Rs. 8,500 debited from account towards HDFC Bank loan EMI. Ref: HDFC-EMI-9988"
        val parsed = TransactionParser.parse(sms)
        assertNotEquals(ParserConfidence.REJECTED, parsed.confidence)

        // Ingest transaction
        repository.ingestParsedTransaction(parsed, userId)

        // Verify transaction ledger: Exactly 1 transaction
        val txns = transactionDao.getAllTransactions(userId).first()
        assertEquals(1, txns.size)
        assertEquals(8500.0, txns.first().amount, 0.001)
        assertEquals("Loans & Debt", txns.first().category)

        // Verify repayment table: Exactly 1 repayment record
        val repayments = loanDao.getRepaymentsForLoanFlow(loanId, userId).first()
        assertEquals(1, repayments.size)
        assertEquals(8500.0, repayments.first().paidAmount, 0.001)
        assertEquals("HDFC-EMI-9988", repayments.first().transactionRef)
        assertTrue(repayments.first().isAutoDetected)

        // Remaining balance reduced exactly once
        val remaining = loanDao.getTotalRemainingDebtFlow(userId).first()
        assertEquals(191500.0, remaining, 0.001)
    }

    @Test
    fun test22_closeLoan_disappearsFromActiveList() = runTest {
        val userId = "user_test_22"
        val loanId = loanDao.insertLoan(
            LoanEntity(
                userId = userId,
                loanName = "Closed Gadget Loan",
                lenderName = "Bajaj Finserv",
                loanType = LoanEntity.TYPE_CONSUMER_EMI,
                principalAmount = 30000.0,
                emiAmount = 3000.0,
                interestRate = 0.0,
                totalTenureMonths = 10,
                emiDueDayOfMonth = 1,
                startDate = System.currentTimeMillis()
            )
        )

        // Add a repayment
        loanDao.insertRepayment(
            LoanRepaymentEntity(
                loanId = loanId,
                userId = userId,
                paidAmount = 3000.0,
                paymentDate = System.currentTimeMillis()
            )
        )

        // Active before close
        assertEquals(1, loanDao.getActiveLoansFlow(userId).first().size)

        // Close loan
        repository.closeLoan(loanId, userId)

        // Active list must now be empty
        val activeLoans = loanDao.getActiveLoansFlow(userId).first()
        assertTrue("Closed loan must disappear from active list", activeLoans.isEmpty())

        // Closed loans flow still returns the loan
        val closedLoans = loanDao.getClosedLoansFlow(userId).first()
        assertEquals(1, closedLoans.size)
        assertTrue(closedLoans.first().isClosed)

        // Repayment history must be completely preserved
        val repayments = loanDao.getRepaymentsForLoanFlow(loanId, userId).first()
        assertEquals(1, repayments.size)
        assertEquals(3000.0, repayments.first().paidAmount, 0.001)
    }
}
