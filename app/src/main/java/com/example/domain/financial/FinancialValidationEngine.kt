package com.example.domain.financial

import com.example.data.local.BudgetEntity
import com.example.data.local.GoalEntity
import com.example.data.local.LoanEntity
import com.example.data.local.TransactionEntity
import java.math.BigDecimal
import java.util.Locale

/**
 * Validates financial operations before modifying the database.
 * Distinguishes between HARD BLOCKS (preventing impossible states)
 * and SOFT WARNINGS (alerting the user about plan collisions).
 */
object FinancialValidationEngine {

    private const val DUPLICATE_TIME_WINDOW_MS = 2 * 60 * 1000L // 2 minutes conservative dual-notification window

    /**
     * Validates a transaction prior to insertion or update.
     * Blocks negative/zero amounts, insufficient balance on outflows, and duplicate entries.
     */
    fun validateTransaction(
        amount: BigDecimal,
        eventType: FinancialEventType,
        merchantOrTitle: String,
        currentBalance: BigDecimal,
        existingTransactions: List<TransactionEntity> = emptyList(),
        referenceId: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): ValidationResult {
        // 1. Amount validation - strictly reject <= 0
        if (amount <= MoneyUtils.ZERO) {
            return ValidationResult.Blocked(
                title = "Invalid Amount",
                message = "Transaction amount must be strictly greater than ₹0.00.",
                availableBalance = currentBalance,
                requiredAmount = amount,
                shortfall = MoneyUtils.ZERO,
                reason = "Amount cannot be zero or negative."
            )
        }

        // 2. Insufficient balance check for outflows
        val isOutflow = when (eventType) {
            FinancialEventType.EXPENSE,
            FinancialEventType.SUBSCRIPTION_PAYMENT,
            FinancialEventType.INVESTMENT_PURCHASE,
            FinancialEventType.GOAL_ALLOCATION,
            FinancialEventType.LOAN_PAYMENT -> true
            else -> false
        }

        if (isOutflow && amount > currentBalance) {
            val shortfall = amount.subtract(currentBalance)
            return ValidationResult.Warning(
                title = "Insufficient Balance",
                message = "Available Balance: ₹${MoneyUtils.formatInr(currentBalance)}\nTransaction Amount: ₹${MoneyUtils.formatInr(amount)}\nShortfall: ₹${MoneyUtils.formatInr(shortfall)}",
                availableAmount = currentBalance,
                plannedAmount = amount,
                shortfall = shortfall,
                affectedFeature = "Ledger",
                actionSuggestion = "Transaction of ₹${MoneyUtils.formatInr(amount)} exceeds spendable cash balance of ₹${MoneyUtils.formatInr(currentBalance)}."
            )
        }

        // 3. Duplicate transaction detection
        val hasRef = referenceId.isNotBlank()
        if (hasRef) {
            val hasExactRef = existingTransactions.any {
                it.referenceId.isNotBlank() && it.referenceId.equals(referenceId, ignoreCase = true)
            }
            if (hasExactRef) {
                return ValidationResult.Blocked(
                    title = "Duplicate Transaction",
                    message = "A transaction with Reference ID '$referenceId' has already been recorded in your ledger.",
                    availableBalance = currentBalance,
                    requiredAmount = amount,
                    shortfall = MoneyUtils.ZERO,
                    reason = "Duplicate reference ID detected."
                )
            }
            // Explicit distinct reference ID: authoritative, not a duplicate
            return ValidationResult.Valid
        }

        // When referenceId is unavailable, use a conservative fingerprint:
        // Same amount, same merchant/title, same event direction, within 2-minute deduplication window.
        // Transactions 5 minutes apart are legitimate separate transactions and are NOT blocked.
        val duplicateRecent = existingTransactions.find { tx ->
            val txAmount = MoneyUtils.of(tx.amount)
            val timeDiff = kotlin.math.abs(timestamp - tx.timestamp)
            val sameAmount = txAmount.compareTo(amount) == 0
            val normCurrent = com.example.data.parser.TransactionParser.normalizeMerchantForFingerprint(merchantOrTitle)
            val normTxMerchant = com.example.data.parser.TransactionParser.normalizeMerchantForFingerprint(tx.merchant)
            val normTxTitle = com.example.data.parser.TransactionParser.normalizeMerchantForFingerprint(tx.title)
            val sameTitle = tx.title.trim().equals(merchantOrTitle.trim(), ignoreCase = true) ||
                    tx.merchant.trim().equals(merchantOrTitle.trim(), ignoreCase = true) ||
                    normTxMerchant == normCurrent ||
                    normTxTitle == normCurrent
            val sameType = FinancialCalculationEngine.determineEventType(tx) == eventType

            sameAmount && sameTitle && sameType && timeDiff < DUPLICATE_TIME_WINDOW_MS
        }

        if (duplicateRecent != null) {
            return ValidationResult.Blocked(
                title = "Duplicate Transaction",
                message = "An identical transaction of ₹${MoneyUtils.formatInr(amount)} for '$merchantOrTitle' was recorded recently.",
                availableBalance = currentBalance,
                requiredAmount = amount,
                shortfall = MoneyUtils.ZERO,
                reason = "Recent duplicate transaction detected within the 2-minute duplicate window."
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Validates a budget creation or limit adjustment.
     * Blocks <= 0, and returns a warning if total budgets exceed available money.
     */
    fun validateBudget(
        category: String,
        newLimit: BigDecimal,
        existingBudgets: List<BudgetEntity>,
        availableMoney: BigDecimal
    ): ValidationResult {
        if (category.isBlank()) {
            return ValidationResult.Blocked(
                title = "Invalid Category",
                message = "Budget category name cannot be empty.",
                availableBalance = availableMoney,
                requiredAmount = newLimit,
                shortfall = MoneyUtils.ZERO,
                reason = "Category name required."
            )
        }

        if (newLimit <= MoneyUtils.ZERO) {
            return ValidationResult.Blocked(
                title = "Invalid Budget Limit",
                message = "Budget limit must be strictly greater than ₹0.00.",
                availableBalance = availableMoney,
                requiredAmount = newLimit,
                shortfall = MoneyUtils.ZERO,
                reason = "Limit must be positive."
            )
        }

        // Calculate pending total budget across all categories
        val otherBudgetsTotal = existingBudgets
            .filter { !it.category.equals(category, ignoreCase = true) }
            .fold(MoneyUtils.ZERO) { acc, b -> acc.add(MoneyUtils.of(b.monthlyLimit)) }
        val pendingTotalBudget = otherBudgetsTotal.add(newLimit)

        if (availableMoney > MoneyUtils.ZERO && pendingTotalBudget > availableMoney) {
            val shortfall = pendingTotalBudget.subtract(availableMoney)
            return ValidationResult.Warning(
                title = "Budget Exceeds Available Money",
                message = "Your total planned monthly budget (₹${MoneyUtils.formatInr(pendingTotalBudget)}) exceeds your available money (₹${MoneyUtils.formatInr(availableMoney)}) by ₹${MoneyUtils.formatInr(shortfall)}.",
                availableAmount = availableMoney,
                plannedAmount = pendingTotalBudget,
                shortfall = shortfall,
                affectedFeature = "Budgets",
                actionSuggestion = "You can save this budget, but review planned spending to avoid exceeding cashflow."
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Validates a goal creation or target edit.
     * Budgets and goals operate independently: monthly budget controls category spending limits,
     * while goals manage target savings milestones.
     * Any funding collision acts strictly as an advisory warning card that users can acknowledge,
     * without blocking goal creation or transaction execution.
     */
    fun validateGoal(
        title: String,
        targetAmount: BigDecimal,
        monthlyTarget: BigDecimal,
        availableCashAfterBudgets: BigDecimal
    ): ValidationResult {
        if (title.isBlank()) {
            return ValidationResult.Blocked(
                title = "Invalid Goal Title",
                message = "Goal title cannot be empty.",
                availableBalance = availableCashAfterBudgets,
                requiredAmount = targetAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Goal title required."
            )
        }

        if (targetAmount <= MoneyUtils.ZERO) {
            return ValidationResult.Blocked(
                title = "Invalid Target Amount",
                message = "Goal target amount must be strictly greater than ₹0.00.",
                availableBalance = availableCashAfterBudgets,
                requiredAmount = targetAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Target amount must be positive."
            )
        }

        // Advisory warning card only - never hard-blocks goal creation
        if (monthlyTarget > MoneyUtils.ZERO && monthlyTarget > availableCashAfterBudgets) {
            val shortfall = if (availableCashAfterBudgets > MoneyUtils.ZERO) {
                monthlyTarget.subtract(availableCashAfterBudgets)
            } else {
                monthlyTarget
            }
            return ValidationResult.Warning(
                title = "Goal Target Exceeds Available Funds",
                message = "Monthly goal target of ₹${MoneyUtils.formatInr(monthlyTarget)} exceeds unallocated funds (₹${MoneyUtils.formatInr(availableCashAfterBudgets.coerceAtLeast(MoneyUtils.ZERO))}) by ₹${MoneyUtils.formatInr(shortfall)}.",
                availableAmount = availableCashAfterBudgets.coerceAtLeast(MoneyUtils.ZERO),
                plannedAmount = monthlyTarget,
                shortfall = shortfall,
                affectedFeature = "Goals",
                actionSuggestion = "Advisory: You can still create and fund this goal. Adjusting category budgets or extending the timeline is optional."
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Validates a bill payment prior to execution.
     * Prevents paying an already paid bill, negative amount, or paying with insufficient balance.
     */
    fun validateBillPayment(
        bill: com.example.data.local.BillEntity,
        availableBalance: BigDecimal
    ): ValidationResult {
        if (bill.isPaid) {
            return ValidationResult.Blocked(
                title = "Bill Already Paid",
                message = "The bill '${bill.title}' has already been paid.",
                availableBalance = availableBalance,
                requiredAmount = MoneyUtils.of(bill.amount),
                shortfall = MoneyUtils.ZERO,
                reason = "Bill is already marked as paid."
            )
        }
        val billAmount = MoneyUtils.of(bill.amount)
        if (billAmount <= MoneyUtils.ZERO) {
            return ValidationResult.Blocked(
                title = "Invalid Bill Amount",
                message = "Bill amount must be strictly greater than ₹0.00.",
                availableBalance = availableBalance,
                requiredAmount = billAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Bill amount cannot be zero or negative."
            )
        }
        if (billAmount > availableBalance) {
            val shortfall = billAmount.subtract(availableBalance)
            return ValidationResult.Blocked(
                title = "Insufficient Balance",
                message = "Available Balance: ₹${MoneyUtils.formatInr(availableBalance)}\nBill Amount: ₹${MoneyUtils.formatInr(billAmount)}\nShortfall: ₹${MoneyUtils.formatInr(shortfall)}",
                availableBalance = availableBalance,
                requiredAmount = billAmount,
                shortfall = shortfall,
                reason = "Cannot pay bill without sufficient cash balance."
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Validates a goal funding / allocation transaction.
     */
    fun validateGoalContribution(
        goal: GoalEntity,
        contributionAmount: BigDecimal,
        availableBalance: BigDecimal
    ): ValidationResult {
        if (goal.targetAmount <= 0.0) {
            return ValidationResult.Blocked(
                title = "Invalid Goal",
                message = "Goal target amount must be strictly greater than ₹0.00.",
                availableBalance = availableBalance,
                requiredAmount = contributionAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Goal target amount is invalid."
            )
        }

        if (goal.savedAmount >= goal.targetAmount) {
            return ValidationResult.Blocked(
                title = "Goal Already Fully Funded",
                message = "Goal '${goal.title}' has already reached its target of ₹${MoneyUtils.formatInr(MoneyUtils.of(goal.targetAmount))}.",
                availableBalance = availableBalance,
                requiredAmount = contributionAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Goal is already completed."
            )
        }

        if (contributionAmount <= MoneyUtils.ZERO) {
            return ValidationResult.Blocked(
                title = "Invalid Contribution",
                message = "Contribution amount must be strictly greater than ₹0.00.",
                availableBalance = availableBalance,
                requiredAmount = contributionAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Contribution must be positive."
            )
        }

        if (contributionAmount > availableBalance) {
            val shortfall = contributionAmount.subtract(availableBalance)
            return ValidationResult.Blocked(
                title = "Insufficient Balance",
                message = "Available Balance: ₹${MoneyUtils.formatInr(availableBalance)}\nContribution: ₹${MoneyUtils.formatInr(contributionAmount)}\nShortfall: ₹${MoneyUtils.formatInr(shortfall)}",
                availableBalance = availableBalance,
                requiredAmount = contributionAmount,
                shortfall = shortfall,
                reason = "Cannot allocate more money than your current available spendable balance."
            )
        }

        val target = MoneyUtils.of(goal.targetAmount)
        val saved = MoneyUtils.of(goal.savedAmount)
        val remaining = target.subtract(saved)
        if (contributionAmount > remaining && remaining > MoneyUtils.ZERO) {
            val excess = contributionAmount.subtract(remaining)
            return ValidationResult.Warning(
                title = "Goal Will Be Fully Funded",
                message = "This contribution exceeds the remaining goal target of ₹${MoneyUtils.formatInr(remaining)} by ₹${MoneyUtils.formatInr(excess)}.",
                availableAmount = availableBalance,
                plannedAmount = contributionAmount,
                shortfall = MoneyUtils.ZERO,
                affectedFeature = "Goals",
                actionSuggestion = "Excess funds will be stored in your goal savings balance."
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Validates a loan EMI payment.
     */
    fun validateLoanPayment(
        loan: LoanEntity,
        paymentAmount: BigDecimal,
        availableBalance: BigDecimal
    ): ValidationResult {
        if (loan.remaining <= 0.0) {
            return ValidationResult.Blocked(
                title = "Loan Already Fully Repaid",
                message = "The loan '${loan.title}' has no remaining principal balance.",
                availableBalance = availableBalance,
                requiredAmount = paymentAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Loan is already closed."
            )
        }

        if (paymentAmount <= MoneyUtils.ZERO) {
            return ValidationResult.Blocked(
                title = "Invalid Payment Amount",
                message = "Payment amount must be strictly greater than ₹0.00.",
                availableBalance = availableBalance,
                requiredAmount = paymentAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Payment must be positive."
            )
        }

        val remainingPrincipal = MoneyUtils.of(loan.remaining)
        if (paymentAmount > remainingPrincipal) {
            return ValidationResult.Blocked(
                title = "Overpayment Exceeds Remaining Principal",
                message = "EMI payment of ₹${MoneyUtils.formatInr(paymentAmount)} exceeds remaining loan principal of ₹${MoneyUtils.formatInr(remainingPrincipal)}.",
                availableBalance = availableBalance,
                requiredAmount = paymentAmount,
                shortfall = MoneyUtils.ZERO,
                reason = "Overpayment beyond remaining principal is not permitted."
            )
        }

        if (paymentAmount > availableBalance) {
            val shortfall = paymentAmount.subtract(availableBalance)
            return ValidationResult.Blocked(
                title = "Insufficient Balance",
                message = "Available Balance: ₹${MoneyUtils.formatInr(availableBalance)}\nEMI Payment: ₹${MoneyUtils.formatInr(paymentAmount)}\nShortfall: ₹${MoneyUtils.formatInr(shortfall)}",
                availableBalance = availableBalance,
                requiredAmount = paymentAmount,
                shortfall = shortfall,
                reason = "Cannot pay loan EMI without sufficient cash balance."
            )
        }

        return ValidationResult.Valid
    }
}
