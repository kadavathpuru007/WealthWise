package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.local.*
import com.example.data.repository.GoalsRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.IncomeExpensePoint
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class HomeUiState(
    val availableBalance: Double = 0.0,
    val safeToSpend: Double = 0.0,
    val actualBalance: Double = 0.0,
    val isLoading: Boolean = false
)

data class HomeOverviewData(
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<GoalEntity>,
    val totalGoalsAllocated: Double,
    val startingBalance: Double,
    val categories: List<CategoryEntity> = emptyList(),
    val activeLoans: List<LoanEntity> = emptyList(),
    val totalRemainingDebt: Double = 0.0
)

data class CategoryBudgetLimit(
    val name: String,
    val limit: Double
)

data class CategorySpending(
    val name: String,
    val limit: Double,
    val spent: Double,
    val remaining: Double,
    val progress: Float,
    val percentUsed: Int = if (limit > 0) ((spent / limit) * 100).toInt() else 0,
    val isExceeded: Boolean = limit > 0 && spent > limit,
    val overrun: Double = (spent - limit).coerceAtLeast(0.0)
)

data class BalanceCalculationResult(
    val totalIncome: Double,
    val totalExpense: Double,
    val startingBalance: Double,
    val actualBalance: Double,
    val availableBalance: Double,
    val committedToGoals: Double,
    val safeToSpend: Double
)

data class BudgetStatusResult(
    val totalBudget: Double,
    val totalExpense: Double,
    val remainingBudget: Double,
    val percentageUsed: Int
)

data class HomeFinancialState(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val startingBalance: Double = 0.0,
    val actualBalance: Double = 0.0,
    val availableBalance: Double = 0.0,
    val committedToGoals: Double = 0.0,
    val safeToSpend: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val totalBudget: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val percentageUsed: Int = 0,
    val transactions: List<TransactionEntity> = emptyList(),
    val monthlyTransactions: List<TransactionEntity> = emptyList(),
    val categorySpendings: List<CategorySpending> = emptyList(),
    val topActiveCategories: List<CategorySpending> = emptyList(),
    val smartSuggestion: String? = null,
    val chartPoints: List<IncomeExpensePoint> = emptyList(),
    val activeLoans: List<LoanEntity> = emptyList(),
    val activeLoanCount: Int = 0,
    val totalRemainingDebt: Double = 0.0,
    val isLoading: Boolean = false
)

fun isCompletedTransaction(txn: TransactionEntity): Boolean {
    val s = txn.status.uppercase(Locale.ROOT)
    val isFailed = s == "FAILED" || s == "REJECTED" || txn.eventType.equals("FAILED", ignoreCase = true)
    val isPending = s == "PENDING" || txn.eventType.equals("PENDING", ignoreCase = true)
    val isDuplicate = s == "DUPLICATE" || txn.eventType.equals("DUPLICATE", ignoreCase = true)
    val isPromotional = s == "PROMOTIONAL" || txn.eventType.equals("PROMOTIONAL", ignoreCase = true)
    val isBalanceOnly = s == "BALANCE_ONLY" || txn.eventType.equals("BALANCE_ONLY", ignoreCase = true)
    return !isFailed && !isPending && !isDuplicate && !isPromotional && !isBalanceOnly &&
            (s.isEmpty() || s == "COMPLETED" || s == "SUCCESS" || s == "PAID")
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WealthWiseRepository(
        WealthWiseDatabase.getDatabase(application),
        application
    )
    private val userPreferencesRepository = UserPreferencesRepository(application)
    private val loanDao = WealthWiseDatabase.getDatabase(application).loanDao()

    val defaultCategories = listOf(
        CategoryBudgetLimit("Food", 2000.0),
        CategoryBudgetLimit("Shopping", 8000.0),
        CategoryBudgetLimit("Transport", 5000.0),
        CategoryBudgetLimit("Personal Transfer", 5000.0),
        CategoryBudgetLimit("Bills", 12000.0),
        CategoryBudgetLimit("Health", 5000.0),
        CategoryBudgetLimit("Entertainment", 2500.0),
        CategoryBudgetLimit("Other", 5000.0)
    )

    private val _activeUserId = MutableStateFlow(repository.currentUserId())
    val activeUserId: StateFlow<String> = _activeUserId.asStateFlow()

    private val _homeState = MutableStateFlow(HomeFinancialState(isLoading = true))
    val homeState: StateFlow<HomeFinancialState> = _homeState.asStateFlow()

    val uiState: StateFlow<HomeUiState> = _homeState.map { state ->
        HomeUiState(
            availableBalance = state.availableBalance,
            safeToSpend = state.safeToSpend,
            actualBalance = state.actualBalance,
            isLoading = state.isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    val monthlyTransactions: StateFlow<List<TransactionEntity>> = _homeState.map { it.monthlyTransactions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyIncome: StateFlow<Double> = _homeState.map { it.monthlyIncome }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense: StateFlow<Double> = _homeState.map { it.monthlyExpense }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val actualBalance: StateFlow<Double> = _homeState.map { it.actualBalance }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val availableBalance: StateFlow<Double> = _homeState.map { it.availableBalance }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun updateActiveUser(newUid: String) {
        _activeUserId.value = newUid
    }

    init {
        try {
            FirebaseManager.auth?.addAuthStateListener { auth ->
                val uid = auth.currentUser?.uid.orEmpty()
                _activeUserId.value = uid
            }
        } catch (_: Throwable) {}

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            _activeUserId.flatMapLatest { userId ->
                val safeTxnFlow = repository.observeTransactions(userId).catch { emit(emptyList()) }
                val safeBudgetFlow = repository.budgetsForUser(userId).catch { emit(emptyList()) }
                val safeGoalsFlow = repository.getGoalsFlow(userId).catch { emit(emptyList()) }
                val safeGoalAllocFlow = repository.getTotalGoalAllocatedFlow(userId).catch { emit(0.0) }
                
                val safeInitBalFlow = userPreferencesRepository.getInitialBalanceFlow(userId)
                    .combine(userPreferencesRepository.userPreferencesFlow) { userBal, prefs ->
                        if (userBal > 0.0) userBal else (prefs.initialBalance ?: 0.0)
                    }
                    .catch { emit(0.0) }

                val safeCategoryFlow = repository.getCategoriesFlow(userId).catch { emit(emptyList()) }
                val safeLoanFlow = loanDao.getActiveLoansFlow(userId).catch { emit(emptyList()) }
                val safeDebtFlow = loanDao.getTotalRemainingDebtFlow(userId).catch { emit(0.0) }

                // Nested combine to safely bypass Kotlin's 5-flow limit
                val part1 = combine(safeTxnFlow, safeBudgetFlow, safeGoalsFlow) { t, b, g -> Triple(t, b, g) }
                val part2 = combine(safeGoalAllocFlow, safeInitBalFlow, safeCategoryFlow) { ga, ib, c -> Triple(ga, ib, c) }
                val part3 = combine(safeLoanFlow, safeDebtFlow) { l, d -> Pair(l, d) }

                combine(part1, part2, part3) { (txns, budgets, goals), (totalGoalsAlloc, startingBal, categories), (activeLoans, totalDebt) ->
                    HomeOverviewData(
                        transactions = txns,
                        budgets = budgets,
                        goals = goals,
                        totalGoalsAllocated = totalGoalsAlloc,
                        startingBalance = startingBal,
                        categories = categories,
                        activeLoans = activeLoans,
                        totalRemainingDebt = totalDebt
                    )
                }
            }.catch {
                emit(HomeOverviewData(emptyList(), emptyList(), emptyList(), 0.0, 0.0, emptyList(), emptyList(), 0.0))
            }.collect { data ->
                val monthTxns = filterCurrentMonthTransactions(data.transactions)
                val (mIncome, mExpense) = computeMonthlyTotals(monthTxns)
                val chartPoints = computeChartPoints(monthTxns)

                val effectiveCategories = when {
                    data.categories.isNotEmpty() -> data.categories.map { CategoryBudgetLimit(it.name, it.budgetLimit) }
                    data.budgets.isNotEmpty() -> data.budgets.map { CategoryBudgetLimit(it.category, it.monthlyLimit) }
                    else -> defaultCategories
                }

                val totalBudget = effectiveCategories.sumOf { it.limit }
                val spendings = effectiveCategories.map { cat ->
                    computeCategorySpending(cat.name, cat.limit, monthTxns)
                }

                val balanceRes = computeBalances(
                    transactions = data.transactions,
                    startingBalance = data.startingBalance,
                    goals = data.goals,
                    totalGoalsAllocated = data.totalGoalsAllocated,
                    budgets = data.budgets
                )

                val topActive = spendings
                    .filter { it.spent > 0.0 }
                    .sortedByDescending { it.spent }
                    .take(2)

                val suggestion: String? = if (topActive.isNotEmpty()) {
                    val top = topActive.first()
                    val spentInt = top.spent.toLong()
                    val limitInt = top.limit.toLong()
                    when {
                        top.isExceeded -> "💡 Tip: You've exceeded your ${top.name} budget by ₹${top.overrun.toLong()}. Consider reviewing spending."
                        top.limit > 0 && top.percentUsed >= 80 -> "💡 Tip: You've used ${top.percentUsed}% of your ${top.name} budget (₹$spentInt of ₹$limitInt). Slow down."
                        top.limit > 0 -> "💡 Tip: You've spent ₹$spentInt in ${top.name} (${top.percentUsed}% of ₹$limitInt limit)."
                        else -> "💡 Tip: You've spent ₹$spentInt in ${top.name}."
                    }
                } else null

                val totalSpent = mExpense
                val remainingBudget = (totalBudget - totalSpent).coerceAtLeast(0.0)
                val percentageUsed = if (totalBudget > 0.0) {
                    ((totalSpent / totalBudget) * 100).toInt().coerceIn(0, 100)
                } else 0

                _homeState.update {
                    it.copy(
                        totalIncome = balanceRes.totalIncome,
                        totalExpense = balanceRes.totalExpense,
                        startingBalance = balanceRes.startingBalance,
                        actualBalance = balanceRes.actualBalance,
                        availableBalance = balanceRes.availableBalance,
                        committedToGoals = balanceRes.committedToGoals,
                        safeToSpend = balanceRes.safeToSpend,
                        monthlyIncome = mIncome,
                        monthlyExpense = mExpense,
                        totalBudget = totalBudget,
                        remainingBudget = remainingBudget,
                        percentageUsed = percentageUsed,
                        transactions = data.transactions,
                        monthlyTransactions = monthTxns,
                        categorySpendings = spendings,
                        topActiveCategories = topActive,
                        smartSuggestion = suggestion,
                        chartPoints = chartPoints,
                        activeLoans = data.activeLoans,
                        activeLoanCount = data.activeLoans.size,
                        totalRemainingDebt = data.totalRemainingDebt,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun computeBalances(
        transactions: List<TransactionEntity>,
        startingBalance: Double,
        goals: List<GoalEntity> = emptyList(),
        totalGoalsAllocated: Double = 0.0,
        budgets: List<BudgetEntity> = emptyList()
    ): BalanceCalculationResult = Companion.computeBalances(
        transactions = transactions,
        startingBalance = startingBalance,
        goals = goals,
        totalGoalsAllocated = totalGoalsAllocated,
        budgets = budgets
    )

    companion object {
        fun computeBalances(
            transactions: List<TransactionEntity>,
            startingBalance: Double,
            goals: List<GoalEntity> = emptyList(),
            totalGoalsAllocated: Double = 0.0,
            budgets: List<BudgetEntity> = emptyList()
        ): BalanceCalculationResult {
            val totalBudget = budgets.sumOf { it.monthlyLimit }
            val monthTxns = filterCurrentMonthTransactions(transactions)
            return computeBalancesStatic(
                transactions = transactions,
                startingBalance = startingBalance,
                goals = goals,
                totalGoalsAllocated = totalGoalsAllocated,
                monthlyTransactions = monthTxns,
                effectiveBudget = totalBudget
            )
        }

        fun isCompletedTransaction(txn: TransactionEntity): Boolean {
            val s = txn.status.uppercase(Locale.ROOT)
            val isFailed = s == "FAILED" || s == "REJECTED" || txn.eventType.equals("FAILED", ignoreCase = true)
            val isPending = s == "PENDING" || txn.eventType.equals("PENDING", ignoreCase = true)
            val isDuplicate = s == "DUPLICATE" || txn.eventType.equals("DUPLICATE", ignoreCase = true)
            val isPromotional = s == "PROMOTIONAL" || txn.eventType.equals("PROMOTIONAL", ignoreCase = true)
            val isBalanceOnly = s == "BALANCE_ONLY" || txn.eventType.equals("BALANCE_ONLY", ignoreCase = true)
            return !isFailed && !isPending && !isDuplicate && !isPromotional && !isBalanceOnly &&
                    (s.isEmpty() || s == "COMPLETED" || s == "SUCCESS" || s == "PAID")
        }

        fun computeBalancesStatic(
            transactions: List<TransactionEntity>,
            startingBalance: Double,
            goals: List<GoalEntity> = emptyList(),
            totalGoalsAllocated: Double = 0.0,
            monthlyTransactions: List<TransactionEntity> = emptyList(),
            effectiveBudget: Double = 0.0
        ): BalanceCalculationResult {
            val completedTxns = transactions.filter { isCompletedTransaction(it) }

            val totalIncome = completedTxns
                .filter { it.type.equals("INCOME", ignoreCase = true) || (!it.isTransfer && (it.isInflow || it.eventType.equals("REFUND", ignoreCase = true) || it.eventType.equals("REVERSAL", ignoreCase = true))) }
                .sumOf { kotlin.math.abs(it.amount) }

            val totalExpense = completedTxns
                .filter {
                    !it.isTransfer && !it.isGoalTransfer && !it.isInflow &&
                    !it.eventType.equals("REFUND", ignoreCase = true) &&
                    !it.eventType.equals("REVERSAL", ignoreCase = true) &&
                    !it.category.equals("Savings Goal", ignoreCase = true) &&
                    !it.category.equals("Goal Allocation", ignoreCase = true) &&
                    !it.eventType.equals("GOAL_ALLOCATION", ignoreCase = true) &&
                    !it.eventType.equals("GOAL_TRANSFER", ignoreCase = true) &&
                    !it.type.equals("GOAL_TRANSFER", ignoreCase = true)
                }
                .sumOf { kotlin.math.abs(it.amount) }

            val actualBalance = startingBalance + totalIncome - totalExpense

            val committedToGoals = when {
                totalGoalsAllocated > 0.0 -> totalGoalsAllocated
                goals.isNotEmpty() -> goals.sumOf { it.savedAmount }
                else -> 0.0
            }

            val availableBalance = (actualBalance - committedToGoals).coerceAtLeast(0.0)

            val currentMonthExpense = monthlyTransactions
                .filter {
                    !it.isTransfer && !it.isGoalTransfer && !it.isInflow &&
                    !it.eventType.equals("REFUND", ignoreCase = true) &&
                    !it.eventType.equals("REVERSAL", ignoreCase = true) &&
                    !it.category.equals("Savings Goal", ignoreCase = true) &&
                    !it.category.equals("Goal Allocation", ignoreCase = true) &&
                    !it.eventType.equals("GOAL_ALLOCATION", ignoreCase = true) &&
                    !it.eventType.equals("GOAL_TRANSFER", ignoreCase = true) &&
                    !it.type.equals("GOAL_TRANSFER", ignoreCase = true)
                }
                .sumOf { kotlin.math.abs(it.amount) }

            val safeToSpend = if (effectiveBudget > 0.0) {
                val budgetRemaining = (effectiveBudget - currentMonthExpense).coerceAtLeast(0.0)
                minOf(availableBalance, budgetRemaining)
            } else {
                availableBalance
            }

            return BalanceCalculationResult(
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                startingBalance = startingBalance,
                actualBalance = actualBalance,
                availableBalance = availableBalance,
                committedToGoals = committedToGoals,
                safeToSpend = safeToSpend
            )
        }

        fun filterCurrentMonthTransactions(transactions: List<TransactionEntity>): List<TransactionEntity> {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val currentMonthStart = calendar.timeInMillis

            val endCalendar = (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val currentMonthEnd = endCalendar.timeInMillis

            return transactions.filter { txn ->
                isCompletedTransaction(txn) && txn.timestamp in currentMonthStart..currentMonthEnd
            }
        }

        fun computeMonthlyTotals(monthlyTransactions: List<TransactionEntity>): Pair<Double, Double> {
            val income = monthlyTransactions
                .filter { !it.isTransfer && (it.isInflow || it.eventType.equals("REFUND", ignoreCase = true) || it.eventType.equals("REVERSAL", ignoreCase = true)) }
                .sumOf { kotlin.math.abs(it.amount) }

            val expense = monthlyTransactions
                .filter { 
                    !it.isTransfer && !it.isInflow && 
                    !it.eventType.equals("REFUND", ignoreCase = true) && 
                    !it.eventType.equals("REVERSAL", ignoreCase = true) &&
                    !it.category.equals("Savings Goal", ignoreCase = true) &&
                    !it.category.equals("Goal Allocation", ignoreCase = true)
                }
                .sumOf { kotlin.math.abs(it.amount) }

            return Pair(income, expense)
        }

        fun computeChartPoints(monthlyTransactions: List<TransactionEntity>): List<IncomeExpensePoint> {
            val completedTxns = monthlyTransactions.filter { isCompletedTransaction(it) && !it.isTransfer }
            if (completedTxns.isEmpty()) return emptyList()

            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH)
            val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
            val monthName = monthFormat.format(cal.time)

            val dailyIncome = DoubleArray(maxDays + 1)
            val dailyExpense = DoubleArray(maxDays + 1)

            completedTxns.forEach { txn ->
                val txnCal = Calendar.getInstance().apply { timeInMillis = txn.timestamp }
                if (txnCal.get(Calendar.YEAR) == currentYear && txnCal.get(Calendar.MONTH) == currentMonth) {
                    val day = txnCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, maxDays)
                    val amt = kotlin.math.abs(txn.amount)
                    if (txn.isInflow || txn.eventType.equals("REFUND", ignoreCase = true) || txn.eventType.equals("REVERSAL", ignoreCase = true)) {
                        dailyIncome[day] += amt
                    } else if (txn.isOutflow) {
                        dailyExpense[day] += amt
                    }
                }
            }

            val hasAnyData = (1..maxDays).any { dailyIncome[it] > 0.0 || dailyExpense[it] > 0.0 }
            if (!hasAnyData) return emptyList()

            val points = mutableListOf<IncomeExpensePoint>()
            for (day in 1..maxDays) {
                points.add(
                    IncomeExpensePoint(
                        label = day.toString(),
                        income = dailyIncome[day],
                        expense = dailyExpense[day],
                        tooltipDate = "$monthName $day"
                    )
                )
            }
            return points
        }

        fun computeCategorySpending(
            categoryName: String,
            categoryLimit: Double,
            transactions: List<TransactionEntity>
        ): CategorySpending {
            val spent = transactions
                .filter {
                    isCompletedTransaction(it) &&
                            !it.isTransfer &&
                            (it.type.equals("EXPENSE", ignoreCase = true) || it.type.equals("debit", ignoreCase = true) || it.isOutflow) &&
                            it.category.trim().equals(categoryName.trim(), ignoreCase = true)
                }
                .sumOf { kotlin.math.abs(it.amount) }

            val remaining = (categoryLimit - spent).coerceAtLeast(0.0)
            val progress = if (categoryLimit > 0) (spent / categoryLimit).toFloat().coerceIn(0f, 1f) else 0f
            val percentUsed = if (categoryLimit > 0) ((spent / categoryLimit) * 100).toInt() else 0
            val isExceeded = categoryLimit > 0 && spent > categoryLimit
            val overrun = (spent - categoryLimit).coerceAtLeast(0.0)

            return CategorySpending(
                name = categoryName,
                limit = categoryLimit,
                spent = spent,
                remaining = remaining,
                progress = progress,
                percentUsed = percentUsed,
                isExceeded = isExceeded,
                overrun = overrun
            )
        }

        fun computeMonthlyBudgetStatus(
            categories: List<CategoryBudgetLimit>,
            totalExpenseThisMonth: Double
        ): BudgetStatusResult {
            val totalBudget = categories.sumOf { it.limit }
            val remainingBudget = (totalBudget - totalExpenseThisMonth).coerceAtLeast(0.0)
            val percentageUsed = if (totalBudget > 0) ((totalExpenseThisMonth / totalBudget) * 100).toInt() else 0

            return BudgetStatusResult(
                totalBudget = totalBudget,
                totalExpense = totalExpenseThisMonth,
                remainingBudget = remainingBudget,
                percentageUsed = percentageUsed
            )
        }
    }
}