package com.example.ui.investments

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.TransactionEntity
import com.example.ui.trading.TradingScreen
import com.example.ui.viewmodel.TradingViewModel

/**
 * Investments & Trading Hub Screen.
 * Overhauled to integrate reactive trade journaling, automated broker capital tracking from the central ledger,
 * and independent regulatory verification.
 */
@Composable
fun InvestmentsScreen(
    investmentsTotal: Double = 0.0,
    transactions: List<TransactionEntity> = emptyList(),
    availableCashBalance: Double = 0.0,
    onBack: () -> Unit,
    tradingViewModel: TradingViewModel = viewModel()
) {
    TradingScreen(
        viewModel = tradingViewModel,
        onBack = onBack
    )
}
