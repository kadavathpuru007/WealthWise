package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.local.TransactionEntity
import com.example.data.local.WealthWiseDatabase
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.ActivityAnalyticsResult
import com.example.domain.financial.PageAnalyticsEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class ActivityUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val filter: String = "ALL", // "ALL", "INFLOW", "OUTFLOW"
    val period: String = "1 DAY", // "1 DAY", "WEEK", "MONTH", "YEAR"
    val analytics: ActivityAnalyticsResult? = null,
    val isLoading: Boolean = false
)

/**
 * ActivityViewModel provides authoritative, read-only observation of Room transaction Flow
 * for ActivityLedgerScreen. Automatically recalculates activity graphs across ALL/INFLOW/OUTFLOW
 * and 1 DAY/WEEK/MONTH/YEAR periods without app restart.
 */
class ActivityViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WealthWiseRepository = WealthWiseRepository(
        WealthWiseDatabase.getDatabase(application),
        application
    )
) : AndroidViewModel(application) {

    private val _filter = MutableStateFlow("ALL")
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _period = MutableStateFlow("1 DAY")
    val period: StateFlow<String> = _period.asStateFlow()

    private val _activeUserId = MutableStateFlow(repository.currentUserId())
    val activeUserId: StateFlow<String> = _activeUserId.asStateFlow()

    fun setFilter(newFilter: String) {
        _filter.value = when (newFilter.uppercase()) {
            "INFLOW", "INCOME" -> "INFLOW"
            "OUTFLOW", "EXPENSE" -> "OUTFLOW"
            else -> "ALL"
        }
    }

    fun setPeriod(newPeriod: String) {
        _period.value = newPeriod
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ActivityUiState> = _activeUserId
        .flatMapLatest { userId ->
            if (userId.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeTransactions(userId)
            }
        }
        .combine(_filter) { txns, f -> Pair(txns, f) }
        .combine(_period) { (txns, f), p ->
            val analytics = PageAnalyticsEngine.calculateActivityAnalytics(txns, p, f)
            ActivityUiState(
                transactions = txns,
                filter = f,
                period = p,
                analytics = analytics,
                isLoading = false
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ActivityUiState(isLoading = true)
        )

    init {
        try {
            FirebaseManager.auth?.addAuthStateListener { auth ->
                _activeUserId.value = auth.currentUser?.uid.orEmpty()
            }
        } catch (_: Throwable) {}
    }
}
