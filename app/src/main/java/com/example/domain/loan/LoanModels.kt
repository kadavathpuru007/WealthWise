package com.example.domain.loan

import com.example.data.local.LoanEntity

/**
 * Information regarding the next upcoming EMI across active loans.
 */
data class UpcomingEmiInfo(
    val loanId: Long,
    val lenderName: String,
    val loanName: String,
    val amount: Double,
    val dueDate: Long,
    val daysRemaining: Int
)

/**
 * Comprehensive progress item representing an active loan and its payment status.
 */
data class LoanProgressItem(
    val loan: LoanEntity,
    val amountPaid: Double,
    val remainingAmount: Double,
    val progress: Float,
    val paidEmiCount: Int,
    val remainingEmiCount: Int
)

/**
 * Reactive UI state for the Loans & Debt Management screen.
 */
data class LoansUiState(
    val isLoading: Boolean = true,
    val totalOriginalDebt: Double = 0.0,
    val totalDebtRepaid: Double = 0.0,
    val totalRemainingDebt: Double = 0.0,
    val nextUpcomingEmi: UpcomingEmiInfo? = null,
    val loansWithProgress: List<LoanProgressItem> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Result of loan or repayment input validation.
 */
sealed interface LoanValidationResult {
    data object Valid : LoanValidationResult
    data class Error(val message: String) : LoanValidationResult
}
