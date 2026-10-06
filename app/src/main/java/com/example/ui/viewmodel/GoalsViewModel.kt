package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.local.*
import com.example.data.repository.GoalsRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.FinancialEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max

data class GoalsUiState(
    val goals: List<GoalEntity> = emptyList(),
    val actualBalance: Double = 0.0,
    val totalSavedAcrossGoals: Double = 0.0,
    val availableBalance: Double = 0.0,
    val isFunding: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * GoalsViewModel enforces strict mathematical rules for goal savings:
 *
 * 1. Observes GoalDao.getGoalsFlow(userId) reactively.
 * 2. Available Balance is dedicated strictly to Goals:
 *    Equals: Actual Balance - Total Saved Across Goals.
 *    Active budget caps must NEVER reduce Available Balance.
 *
 * 3. When a user funds a goal:
 *    - Validates amount > 0 and amount <= Available Balance.
 *    - Atomically persists GoalAllocationEntity, updates goal savedAmount, and creates ledger transaction.
 */
class GoalsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WealthWiseRepository = WealthWiseRepository(
        WealthWiseDatabase.getDatabase(application),
        application
    ),
    private val goalsRepository: GoalsRepository = GoalsRepository(
        WealthWiseDatabase.getDatabase(application).goalDao()
    ),
    private val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    private val _activeUserId = MutableStateFlow(repository.currentUserId())
    val activeUserId: StateFlow<String> = _activeUserId.asStateFlow()

    init {
        try {
            FirebaseManager.auth?.addAuthStateListener { auth ->
                _activeUserId.value = auth.currentUser?.uid.orEmpty()
            }
        } catch (_: Throwable) {}

        @OptIn(ExperimentalCoroutinesApi::class)
        _activeUserId.flatMapLatest { userId ->
            if (userId.isBlank()) {
                flowOf(Triple(emptyList<TransactionEntity>(), emptyList<GoalEntity>(), 0.0))
            } else {
                combine(
                    repository.observeTransactions(userId),
                    goalsRepository.getGoalsFlow(userId),
                    userPreferencesRepository.getInitialBalanceFlow(userId)
                ) { txns, goals, initialBalance ->
                    Triple(txns, goals, initialBalance)
                }
            }
        }.onEach { (txns, goals, initialBalance) ->
            val completedTxns = txns.filter { it.status.uppercase() != "FAILED" }

            val income = completedTxns
                .filter { FinancialCalculationEngine.determineEventType(it) == FinancialEventType.INCOME }
                .sumOf { kotlin.math.abs(it.amount) }

            val realExpenses = completedTxns
                .filter {
                    val type = FinancialCalculationEngine.determineEventType(it)
                    type == FinancialEventType.EXPENSE || type == FinancialEventType.SUBSCRIPTION_PAYMENT
                }
                .sumOf { kotlin.math.abs(it.amount) }

            val refunds = completedTxns
                .filter {
                    val type = FinancialCalculationEngine.determineEventType(it)
                    type == FinancialEventType.REFUND || type == FinancialEventType.REVERSAL
                }
                .sumOf { kotlin.math.abs(it.amount) }

            val netRealExpenses = max(0.0, realExpenses - refunds)
            val actualBalance = initialBalance + income - netRealExpenses
            val totalSaved = goals.sumOf { it.savedAmount }

            // Available Balance = Actual Balance - Total Saved Across Goals (Active budget caps NEVER reduce Available Balance)
            val available = max(0.0, actualBalance - totalSaved)

            _uiState.update {
                it.copy(
                    goals = goals,
                    actualBalance = actualBalance,
                    totalSavedAcrossGoals = totalSaved,
                    availableBalance = available
                )
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Funds a goal after validating that amount <= Available Balance.
     * Enforces single atomic repository operation and prevents duplicate submissions.
     */
    fun fundGoal(goalId: Long, amount: Double): Boolean {
        if (_uiState.value.isFunding) return false

        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            _uiState.update { it.copy(errorMessage = "Contribution amount must be greater than 0") }
            return false
        }

        val currentAvailable = _uiState.value.availableBalance
        if (amount > currentAvailable) {
            val errorMsg = "Cannot fund goal: Amount (₹$amount) exceeds Available Balance (₹$currentAvailable)"
            _uiState.update { it.copy(errorMessage = errorMsg) }
            return false
        }

        val uid = _activeUserId.value
        val goal = _uiState.value.goals.find { it.id == goalId }
        if (goal != null && goal.userId.isNotBlank() && uid.isNotBlank() && goal.userId != uid) {
            _uiState.update { it.copy(errorMessage = "Unauthorized: Goal does not belong to active user") }
            return false
        }

        _uiState.update { it.copy(isFunding = true) }
        viewModelScope.launch {
            try {
                repository.fundGoal(goalId, amount, uid)
                _uiState.update {
                    it.copy(
                        isFunding = false,
                        errorMessage = null,
                        successMessage = "Successfully allocated ₹$amount towards goal!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isFunding = false,
                        errorMessage = e.localizedMessage ?: "Failed to fund goal"
                    )
                }
            }
        }
        return true
    }

    fun fundGoal(goalId: Int, amount: Double): Boolean = fundGoal(goalId.toLong(), amount)

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
