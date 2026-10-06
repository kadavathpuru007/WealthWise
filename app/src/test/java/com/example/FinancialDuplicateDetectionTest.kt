package com.example

import com.example.data.local.TransactionEntity
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialValidationEngine
import com.example.domain.financial.MoneyUtils
import com.example.domain.financial.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FinancialDuplicateDetectionTest {

    private val startingBalance = BigDecimal("50000.00")

    @Test
    fun `test 1 - exact duplicate with identical referenceId is rejected`() {
        val existingTxn = TransactionEntity(
            id = 1,
            title = "Rahul",
            amount = 500.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-28",
            timestamp = 1000000L,
            merchant = "Rahul",
            referenceId = "UPI9876543210"
        )

        val result = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(500.0),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Rahul",
            currentBalance = startingBalance,
            existingTransactions = listOf(existingTxn),
            referenceId = "UPI9876543210",
            timestamp = 1000000L
        )

        assertTrue("Exact reference ID duplicate must be blocked", result is ValidationResult.Blocked)
        val blocked = result as ValidationResult.Blocked
        assertTrue(blocked.reason.contains("Duplicate reference ID"))
    }

    @Test
    fun `test 2 - same amount and merchant after 5 minutes is allowed as separate transaction`() {
        val baseTimestamp = 1000000L
        val fiveMinutesLater = baseTimestamp + (5 * 60 * 1000L) // +300,000 ms

        val existingTxn = TransactionEntity(
            id = 1,
            title = "Swiggy",
            amount = 350.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-28",
            timestamp = baseTimestamp,
            merchant = "Swiggy",
            referenceId = ""
        )

        val result = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(350.0),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Swiggy",
            currentBalance = startingBalance,
            existingTransactions = listOf(existingTxn),
            referenceId = "",
            timestamp = fiveMinutesLater
        )

        assertTrue("Legitimate separate transaction after 5 minutes must NOT be blocked", result is ValidationResult.Valid)
    }

    @Test
    fun `test 3 - same amount and merchant with different reference IDs is allowed`() {
        val baseTimestamp = 1000000L
        val oneMinuteLater = baseTimestamp + 60000L

        val existingTxn = TransactionEntity(
            id = 1,
            title = "Swiggy",
            amount = 450.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-28",
            timestamp = baseTimestamp,
            merchant = "Swiggy",
            referenceId = "UPI_ORDER_1"
        )

        val result = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(450.0),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Swiggy",
            currentBalance = startingBalance,
            existingTransactions = listOf(existingTxn),
            referenceId = "UPI_ORDER_2",
            timestamp = oneMinuteLater
        )

        assertTrue("Transactions with distinct authoritative reference IDs must be allowed", result is ValidationResult.Valid)
    }

    @Test
    fun `test 4 - same transaction from different notification sources within dual notification window is blocked`() {
        val baseTimestamp = 1000000L
        val thirtySecondsLater = baseTimestamp + 30000L // 30s later (Bank SMS arriving after UPI push)

        val existingGPayTxn = TransactionEntity(
            id = 1,
            title = "Rahul",
            amount = 450.0,
            type = "EXPENSE",
            category = "Other",
            paymentMethod = "UPI",
            date = "2026-09-28",
            timestamp = baseTimestamp,
            merchant = "Rahul",
            referenceId = ""
        )

        // Incoming bank SMS for the same transaction without explicit reference ID arriving within 30 seconds
        val result = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(450.0),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Rahul",
            currentBalance = startingBalance,
            existingTransactions = listOf(existingGPayTxn),
            referenceId = "",
            timestamp = thirtySecondsLater
        )

        assertTrue("Duplicate dual-notification within 2-minute window must be blocked", result is ValidationResult.Blocked)
    }

    @Test
    fun `test 5 - missing reference ID within rapid window is blocked but after 5 minutes is allowed`() {
        val baseTimestamp = 1000000L
        val existingTxn = TransactionEntity(
            id = 1,
            title = "Chai Point",
            amount = 60.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-28",
            timestamp = baseTimestamp,
            merchant = "Chai Point",
            referenceId = ""
        )

        // Immediate duplicate (e.g. 10s later)
        val rapidResult = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(60.0),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Chai Point",
            currentBalance = startingBalance,
            existingTransactions = listOf(existingTxn),
            referenceId = "",
            timestamp = baseTimestamp + 10000L
        )
        assertTrue("Rapid duplicate missing referenceId must be blocked", rapidResult is ValidationResult.Blocked)

        // Separate cup of tea 5 minutes later (300 seconds)
        val laterResult = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(60.0),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Chai Point",
            currentBalance = startingBalance,
            existingTransactions = listOf(existingTxn),
            referenceId = "",
            timestamp = baseTimestamp + (5 * 60 * 1000L)
        )
        assertTrue("Separate transaction missing referenceId 5 minutes later must be allowed", laterResult is ValidationResult.Valid)
    }

    @Test
    fun `test 6 - 5-minute window fingerprint identical across SMS and notification despite different formatting`() {
        // Base timestamp aligned to start of a 5-minute bucket
        val baseTimestamp = (1700000000000L / 300_000L) * 300_000L + 15000L
        val twoMinutesLater = baseTimestamp + 120000L // 2 mins later (within same 5-min window)

        // Notification: "Paid to Swiggy", amount 350.0
        val fpNotification = com.example.data.parser.TransactionParser.generateFingerprint(
            amount = 350.0,
            type = "EXPENSE",
            timestamp = baseTimestamp,
            merchant = "Paid to Swiggy"
        )

        // Bank SMS: "swiggy@icici" or "Swiggy", amount 350.0
        val fpSms = com.example.data.parser.TransactionParser.generateFingerprint(
            amount = 350.0,
            type = "EXPENSE",
            timestamp = twoMinutesLater,
            merchant = "swiggy@icici"
        )

        assertEquals("Fingerprints across SMS and Notification within 5-min window must be identical", fpNotification, fpSms)
    }

    @Test
    fun `test 7 - decoupled budgets and goals - goal exceeding available funds produces advisory warning without blocking`() {
        val availableCashAfterBudgets = BigDecimal("2000.00")
        val monthlyTarget = BigDecimal("5000.00")
        val targetAmount = BigDecimal("50000.00")

        val result = FinancialValidationEngine.validateGoal(
            title = "New Bike",
            targetAmount = targetAmount,
            monthlyTarget = monthlyTarget,
            availableCashAfterBudgets = availableCashAfterBudgets
        )

        assertTrue("Goal target exceeding available funds must be an advisory Warning, NOT Blocked", result is ValidationResult.Warning)
        val warning = result as ValidationResult.Warning
        assertEquals("Goal Target Exceeds Available Funds", warning.title)
        assertEquals(BigDecimal("3000.00"), warning.shortfall)
        assertTrue("Warning message must mention advisory status", warning.actionSuggestion.contains("Advisory"))
    }

    @Test
    fun `test 8 - normalized merchant matching prevents duplicate transaction even with variations like Paid to or UPI suffix`() {
        val baseTimestamp = 1000000L
        val existingTxn = TransactionEntity(
            id = 1,
            title = "Swiggy",
            amount = 450.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-28",
            timestamp = baseTimestamp,
            merchant = "Swiggy",
            referenceId = ""
        )

        // Arriving notification says "Paid to Swiggy"
        val result = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(450.0),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = "Paid to Swiggy",
            currentBalance = startingBalance,
            existingTransactions = listOf(existingTxn),
            referenceId = "",
            timestamp = baseTimestamp + 45000L // 45s later
        )

        assertTrue("Normalized merchant duplicate must be blocked", result is ValidationResult.Blocked)
    }
}
