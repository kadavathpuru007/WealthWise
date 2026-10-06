package com.example.ui.loans

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.LoanEntity
import com.example.data.local.TransactionEntity
import com.example.ui.viewmodel.LoansViewModel

/**
 * LoansDebtsScreen routes directly to the authoritative [LoansScreen].
 * Provides full backward compatibility for navigation while delegating
 * to the production-quality, reactive Loans & Debt Management module.
 */
@Composable
fun LoansDebtsScreen(
    loansRemaining: Double = 0.0,
    loans: List<LoanEntity> = emptyList(),
    transactions: List<TransactionEntity> = emptyList(),
    safeToSpend: Double = 0.0,
    onAddLoanClick: () -> Unit = {},
    onPayEmi: (LoanEntity) -> Unit = {},
    onDeleteLoan: (Int) -> Unit = {},
    onBack: () -> Unit,
    loansViewModel: LoansViewModel = viewModel()
) {
    LoansScreen(
        onBack = onBack,
        viewModel = loansViewModel
    )
}