package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.local.AppDatabaseProvider
import com.example.data.local.TradeDao
import com.example.data.local.TradeEntity
import com.example.data.local.TransactionDao
import com.example.domain.trading.BrokerCapitalCalculator
import com.example.domain.trading.BrokerSecurityState
import com.example.domain.trading.BrokerTrustEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * UI State for the Trading & Investment Hub.
 */
data class TradingUiState(
    val totalDeployedCapital: Double = 0.0,
    val totalPayoutsWithdrawn: Double = 0.0,
    val netCapitalInMarket: Double = 0.0,
    val totalRealizedPnl: Double = 0.0,
    val winRatePercentage: Int = 0,
    val tradesList: List<TradeEntity> = emptyList(),
    val brokerTrustStatus: BrokerSecurityState = BrokerSecurityState.Neutral,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

sealed class TradeValidationResult {
    data object Success : TradeValidationResult()
    data class Error(val message: String) : TradeValidationResult()
}

/**
 * TradingViewModel combines TradeDao flows, TransactionDao broker movements,
 * device broker trust status, and the authenticated user scope into a single reactive StateFlow.
 */
class TradingViewModel(
    application: Application,
    private val tradeDao: TradeDao,
    private val transactionDao: TransactionDao,
    private val brokerTrustEngine: BrokerTrustEngine,
    private val authUidProvider: () -> String,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(5000)
) : AndroidViewModel(application) {

    // Default constructor for standard Android ViewModel creation
    constructor(application: Application) : this(
        application = application,
        tradeDao = AppDatabaseProvider.getDatabase(application).tradeDao(),
        transactionDao = AppDatabaseProvider.getDatabase(application).transactionDao(),
        brokerTrustEngine = BrokerTrustEngine(application),
        authUidProvider = {
            FirebaseManager.currentUser?.uid.orEmpty()
        },
        sharingStarted = SharingStarted.WhileSubscribed(5000)
    )

    private val _currentUserId = MutableStateFlow(authUidProvider())
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateAuthenticatedUser(userId: String) {
        _currentUserId.value = userId
    }

    fun clearError() {
        _errorMessage.value = null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TradingUiState> = _currentUserId
        .flatMapLatest { uid ->
            if (uid.isBlank()) {
                flowOf(
                    TradingUiState(
                        brokerTrustStatus = BrokerSecurityState.Neutral,
                        isLoading = false
                    )
                )
            } else {
                combine(
                    tradeDao.getAllTradesFlow(uid),
                    transactionDao.getTransactionsFlow(uid),
                    brokerTrustEngine.brokerSecurityFlow,
                    _errorMessage
                ) { trades, transactions, securityStatus, error ->
                    // Calculate Trade Journal Metrics
                    val closedTrades = trades.filter {
                        it.status.equals(TradeEntity.STATUS_CLOSED, ignoreCase = true)
                    }
                    val winningTrades = closedTrades.filter { it.realizedPnl > 0.0 }
                    val winRate = if (closedTrades.isNotEmpty()) {
                        ((winningTrades.size.toDouble() / closedTrades.size.toDouble()) * 100).toInt()
                    } else {
                        0
                    }
                    val totalRealizedPnl = closedTrades.sumOf { it.realizedPnl }

                    // Calculate Broker Capital Flow Metrics from Central Ledger
                    val capitalMetrics = BrokerCapitalCalculator.calculateMetrics(transactions)

                    TradingUiState(
                        totalDeployedCapital = capitalMetrics.totalDeployedCapital,
                        totalPayoutsWithdrawn = capitalMetrics.totalPayoutsWithdrawn,
                        netCapitalInMarket = capitalMetrics.netCapitalInMarket,
                        totalRealizedPnl = totalRealizedPnl,
                        winRatePercentage = winRate,
                        tradesList = trades,
                        brokerTrustStatus = securityStatus,
                        isLoading = false,
                        errorMessage = error
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = sharingStarted,
            initialValue = TradingUiState(isLoading = true)
        )

    /**
     * Validates and records a trade entry in the trade journal.
     */
    fun logTrade(
        symbol: String,
        type: String,
        buyPrice: Double,
        sellPrice: Double,
        quantity: Int,
        broker: String,
        status: String = TradeEntity.STATUS_CLOSED,
        tradeDate: Long = System.currentTimeMillis(),
        onResult: (TradeValidationResult) -> Unit = {}
    ) {
        val uid = _currentUserId.value
        if (uid.isBlank()) {
            val err = TradeValidationResult.Error("No authenticated user session found.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (symbol.isBlank()) {
            val err = TradeValidationResult.Error("Symbol must not be blank.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (buyPrice.isNaN() || buyPrice.isInfinite() || buyPrice <= 0.0) {
            val err = TradeValidationResult.Error("Buy price must be greater than zero.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (sellPrice.isNaN() || sellPrice.isInfinite() || sellPrice <= 0.0) {
            val err = TradeValidationResult.Error("Sell price must be greater than zero.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (quantity <= 0) {
            val err = TradeValidationResult.Error("Quantity must be greater than zero.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        val normType = type.trim().uppercase(Locale.ROOT)
        if (!TradeEntity.SUPPORTED_TYPES.contains(normType)) {
            val err = TradeValidationResult.Error("Trade type '$type' is not supported. Use: ${TradeEntity.SUPPORTED_TYPES.joinToString()}")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        if (broker.isBlank()) {
            val err = TradeValidationResult.Error("Broker name must not be blank.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        val normStatus = status.trim().uppercase(Locale.ROOT)
        if (normStatus != TradeEntity.STATUS_CLOSED && normStatus != TradeEntity.STATUS_OPEN) {
            val err = TradeValidationResult.Error("Status must be OPEN or CLOSED.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        val realizedPnl = TradeEntity.calculateRealizedPnl(
            status = normStatus,
            buyPrice = buyPrice,
            sellPrice = sellPrice,
            quantity = quantity
        )

        if (realizedPnl.isNaN() || realizedPnl.isInfinite()) {
            val err = TradeValidationResult.Error("Calculated P&L value is invalid.")
            _errorMessage.value = err.message
            onResult(err)
            return
        }

        val trade = TradeEntity(
            userId = uid,
            symbol = symbol.trim().uppercase(Locale.ROOT),
            tradeType = normType,
            buyPrice = buyPrice,
            sellPrice = sellPrice,
            quantity = quantity,
            realizedPnl = realizedPnl,
            tradeDate = tradeDate,
            brokerName = broker.trim(),
            status = normStatus
        )

        viewModelScope.launch {
            tradeDao.insertTrade(trade)
            _errorMessage.value = null
            onResult(TradeValidationResult.Success)
        }
    }

    /**
     * Safely deletes a trade journal entry.
     * Crucially: Deleting a trade does NOT delete or alter any financial transactions in the central ledger.
     */
    fun deleteTrade(trade: TradeEntity) {
        viewModelScope.launch {
            tradeDao.deleteTrade(trade)
        }
    }
}
