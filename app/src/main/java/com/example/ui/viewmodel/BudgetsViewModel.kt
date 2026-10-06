package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.local.*
import com.example.data.repository.WealthWiseRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

data class BudgetCategoryUiModel(
    val id: Long = 0,
    val name: String,
    val budgetLimit: Double,
    val spent: Double,
    val remaining: Double,
    val progress: Float,
    val percentUsed: Int,
    val isExceeded: Boolean,
    val overrun: Double,
    val colorHex: String = "#3B82F6"
)

data class SuggestedCategoryUiModel(
    val name: String,
    val averageMonthlySpend: Double,
    val suggestedLimit: Double,
    val transactionCount: Int
)

data class BudgetsUiState(
    val categories: List<BudgetCategoryUiModel> = emptyList(),
    val suggestedCategories: List<SuggestedCategoryUiModel> = emptyList(),
    val totalMonthlyBudgetCap: Double = 0.0,
    val currentMonthBudgetedExpenses: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val safeToSpend: Double = 0.0,
    val budgets: List<BudgetEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * BudgetsViewModel is the central reactive state coordinator for the complete Budget Manager.
 *
 * Architecture:
 * Room Database (CategoryDao & TransactionDao)
 *   ↓ Flow
 * Repository Flow
 *   ↓ combine / map
 * ViewModel StateFlow (stateIn)
 *   ↓
 * Compose UI
 */
class BudgetsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WealthWiseRepository = WealthWiseRepository(
        WealthWiseDatabase.getDatabase(application),
        application
    )
) : AndroidViewModel(application) {

    private val _activeUserId = MutableStateFlow(repository.currentUserId())
    val activeUserId: StateFlow<String> = _activeUserId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    init {
        try {
            FirebaseManager.auth?.addAuthStateListener { auth ->
                val uid = auth.currentUser?.uid.orEmpty()
                _activeUserId.value = uid
            }
        } catch (_: Throwable) {}
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BudgetsUiState> = _activeUserId
        .flatMapLatest { userId ->
            if (userId.isBlank()) {
                flowOf(BudgetsUiState(isLoading = false))
            } else {
                combine(
                    repository.getCategoriesFlow(userId),
                    repository.budgetsForUser(userId),
                    repository.observeTransactions(userId),
                    _errorMessage
                ) { roomCategories, legacyBudgets, transactions, errorMsg ->
                    computeUiState(roomCategories, legacyBudgets, transactions, errorMsg, userId)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BudgetsUiState(isLoading = true)
        )

    private fun computeUiState(
        roomCategories: List<CategoryEntity>,
        legacyBudgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        errorMsg: String?,
        userId: String
    ): BudgetsUiState {
        // Fallback to legacy budgets mapped to CategoryEntity if roomCategories is initially empty
        val effectiveCategories: List<CategoryEntity> = if (roomCategories.isNotEmpty()) {
            roomCategories
        } else if (legacyBudgets.isNotEmpty()) {
            legacyBudgets.mapIndexed { index, it ->
                CategoryEntity(
                    id = (index + 1).toLong(),
                    userId = it.userId,
                    name = it.category,
                    budgetLimit = it.monthlyLimit,
                    colorHex = it.colorHex
                )
            }
        } else {
            emptyList()
        }

        // 1. Current calendar month boundary (startOfMonth 00:00:00 to endOfMonth 23:59:59.999 local timezone)
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = cal.timeInMillis

        val endCal = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endOfMonth = endCal.timeInMillis

        // Valid completed current month expense transactions
        val monthlyExpenseTxns = transactions.filter { txn ->
            HomeViewModel.isCompletedTransaction(txn) &&
                    !txn.isTransfer &&
                    !txn.isGoalTransfer &&
                    !txn.type.equals("GOAL_TRANSFER", ignoreCase = true) &&
                    !txn.eventType.equals("GOAL_TRANSFER", ignoreCase = true) &&
                    !txn.category.equals("Savings Goal", ignoreCase = true) &&
                    !txn.category.equals("Goal Allocation", ignoreCase = true) &&
                    (txn.type.equals("EXPENSE", ignoreCase = true) || txn.type.equals("debit", ignoreCase = true) || txn.isOutflow) &&
                    txn.timestamp in startOfMonth..endOfMonth
        }

        // 2. Compute category spending matching case-insensitively with trim()
        val categoryModels = effectiveCategories.map { cat ->
            val spent = monthlyExpenseTxns
                .filter { it.category.trim().equals(cat.name.trim(), ignoreCase = true) }
                .sumOf { kotlin.math.abs(it.amount) }

            val remaining = (cat.budgetLimit - spent).coerceAtLeast(0.0)
            val progress = if (cat.budgetLimit > 0.0) {
                (spent / cat.budgetLimit).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            val percentUsed = if (cat.budgetLimit > 0.0) {
                ((spent / cat.budgetLimit) * 100).toInt().coerceAtLeast(0)
            } else {
                0
            }
            val isExceeded = cat.budgetLimit > 0.0 && spent > cat.budgetLimit
            val overrun = (spent - cat.budgetLimit).coerceAtLeast(0.0)

            BudgetCategoryUiModel(
                id = cat.id,
                name = cat.name,
                budgetLimit = cat.budgetLimit,
                spent = spent,
                remaining = remaining,
                progress = progress,
                percentUsed = percentUsed,
                isExceeded = isExceeded,
                overrun = overrun,
                colorHex = cat.colorHex
            )
        }

        val totalMonthlyBudgetCap = effectiveCategories.sumOf { it.budgetLimit }
        val existingCategoryNames = effectiveCategories.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()
        val currentMonthBudgetedExpenses = monthlyExpenseTxns
            .filter { it.category.trim().lowercase(Locale.ROOT) in existingCategoryNames }
            .sumOf { kotlin.math.abs(it.amount) }

        val remainingBudget = (totalMonthlyBudgetCap - currentMonthBudgetedExpenses).coerceAtLeast(0.0)
        val safeToSpend = max(0.0, totalMonthlyBudgetCap - currentMonthBudgetedExpenses)

        // 3. Suggested for You Recommendations - removed per requirement
        val suggestedCategories = emptyList<SuggestedCategoryUiModel>()

        val legacyBudgetList = effectiveCategories.map {
            BudgetEntity(
                userId = it.userId,
                category = it.name,
                monthlyLimit = it.budgetLimit,
                colorHex = it.colorHex
            )
        }

        return BudgetsUiState(
            categories = categoryModels,
            suggestedCategories = suggestedCategories,
            totalMonthlyBudgetCap = totalMonthlyBudgetCap,
            currentMonthBudgetedExpenses = currentMonthBudgetedExpenses,
            remainingBudget = remainingBudget,
            safeToSpend = safeToSpend,
            budgets = legacyBudgetList,
            isLoading = false,
            errorMessage = errorMsg
        )
    }

    /**
     * Adds a new budget category into Room.
     * Enforces case-insensitive duplicate prevention.
     */
    fun addCategory(name: String, limit: Double) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = "Category name cannot be empty"
            return
        }
        if (limit <= 0.0) {
            _errorMessage.value = "Budget limit must be strictly positive"
            return
        }

        val userId = _activeUserId.value
        viewModelScope.launch {
            try {
                val existing = repository.getCategories(userId)
                val isDuplicate = existing.any { it.name.trim().equals(trimmed, ignoreCase = true) }
                if (isDuplicate) {
                    _errorMessage.value = "Category \"$trimmed\" already exists"
                    return@launch
                }

                val newCategory = CategoryEntity(
                    userId = userId,
                    name = trimmed,
                    budgetLimit = limit,
                    colorHex = pickCategoryColor(existing.size)
                )
                repository.insertCategory(newCategory)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to add category"
            }
        }
    }

    /**
     * Updates an existing category's name and limit.
     * Allows current category to retain its own name (ignores own ID for duplicate check).
     */
    fun updateCategory(id: Long, name: String, limit: Double) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = "Category name cannot be empty"
            return
        }
        if (limit <= 0.0) {
            _errorMessage.value = "Budget limit must be strictly positive"
            return
        }

        val userId = _activeUserId.value
        viewModelScope.launch {
            try {
                val existing = repository.getCategories(userId)
                val isDuplicate = existing.any { it.id != id && it.name.trim().equals(trimmed, ignoreCase = true) }
                if (isDuplicate) {
                    _errorMessage.value = "Category \"$trimmed\" already exists"
                    return@launch
                }

                val target = existing.firstOrNull { it.id == id }
                val updated = CategoryEntity(
                    id = id,
                    userId = userId,
                    name = trimmed,
                    budgetLimit = limit,
                    colorHex = target?.colorHex ?: "#3B82F6"
                )
                repository.updateCategory(updated)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update category"
            }
        }
    }

    /**
     * Deletes a category by id from Room.
     */
    fun deleteCategory(id: Long) {
        val userId = _activeUserId.value
        viewModelScope.launch {
            try {
                repository.deleteCategory(id)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to delete category"
            }
        }
    }

    /**
     * Quick Add chip action from "Suggested for You".
     * Inserts category immediately into Room so both Budgets and Home reactive flows emit.
     */
    fun quickAddSuggestedCategory(suggested: SuggestedCategoryUiModel) {
        addCategory(suggested.name, suggested.suggestedLimit)
    }

    /**
     * Backward-compatible legacy updateBudget method.
     */
    fun updateBudget(category: String, newLimit: Double) {
        if (newLimit <= 0.0) {
            _errorMessage.value = "Budget limit must be strictly positive"
            return
        }
        val trimmed = category.trim()
        val userId = _activeUserId.value
        viewModelScope.launch {
            try {
                repository.updateBudget(trimmed, newLimit)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update budget"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun pickCategoryColor(index: Int): String {
        val colors = listOf(
            "#10B981", "#3B82F6", "#F59E0B", "#EF4444", "#8B5CF6",
            "#EC4899", "#14B8A6", "#6366F1", "#F97316", "#06B6D4"
        )
        return colors[index % colors.size]
    }
}
