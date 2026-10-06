package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LoanDao
import com.example.data.local.LoanEntity
import com.example.data.local.LoanRepaymentEntity
import com.example.data.local.WealthWiseDatabase
import com.example.data.repository.WealthWiseRepository
import com.example.domain.loan.LoanCalculations
import com.example.domain.loan.LoanProgressItem
import com.example.domain.loan.LoanValidationResult
import com.example.domain.loan.LoansUiState
import com.example.domain.loan.UpcomingEmiInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Production Loans & Debt ViewModel.
 *
 * Responsibilities:
 * 1. Reactive flow collection from [LoanDao] and [WealthWiseRepository] scoped strictly by user ID.
 * 2. Real-time debt calculation, repayment progress, and upcoming EMI dates.
 * 3. Validation and persistence of new loans and manual repayment records.
 * 4. Safe closing of loans while preserving historical repayment records.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoansViewModel @JvmOverloads constructor(
    application: Application,
    private val loanDao: LoanDao = WealthWiseDatabase.getDatabase(application).loanDao(),
    private val repository: WealthWiseRepository = WealthWiseRepository(
        WealthWiseDatabase.getDatabase(application),
        application
    ),
    private val authUidProvider: () -> String = {
        try {
            val fbUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (!fbUid.isNullOrBlank()) fbUid else repository.currentUserId()
        } catch (_: Throwable) {
            repository.currentUserId()
        }
    },
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) : AndroidViewModel(application) {

    private val _currentUserId = MutableStateFlow(authUidProvider())
    private val _errorMessage = MutableStateFlow<String?>(null)

    init {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
                _currentUserId.value = auth.currentUser?.uid ?: repository.currentUserId()
            }
        } catch (_: Throwable) {}
        refreshUserSession()
    }

    fun refreshUserSession() {
        _currentUserId.value = authUidProvider()
    }

    val uiState: StateFlow<LoansUiState> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isBlank()) {
                flowOf(LoansUiState(isLoading = false))
            } else {
                combine(
                    loanDao.getActiveLoansFlow(uid),
                    loanDao.getAllRepaymentsForUserFlow(uid),
                    _errorMessage
                ) { activeLoans, allRepayments, error ->
                    val repaymentsByLoan = allRepayments.groupBy { it.loanId }

                    val progressItems = activeLoans.map { loan ->
                        val loanRepayments = repaymentsByLoan[loan.id] ?: emptyList()
                        val paidSum = loanRepayments.sumOf { it.paidAmount }
                        val remaining = LoanCalculations.calculateRemainingAmount(loan.principalAmount, paidSum)
                        val progress = LoanCalculations.calculateProgress(loan.principalAmount, paidSum)
                        val paidCount = loanRepayments.size
                        val remainingCount = maxOf(0, loan.totalTenureMonths - paidCount)

                        LoanProgressItem(
                            loan = loan,
                            amountPaid = paidSum,
                            remainingAmount = remaining,
                            progress = progress,
                            paidEmiCount = paidCount,
                            remainingEmiCount = remainingCount
                        )
                    }

                    // Calculate next upcoming EMI across all active loans
                    val nextUpcomingEmi = findNextUpcomingEmi(activeLoans, repaymentsByLoan)

                    val totalOriginal = activeLoans.sumOf { it.principalAmount }
                    val totalRepaid = allRepayments.sumOf { it.paidAmount }
                    val totalRemaining = progressItems.sumOf { it.remainingAmount }

                    LoansUiState(
                        isLoading = false,
                        totalOriginalDebt = totalOriginal,
                        totalDebtRepaid = totalRepaid,
                        totalRemainingDebt = totalRemaining,
                        nextUpcomingEmi = nextUpcomingEmi,
                        loansWithProgress = progressItems,
                        errorMessage = error
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = sharingStarted,
            initialValue = LoansUiState(isLoading = true)
        )

    private fun findNextUpcomingEmi(
        activeLoans: List<LoanEntity>,
        repaymentsByLoan: Map<Long, List<LoanRepaymentEntity>>
    ): UpcomingEmiInfo? {
        if (activeLoans.isEmpty()) return null

        val today = LocalDate.now()
        var earliestInfo: UpcomingEmiInfo? = null
        var minDays = Int.MAX_VALUE

        for (loan in activeLoans) {
            val loanRepayments = repaymentsByLoan[loan.id] ?: emptyList()
            // Check if paid in the current month
            val isCurrentMonthPaid = loanRepayments.any { rep ->
                val repDate = LocalDate.ofEpochDay(rep.paymentDate / (24 * 3600 * 1000L))
                repDate.year == today.year && repDate.month == today.month
            }

            val nextDueDate = LoanCalculations.calculateNextDueDate(
                emiDueDayOfMonth = loan.emiDueDayOfMonth,
                currentDate = today,
                isCurrentMonthPaid = isCurrentMonthPaid
            )
            val daysRemaining = LoanCalculations.calculateDaysRemaining(today, nextDueDate)

            if (daysRemaining < minDays) {
                minDays = daysRemaining
                earliestInfo = UpcomingEmiInfo(
                    loanId = loan.id,
                    lenderName = loan.lenderName,
                    loanName = loan.loanName,
                    amount = loan.emiAmount,
                    dueDate = LoanCalculations.toEpochMilli(nextDueDate),
                    daysRemaining = daysRemaining
                )
            }
        }

        return earliestInfo
    }

    /**
     * Validates and persists a new loan obligation.
     */
    fun addLoan(
        loanName: String,
        lenderName: String,
        loanType: String,
        principalAmount: Double,
        emiAmount: Double,
        interestRate: Double,
        totalTenureMonths: Int,
        emiDueDayOfMonth: Int,
        onResult: (LoanValidationResult) -> Unit = {}
    ) {
        val uid = _currentUserId.value
        if (uid.isBlank()) {
            val err = LoanValidationResult.Error("No authenticated user session found.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (loanName.isBlank()) {
            val err = LoanValidationResult.Error("Loan name must not be blank.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (lenderName.isBlank()) {
            val err = LoanValidationResult.Error("Lender name must not be blank.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (!LoanEntity.isValidLoanType(loanType)) {
            val err = LoanValidationResult.Error("Unsupported loan type: '$loanType'.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (principalAmount <= 0.0 || principalAmount.isNaN() || principalAmount.isInfinite()) {
            val err = LoanValidationResult.Error("Principal amount must be greater than zero.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (emiAmount <= 0.0 || emiAmount.isNaN() || emiAmount.isInfinite()) {
            val err = LoanValidationResult.Error("EMI amount must be greater than zero.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (interestRate < 0.0 || interestRate.isNaN() || interestRate.isInfinite()) {
            val err = LoanValidationResult.Error("Interest rate must be non-negative.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (totalTenureMonths <= 0) {
            val err = LoanValidationResult.Error("Tenure must be greater than zero.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (emiDueDayOfMonth !in 1..31) {
            val err = LoanValidationResult.Error("EMI due day must be between 1 and 31.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        viewModelScope.launch {
            try {
                val entity = LoanEntity(
                    userId = uid,
                    loanName = loanName.trim(),
                    lenderName = lenderName.trim(),
                    loanType = loanType.trim().uppercase(),
                    principalAmount = principalAmount,
                    emiAmount = emiAmount,
                    interestRate = interestRate,
                    totalTenureMonths = totalTenureMonths,
                    emiDueDayOfMonth = emiDueDayOfMonth,
                    startDate = System.currentTimeMillis(),
                    isClosed = false
                )
                loanDao.insertLoan(entity)
                _errorMessage.value = null
                onResult(LoanValidationResult.Valid)
            } catch (t: Throwable) {
                val err = LoanValidationResult.Error(t.localizedMessage ?: "Failed to save loan.")
                _errorMessage.value = err.message
                onResult(err)
            }
        }
    }

    /**
     * Records a manual repayment for a loan.
     * Atomically creates a financial ledger transaction and updates the loan's repayment records.
     */
    fun recordManualRepayment(
        loanId: Long,
        amount: Double,
        paymentMethod: String = "Bank Transfer",
        onResult: (LoanValidationResult) -> Unit = {}
    ) {
        val uid = _currentUserId.value
        if (uid.isBlank()) {
            val err = LoanValidationResult.Error("No authenticated user session found.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            val err = LoanValidationResult.Error("Repayment amount must be greater than zero.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        viewModelScope.launch {
            try {
                repository.recordManualLoanRepayment(loanId, amount, paymentMethod)
                _errorMessage.value = null
                onResult(LoanValidationResult.Valid)
            } catch (t: Throwable) {
                val err = LoanValidationResult.Error(t.localizedMessage ?: "Failed to record repayment.")
                _errorMessage.value = err.message
                onResult(err)
            }
        }
    }

    /**
     * Closes an active loan without removing historical repayment records.
     */
    fun closeLoan(
        loanId: Long,
        onResult: (LoanValidationResult) -> Unit = {}
    ) {
        val uid = _currentUserId.value
        if (uid.isBlank()) {
            val err = LoanValidationResult.Error("No authenticated user session found.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        viewModelScope.launch {
            try {
                repository.closeLoan(loanId, uid)
                _errorMessage.value = null
                onResult(LoanValidationResult.Valid)
            } catch (t: Throwable) {
                val err = LoanValidationResult.Error(t.localizedMessage ?: "Failed to close loan.")
                _errorMessage.value = err.message
                onResult(err)
            }
        }
    }

    /**
     * Retrieves reactive repayment history for a specific loan.
     */
    fun getRepaymentsForLoan(loanId: Long): Flow<List<LoanRepaymentEntity>> {
        val uid = _currentUserId.value
        return if (uid.isNotBlank()) {
            loanDao.getRepaymentsForLoanFlow(loanId, uid)
        } else {
            flowOf(emptyList())
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
