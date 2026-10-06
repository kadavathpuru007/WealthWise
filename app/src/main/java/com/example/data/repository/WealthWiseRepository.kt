package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.parser.ParsedTransactionResult
import com.example.data.parser.ParserConfidence
import com.example.data.parser.RejectionReason
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.FinancialEventType
import com.example.domain.financial.FinancialStatus
import com.example.domain.financial.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class IngestionResult {
    data class Success(val transactionId: Long, val transaction: TransactionEntity) : IngestionResult()
    data class Duplicate(val reason: String, val existingTransactionId: Int) : IngestionResult()
    data class Rejected(val reason: RejectionReason, val message: String) : IngestionResult()
    data class Error(val throwable: Throwable) : IngestionResult()
}

typealias TransactionRepository = WealthWiseRepository

class WealthWiseRepository(
    private val database: WealthWiseDatabase,
    private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wealthwise_prefs", Context.MODE_PRIVATE)

    private val insertMutex = Mutex()

    private val transactionDao = database.transactionDao()
    private val budgetDao = database.budgetDao()
    private val categoryDao = database.categoryDao()
    val categoryRepository = CategoryRepository(categoryDao)
    private val goalDao = database.goalDao()
    private val billDao = database.billDao()
    private val subscriptionDao = database.subscriptionDao()
    private val notificationDao = database.notificationDao()
    private val loanDao = database.loanDao()
    val tradeDao = database.tradeDao()

    fun currentUserId(): String {
        return try {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank()) uid else "local_user"
        } catch (_: Throwable) {
            "local_user"
        }
    }

    private val userPreferencesRepository = UserPreferencesRepository(context)

    // =========================================================================
    // TRADES & INVESTMENTS
    // =========================================================================
    fun getTradesFlow(userId: String = currentUserId()): Flow<List<TradeEntity>> =
        if (userId.isNotBlank()) tradeDao.getAllTradesFlow(userId) else flowOf(emptyList())

    fun getTotalRealizedPnlFlow(userId: String = currentUserId()): Flow<Double> =
        if (userId.isNotBlank()) tradeDao.getTotalRealizedPnlFlow(userId) else flowOf(0.0)

    fun getClosedTradeCountFlow(userId: String = currentUserId()): Flow<Int> =
        if (userId.isNotBlank()) tradeDao.getClosedTradeCountFlow(userId) else flowOf(0)

    suspend fun insertTrade(trade: TradeEntity): Long = tradeDao.insertTrade(trade)

    suspend fun deleteTrade(trade: TradeEntity) = tradeDao.deleteTrade(trade)

    suspend fun deleteTradeById(id: Long, userId: String = currentUserId()) = tradeDao.deleteTradeById(id, userId)

    // =========================================================================
    // TRANSACTIONS
    // =========================================================================
    fun getTransactionsFlow(userId: String = currentUserId()): Flow<List<TransactionEntity>> =
        if (userId.isNotBlank()) transactionDao.getTransactionsFlow(userId) else flowOf(emptyList())

    fun observeTransactions(userId: String = currentUserId()): Flow<List<TransactionEntity>> =
        getTransactionsFlow(userId)

    fun observeTransactionsSince(userId: String = currentUserId(), startTime: Long): Flow<List<TransactionEntity>> =
        if (userId.isNotBlank()) transactionDao.getTransactionsSinceFlow(userId, startTime) else flowOf(emptyList())

    fun transactionsForUser(userId: String = currentUserId()): Flow<List<TransactionEntity>> =
        getTransactionsFlow(userId)

    val allTransactions: Flow<List<TransactionEntity>>
        get() = transactionsForUser(currentUserId())

    fun recentTransactionsForUser(userId: String = currentUserId(), limit: Int = 8): Flow<List<TransactionEntity>> =
        if (userId.isNotBlank()) transactionDao.getRecentTransactions(userId, limit) else flowOf(emptyList())

    val recentTransactions: Flow<List<TransactionEntity>>
        get() = recentTransactionsForUser(currentUserId())

    // =========================================================================
    // BUDGETS & CATEGORIES
    // =========================================================================
    fun budgetsForUser(userId: String = currentUserId()): Flow<List<BudgetEntity>> =
        if (userId.isNotBlank()) budgetDao.getAllBudgets(userId) else flowOf(emptyList())

    val allBudgets: Flow<List<BudgetEntity>>
        get() = budgetsForUser(currentUserId())

    fun getCategoriesFlow(userId: String = currentUserId()): Flow<List<CategoryEntity>> =
        if (userId.isNotBlank()) categoryDao.getCategoriesFlow(userId) else flowOf(emptyList())

    val allCategories: Flow<List<CategoryEntity>>
        get() = getCategoriesFlow(currentUserId())

    // =========================================================================
    // GOALS, BILLS, SUBSCRIPTIONS, NOTIFICATIONS, LOANS
    // =========================================================================
    fun getGoalsFlow(userId: String = currentUserId()): Flow<List<GoalEntity>> =
        if (userId.isNotBlank()) goalDao.getGoalsFlow(userId) else flowOf(emptyList())

    fun getTotalGoalAllocatedFlow(userId: String = currentUserId()): Flow<Double> =
        if (userId.isNotBlank()) goalDao.getTotalGoalAllocatedFlow(userId) else flowOf(0.0)

    fun goalsForUser(userId: String = currentUserId()): Flow<List<GoalEntity>> =
        getGoalsFlow(userId)

    val allGoals: Flow<List<GoalEntity>>
        get() = goalsForUser(currentUserId())

    fun billsForUser(userId: String = currentUserId()): Flow<List<BillEntity>> =
        if (userId.isNotBlank()) billDao.getAllBills(userId) else flowOf(emptyList())

    val allBills: Flow<List<BillEntity>>
        get() = billsForUser(currentUserId())

    fun subscriptionsForUser(userId: String = currentUserId()): Flow<List<SubscriptionEntity>> =
        if (userId.isNotBlank()) subscriptionDao.getSubscriptionsFlow(userId) else flowOf(emptyList())

    val allSubscriptions: Flow<List<SubscriptionEntity>>
        get() = subscriptionsForUser(currentUserId())

    fun notificationsForUser(userId: String = currentUserId()): Flow<List<NotificationEntity>> =
        if (userId.isNotBlank()) notificationDao.getAllNotifications(userId) else flowOf(emptyList())

    val allNotifications: Flow<List<NotificationEntity>>
        get() = notificationsForUser(currentUserId())

    fun loansForUser(userId: String = currentUserId()): Flow<List<LoanEntity>> =
        if (userId.isNotBlank()) loanDao.getAllLoans(userId) else flowOf(emptyList())

    val allLoans: Flow<List<LoanEntity>>
        get() = loansForUser(currentUserId())

    // Auth & Setup states scoped strictly by UID
    fun hasCompletedInitialBalanceSetupForUser(uid: String = currentUserId()): Boolean =
        userPreferencesRepository.hasCompletedInitialBalanceSetupForUser(uid)

    fun setCompletedInitialBalanceSetupForUser(completed: Boolean, uid: String = currentUserId()) =
        userPreferencesRepository.setCompletedInitialBalanceSetupForUser(completed, uid)

    var has_completed_initial_balance_setup: Boolean
        get() = userPreferencesRepository.has_completed_initial_balance_setup
        set(value) {
            userPreferencesRepository.has_completed_initial_balance_setup = value
        }

    var hasCompletedInitialBalanceSetup: Boolean
        get() = userPreferencesRepository.hasCompletedInitialBalanceSetup
        set(value) {
            userPreferencesRepository.hasCompletedInitialBalanceSetup = value
        }

    var hasCompletedSetup: Boolean
        get() = userPreferencesRepository.hasCompletedSetup
        set(value) {
            userPreferencesRepository.hasCompletedSetup = value
        }

    var isLoggedIn: Boolean
        get() = userPreferencesRepository.isLoggedIn
        set(value) {
            userPreferencesRepository.isLoggedIn = value
        }

    var userName: String
        get() = userPreferencesRepository.userName
        set(value) {
            userPreferencesRepository.userName = value
        }

    var userEmail: String
        get() = userPreferencesRepository.userEmail
        set(value) {
            userPreferencesRepository.userEmail = value
        }

    var startingBankBalance: Double
        get() = userPreferencesRepository.startingBankBalance
        set(value) {
            userPreferencesRepository.startingBankBalance = value
        }

    fun getInitialBalance(userId: String = currentUserId()): Double? =
        userPreferencesRepository.getInitialBalance(userId)

    fun getInitialBalanceFlow(userId: String = currentUserId()): Flow<Double> =
        userPreferencesRepository.getInitialBalanceFlow(userId)

    fun setInitialBalance(balance: Double, userId: String = currentUserId()) =
        userPreferencesRepository.setInitialBalance(balance, userId)

    var isMobilePhoneView: Boolean
        get() = prefs.getBoolean("isMobilePhoneView", false)
        set(value) = prefs.edit().putBoolean("isMobilePhoneView", value).apply()

    // Notification Preferences
    var pushNotificationsEnabled: Boolean
        get() = prefs.getBoolean("pushNotificationsEnabled", true)
        set(value) = prefs.edit().putBoolean("pushNotificationsEnabled", value).apply()

    var budgetAlertsEnabled: Boolean
        get() = prefs.getBoolean("budgetAlertsEnabled", true)
        set(value) = prefs.edit().putBoolean("budgetAlertsEnabled", value).apply()

    var billEmiAlertsEnabled: Boolean
        get() = prefs.getBoolean("billEmiAlertsEnabled", true)
        set(value) = prefs.edit().putBoolean("billEmiAlertsEnabled", value).apply()

    var goalAlertsEnabled: Boolean
        get() = prefs.getBoolean("goalAlertsEnabled", true)
        set(value) = prefs.edit().putBoolean("goalAlertsEnabled", value).apply()

    var syncAlertsEnabled: Boolean
        get() = prefs.getBoolean("syncAlertsEnabled", true)
        set(value) = prefs.edit().putBoolean("syncAlertsEnabled", value).apply()

    var billReminderDaysAhead: Int
        get() = prefs.getInt("billReminderDaysAhead", 1)
        set(value) = prefs.edit().putInt("billReminderDaysAhead", value).apply()

    var budgetThresholdPercent: Double
        get() = prefs.getFloat("budgetThresholdPercent", 0.90f).toDouble()
        set(value) = prefs.edit().putFloat("budgetThresholdPercent", value.toFloat()).apply()

    var smsParsingEnabled: Boolean
        get() = prefs.getBoolean("smsParsingEnabled", true)
        set(value) = prefs.edit().putBoolean("smsParsingEnabled", value).apply()

    var accountAggregatorSyncEnabled: Boolean
        get() = prefs.getBoolean("accountAggregatorSyncEnabled", true)
        set(value) = prefs.edit().putBoolean("accountAggregatorSyncEnabled", value).apply()

    // Subscription Management Preferences
    var subscriptionUsageInsightsEnabled: Boolean
        get() = prefs.getBoolean("subscriptionUsageInsightsEnabled", false)
        set(value) = prefs.edit().putBoolean("subscriptionUsageInsightsEnabled", value).apply()

    var upcomingSubscriptionAlertsEnabled: Boolean
        get() = prefs.getBoolean("upcomingSubscriptionAlertsEnabled", true)
        set(value) = prefs.edit().putBoolean("upcomingSubscriptionAlertsEnabled", value).apply()

    var unusedSubscriptionAlertsEnabled: Boolean
        get() = prefs.getBoolean("unusedSubscriptionAlertsEnabled", true)
        set(value) = prefs.edit().putBoolean("unusedSubscriptionAlertsEnabled", value).apply()

    var priceChangeAlertsEnabled: Boolean
        get() = prefs.getBoolean("priceChangeAlertsEnabled", true)
        set(value) = prefs.edit().putBoolean("priceChangeAlertsEnabled", value).apply()

    var subscriptionObservationPeriodDays: Int
        get() = prefs.getInt("subscriptionObservationPeriodDays", 30)
        set(value) = prefs.edit().putInt("subscriptionObservationPeriodDays", value).apply()

    fun getRejectedSubscriptionMerchants(): Set<String> {
        return prefs.getStringSet("rejectedSubscriptionMerchants", emptySet()) ?: emptySet()
    }

    fun addRejectedSubscriptionMerchant(merchant: String) {
        val current = getRejectedSubscriptionMerchants().toMutableSet()
        current.add(merchant.trim())
        prefs.edit().putStringSet("rejectedSubscriptionMerchants", current).apply()
    }

    fun clearRejectedSubscriptionMerchants() {
        prefs.edit().remove("rejectedSubscriptionMerchants").apply()
    }

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        if (transaction.amount <= 0.0) {
            throw IllegalArgumentException("Transaction amount must be strictly positive (> 0.0). Received: ${transaction.amount}")
        }
        val uid = if (transaction.userId.isNotBlank()) transaction.userId else currentUserId()
        val effectiveFp = if (transaction.fingerprint.isNotBlank()) {
            transaction.fingerprint
        } else {
            com.example.data.parser.TransactionParser.generateFingerprint(
                amount = transaction.amount,
                type = transaction.type,
                timestamp = if (transaction.timestamp > 0) transaction.timestamp else System.currentTimeMillis(),
                merchant = transaction.merchant.ifBlank { transaction.title },
                referenceId = transaction.referenceId
            )
        }

        // Prevent double counting of parsed or referenced transactions
        if (transaction.isParsed || transaction.fingerprint.isNotBlank()) {
            val existingFp = transactionDao.findByFingerprint(effectiveFp, uid)
                ?: transactionDao.findByFingerprint(effectiveFp)
            if (existingFp != null && (existingFp.userId.isEmpty() || existingFp.userId == uid)) {
                return existingFp.id.toLong()
            }
        }
        if (transaction.referenceId.isNotBlank()) {
            val existingRef = transactionDao.findByReferenceId(transaction.referenceId, uid)
                ?: transactionDao.findByReferenceId(transaction.referenceId)
            if (existingRef != null && (existingRef.userId.isEmpty() || existingRef.userId == uid)) {
                return existingRef.id.toLong()
            }
        }

        return transactionDao.insertTransaction(transaction.copy(userId = uid, fingerprint = effectiveFp))
    }

    suspend fun deleteTransaction(id: Int) {
        transactionDao.deleteById(id)
    }

    suspend fun updateBudget(category: String, newLimit: Double) {
        if (newLimit <= 0.0) {
            throw IllegalArgumentException("Budget limit must be strictly positive. Received: $newLimit")
        }
        val uid = currentUserId()
        val existingCat = categoryDao.findByName(uid, category)
        if (existingCat != null) {
            categoryDao.updateCategory(existingCat.copy(budgetLimit = newLimit))
        } else {
            categoryDao.insertCategory(CategoryEntity(userId = uid, name = category.trim(), budgetLimit = newLimit))
        }
        budgetDao.insertOrUpdate(BudgetEntity(userId = uid, category = category.trim(), monthlyLimit = newLimit))
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        if (category.budgetLimit < 0.0) {
            throw IllegalArgumentException("Budget limit cannot be negative. Received: ${category.budgetLimit}")
        }
        val uid = if (category.userId.isNotBlank()) category.userId else currentUserId()
        val id = categoryDao.insertCategory(category.copy(userId = uid, name = category.name.trim()))
        budgetDao.insertOrUpdate(BudgetEntity(userId = uid, category = category.name.trim(), monthlyLimit = category.budgetLimit, colorHex = category.colorHex))
        return id
    }

    suspend fun updateCategory(category: CategoryEntity) {
        if (category.budgetLimit < 0.0) {
            throw IllegalArgumentException("Budget limit cannot be negative. Received: ${category.budgetLimit}")
        }
        val uid = if (category.userId.isNotBlank()) category.userId else currentUserId()
        categoryDao.updateCategory(category.copy(userId = uid, name = category.name.trim()))
        budgetDao.insertOrUpdate(BudgetEntity(userId = uid, category = category.name.trim(), monthlyLimit = category.budgetLimit, colorHex = category.colorHex))
    }

    suspend fun getCategories(userId: String = currentUserId()): List<CategoryEntity> =
        categoryRepository.getCategories(userId)

    suspend fun deleteCategory(id: Long) {
        val cat = categoryDao.getCategoryById(id)
        if (cat != null) {
            categoryDao.deleteById(id)
            budgetDao.deleteByCategory(cat.name, cat.userId)
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) = deleteCategory(category.id)

    suspend fun fundGoal(id: Long, amount: Double, userId: String? = null) {
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            throw IllegalArgumentException("Goal contribution must be strictly positive. Received: $amount")
        }
        val uid = if (!userId.isNullOrBlank()) userId else currentUserId()

        insertMutex.withLock {
            database.withTransaction {
                val goal = goalDao.getGoalById(id)
                    ?: throw NoSuchElementException("Goal with ID $id not found")

                if (goal.targetAmount <= 0.0) {
                    throw IllegalStateException("Goal target amount must be strictly positive.")
                }
                if (goal.savedAmount >= goal.targetAmount) {
                    throw IllegalStateException("Goal '${goal.title}' has already reached its target.")
                }

                val effectiveUid = if (uid.isNotBlank()) uid else goal.userId

                if (goal.userId.isNotBlank() && uid.isNotBlank() && goal.userId != uid && goal.userId != "local_user") {
                    throw SecurityException("Unauthorized: Goal does not belong to user $uid")
                }

                val initialBalance = userPreferencesRepository.getInitialBalance(effectiveUid) ?: startingBankBalance
                val existingTxns = getTransactionsFlow(effectiveUid).firstOrNull() ?: emptyList()
                val totalIncome = existingTxns
                    .filter { it.type.equals("INCOME", ignoreCase = true) || it.isInflow }
                    .sumOf { kotlin.math.abs(it.amount) }
                val totalOrdinaryExpenses = existingTxns
                    .filter {
                        !it.isTransfer && !it.isInflow &&
                        (it.type.equals("EXPENSE", ignoreCase = true) || it.type.equals("debit", ignoreCase = true)) &&
                        !it.category.equals("Savings Goal", ignoreCase = true) &&
                        !it.category.equals("Goal Allocation", ignoreCase = true)
                    }
                    .sumOf { kotlin.math.abs(it.amount) }
                val actualBal = initialBalance + totalIncome - totalOrdinaryExpenses
                val currentAllocated = goalDao.getTotalGoalAllocated(effectiveUid) ?: 0.0
                val availableBal = (actualBal - currentAllocated).coerceAtLeast(0.0)

                if (amount > availableBal) {
                    throw IllegalStateException("Insufficient available balance to fund goal '${goal.title}'. Available: ₹${MoneyUtils.formatInr(MoneyUtils.of(availableBal))}, Requested: ₹$amount")
                }

                val now = System.currentTimeMillis()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))

                // 1. Insert GoalAllocationEntity
                val allocation = GoalAllocationEntity(
                    id = 0L,
                    goalId = goal.id,
                    userId = effectiveUid,
                    amount = amount,
                    timestamp = now,
                    note = "Funded Goal: ${goal.title}"
                )
                goalDao.insertGoalAllocation(allocation)

                // 2. Update goal savedAmount
                goalDao.updateGoalSavedAmount(goal.id, amount)

                // 3. Insert transaction
                val txn = TransactionEntity(
                    userId = effectiveUid,
                    title = "Funded Goal: ${goal.title}",
                    amount = amount,
                    type = "GOAL_TRANSFER",
                    category = "Savings Goal",
                    paymentMethod = "Bank Transfer",
                    date = todayStr,
                    timestamp = now,
                    isParsed = false,
                    merchant = goal.title,
                    note = "Transferred into goal savings",
                    eventType = FinancialEventType.GOAL_TRANSFER.name,
                    status = FinancialStatus.COMPLETED.name
                )
                transactionDao.insertTransaction(txn)
            }
        }
    }

    suspend fun fundGoal(id: Int, amount: Double) = fundGoal(id.toLong(), amount)

    suspend fun ingestParsedTransaction(parsed: ParsedTransactionResult, userId: String? = null): IngestionResult = withContext(Dispatchers.IO) {
        if (parsed.confidence == ParserConfidence.REJECTED) {
            return@withContext IngestionResult.Rejected(parsed.rejectionReason, "Rejected: ${parsed.rejectionReason}")
        }
        if (parsed.amount <= 0.0 || parsed.amount.isNaN() || parsed.amount.isInfinite()) {
            return@withContext IngestionResult.Rejected(RejectionReason.INVALID_AMOUNT, "Invalid amount: ${parsed.amount}")
        }

        val uid = if (!userId.isNullOrBlank()) userId else currentUserId()

        insertMutex.withLock {
            try {
                // 1. Check referenceId duplicate
                if (parsed.referenceId.isNotBlank()) {
                    val existingRef = transactionDao.findByReferenceId(parsed.referenceId, uid)
                        ?: transactionDao.findByReferenceId(parsed.referenceId)
                    if (existingRef != null && (existingRef.userId.isEmpty() || existingRef.userId == uid)) {
                        return@withLock IngestionResult.Duplicate("Duplicate referenceId: ${parsed.referenceId}", existingRef.id)
                    }
                }

                // 2. Check fingerprint duplicate (5-minute window bucket + normalized merchant)
                val effectiveFingerprint = parsed.fingerprint.ifBlank {
                    com.example.data.parser.TransactionParser.generateFingerprint(
                        amount = parsed.amount,
                        type = parsed.type,
                        timestamp = parsed.timestamp,
                        merchant = parsed.merchant ?: parsed.title,
                        referenceId = parsed.referenceId
                    )
                }

                if (effectiveFingerprint.isNotBlank()) {
                    val existingFp = transactionDao.findByFingerprint(effectiveFingerprint, uid)
                        ?: transactionDao.findByFingerprint(effectiveFingerprint)
                    if (existingFp != null && (existingFp.userId.isEmpty() || existingFp.userId == uid)) {
                        return@withLock IngestionResult.Duplicate("Duplicate fingerprint", existingFp.id)
                    }
                }

                // Check adjacent 5-minute bucket in case event fell right across a bucket boundary
                val prevBucketFp = com.example.data.parser.TransactionParser.generateFingerprint(
                    amount = parsed.amount,
                    type = parsed.type,
                    timestamp = parsed.timestamp - 300_000L,
                    merchant = parsed.merchant ?: parsed.title,
                    referenceId = parsed.referenceId
                )
                val existingPrevFp = transactionDao.findByFingerprint(prevBucketFp, uid)
                    ?: transactionDao.findByFingerprint(prevBucketFp)
                if (existingPrevFp != null && (existingPrevFp.userId.isEmpty() || existingPrevFp.userId == uid)) {
                    return@withLock IngestionResult.Duplicate("Duplicate fingerprint (adjacent window)", existingPrevFp.id)
                }

                val nextBucketFp = com.example.data.parser.TransactionParser.generateFingerprint(
                    amount = parsed.amount,
                    type = parsed.type,
                    timestamp = parsed.timestamp + 300_000L,
                    merchant = parsed.merchant ?: parsed.title,
                    referenceId = parsed.referenceId
                )
                val existingNextFp = transactionDao.findByFingerprint(nextBucketFp, uid)
                    ?: transactionDao.findByFingerprint(nextBucketFp)
                if (existingNextFp != null && (existingNextFp.userId.isEmpty() || existingNextFp.userId == uid)) {
                    return@withLock IngestionResult.Duplicate("Duplicate fingerprint (adjacent window)", existingNextFp.id)
                }

                // 3. Check time-window duplicate (5 minutes / 300,000 ms tolerance)
                val windowStart = parsed.timestamp - 300_000L
                val recentTxns = transactionDao.getRecentTransactionsSince(uid, windowStart)
                val isDuplicateRecent = recentTxns.any { existing ->
                    val sameAmount = kotlin.math.abs(existing.amount - parsed.amount) < 0.001
                    val sameType = existing.type.equals(parsed.type, ignoreCase = true)
                    val withinWindow = kotlin.math.abs(existing.timestamp - parsed.timestamp) <= 300_000L
                    val normCurrent = com.example.data.parser.TransactionParser.normalizeMerchantForFingerprint(parsed.merchant ?: parsed.title)
                    val normTxMerchant = com.example.data.parser.TransactionParser.normalizeMerchantForFingerprint(existing.merchant)
                    val normTxTitle = com.example.data.parser.TransactionParser.normalizeMerchantForFingerprint(existing.title)
                    val sameMerchant = existing.merchant.isBlank() || parsed.merchant.isNullOrBlank() ||
                        existing.merchant.trim().equals(parsed.merchant?.trim(), ignoreCase = true) ||
                        existing.title.trim().equals(parsed.title.trim(), ignoreCase = true) ||
                        normTxMerchant == normCurrent ||
                        normTxTitle == normCurrent

                    sameAmount && sameType && withinWindow && sameMerchant
                }
                if (isDuplicateRecent) {
                    val match = recentTxns.first { existing ->
                        kotlin.math.abs(existing.amount - parsed.amount) < 0.001 &&
                        existing.type.equals(parsed.type, ignoreCase = true)
                    }
                    return@withLock IngestionResult.Duplicate("Duplicate payment event within 5-min window", match.id)
                }

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(parsed.timestamp))
                val entity = TransactionEntity(
                    userId = uid,
                    title = if (!parsed.merchant.isNullOrBlank()) parsed.merchant else parsed.category,
                    amount = parsed.amount,
                    type = parsed.type,
                    category = parsed.category,
                    paymentMethod = parsed.source,
                    date = todayStr,
                    timestamp = parsed.timestamp,
                    isParsed = true,
                    merchant = parsed.merchant.orEmpty(),
                    referenceId = parsed.referenceId,
                    status = parsed.status.name,
                    fingerprint = effectiveFingerprint
                )

                val newId = database.withTransaction {
                    val txnId = transactionDao.insertTransaction(entity)

                    // Automated Loan EMI matching pipeline
                    if (parsed.isOutflow && (parsed.category.equals("Loans & Debt", ignoreCase = true) ||
                        parsed.transactionType == FinancialEventType.LOAN_PAYMENT ||
                        parsed.title.contains("emi", ignoreCase = true) ||
                        entity.title.contains("emi", ignoreCase = true))) {
                        try {
                            val activeLoans = loanDao.getActiveLoansFlow(uid).firstOrNull() ?: emptyList()
                            val matchResult = com.example.domain.loan.LoanMatchingEngine.matchEmiToLoan(
                                detectedAmount = parsed.amount,
                                detectedMerchantOrLender = parsed.merchant.orEmpty(),
                                detectedTitleOrText = entity.title,
                                activeLoans = activeLoans
                            )
                            if (matchResult is com.example.domain.loan.LoanMatchResult.ExactMatch) {
                                val refToMatch = parsed.referenceId.ifBlank { "TXN-$txnId" }
                                val alreadyLinked = loanDao.findRepaymentByTransactionRef(refToMatch, uid)
                                if (alreadyLinked == null) {
                                    loanDao.insertRepayment(
                                        LoanRepaymentEntity(
                                            loanId = matchResult.loan.id,
                                            userId = uid,
                                            paidAmount = parsed.amount,
                                            paymentDate = parsed.timestamp,
                                            transactionRef = refToMatch,
                                            isAutoDetected = true
                                        )
                                    )
                                }
                            }
                        } catch (_: Throwable) {}
                    }

                    txnId
                }
                IngestionResult.Success(newId, entity.copy(id = newId.toInt()))
            } catch (e: Throwable) {
                IngestionResult.Error(e)
            }
        }
    }

    suspend fun addGoal(goal: GoalEntity) {
        if (goal.targetAmount <= 0.0) {
            throw IllegalArgumentException("Goal target amount must be strictly positive. Received: ${goal.targetAmount}")
        }
        val uid = if (goal.userId.isNotBlank()) goal.userId else currentUserId()
        goalDao.insertGoal(goal.copy(userId = uid))
    }

    suspend fun payBill(id: Int) {
        database.withTransaction {
            val bill = billDao.getBillById(id)
                ?: throw NoSuchElementException("Bill with ID $id not found")
            if (bill.isPaid) {
                throw IllegalStateException("Bill '${bill.title}' has already been paid.")
            }
            if (bill.amount <= 0.0) {
                throw IllegalArgumentException("Bill amount must be strictly positive. Received: ${bill.amount}")
            }
            val uid = currentUserId()
            val existingTxns = getTransactionsFlow(uid).firstOrNull() ?: emptyList()
            val overview = FinancialCalculationEngine.calculateOverview(
                startingBalance = MoneyUtils.of(startingBankBalance),
                transactions = existingTxns
            )
            if (MoneyUtils.of(bill.amount) > overview.currentBalance) {
                throw IllegalStateException("Insufficient balance to pay bill '${bill.title}'. Available: ₹${MoneyUtils.formatInr(overview.currentBalance)}, Bill Amount: ₹${bill.amount}")
            }

            billDao.markAsPaid(id)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            transactionDao.insertTransaction(
                TransactionEntity(
                    userId = uid,
                    title = bill.title,
                    amount = bill.amount,
                    type = "EXPENSE",
                    category = bill.category,
                    paymentMethod = "UPI",
                    date = todayStr,
                    timestamp = System.currentTimeMillis(),
                    isParsed = false,
                    merchant = bill.title,
                    note = "Bill payment for ${bill.category}",
                    eventType = FinancialEventType.EXPENSE.name,
                    status = FinancialStatus.COMPLETED.name
                )
            )
        }
    }

    suspend fun cancelSubscription(id: Int) {
        subscriptionDao.updateStatus(id, "CANCELLED")
    }

    suspend fun reactivateSubscription(id: Int) {
        subscriptionDao.updateStatus(id, "ACTIVE")
    }

    suspend fun updateSubscriptionStatus(id: Int, status: String) {
        subscriptionDao.updateStatus(id, status)
    }

    suspend fun updateSubscription(subscription: SubscriptionEntity) {
        subscriptionDao.updateSubscription(subscription)
    }

    suspend fun updateSubscriptionUsage(id: Int, daysUnused: Int, isUnusedFlagged: Boolean, usageDays: Int) {
        subscriptionDao.updateUsage(id, daysUnused, isUnusedFlagged, usageDays)
    }

    suspend fun recordSubscriptionPayment(
        subscriptionId: Int,
        amount: Double,
        paymentMethod: String = "UPI",
        paymentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ) {
        if (amount <= 0.0) {
            throw IllegalArgumentException("Subscription payment amount must be strictly positive. Received: $amount")
        }
        database.withTransaction {
            val sub = subscriptionDao.getSubscriptionById(subscriptionId)
                ?: throw NoSuchElementException("Subscription with ID $subscriptionId not found")

            val uid = currentUserId()
            val existingTxns = getTransactionsFlow(uid).firstOrNull() ?: emptyList()
            val overview = FinancialCalculationEngine.calculateOverview(
                startingBalance = MoneyUtils.of(startingBankBalance),
                transactions = existingTxns
            )
            if (MoneyUtils.of(amount) > overview.currentBalance) {
                throw IllegalStateException("Insufficient balance for subscription payment '${sub.name}'. Available: ₹${MoneyUtils.formatInr(overview.currentBalance)}, Payment: ₹$amount")
            }

            val subName = sub.name.ifBlank { "Subscription" }
            val merchant = sub.merchantName.ifBlank { subName }
            val category = sub.category.ifBlank { "Subscription" }
            val billingPeriod = sub.billingPeriod.ifBlank { "MONTHLY" }

            val nextDate = com.example.domain.subscription.SubscriptionCalculations.estimateNextPaymentDate(
                paymentDate,
                billingPeriod
            )

            subscriptionDao.recordPayment(
                id = subscriptionId,
                date = paymentDate,
                amount = amount,
                nextDate = nextDate
            )

            transactionDao.insertTransaction(
                TransactionEntity(
                    userId = uid,
                    title = subName,
                    amount = amount,
                    type = "EXPENSE",
                    category = category,
                    paymentMethod = paymentMethod,
                    date = paymentDate,
                    timestamp = System.currentTimeMillis(),
                    isParsed = false,
                    merchant = merchant,
                    note = "Recurring payment for $subName ($billingPeriod)",
                    eventType = FinancialEventType.SUBSCRIPTION_PAYMENT.name,
                    status = FinancialStatus.COMPLETED.name
                )
            )
        }
    }

    suspend fun deleteSubscription(id: Int) {
        subscriptionDao.deleteSubscription(id)
    }

    suspend fun insertSubscription(subscription: SubscriptionEntity) {
        if (subscription.amount < 0.0) {
            throw IllegalArgumentException("Subscription amount cannot be negative.")
        }
        val uid = if (subscription.userId.isNotBlank()) subscription.userId else currentUserId()
        subscriptionDao.insertSubscription(subscription.copy(userId = uid))
    }

    suspend fun addNotification(notification: NotificationEntity) {
        val uid = if (notification.userId.isNotBlank()) notification.userId else currentUserId()
        notificationDao.insertNotification(notification.copy(userId = uid))
    }

    suspend fun markNotificationAsRead(id: Int) {
        notificationDao.markAsRead(id)
    }

    suspend fun markAllNotificationsAsRead() {
        notificationDao.markAllAsRead()
    }

    suspend fun clearAllNotifications() {
        notificationDao.deleteAll()
    }

    suspend fun deleteNotification(id: Int) {
        notificationDao.deleteNotification(id)
    }

    suspend fun insertLoan(loan: LoanEntity): Long {
        if (loan.principalAmount <= 0.0) {
            throw IllegalArgumentException("Loan principal amount must be strictly positive.")
        }
        val uid = if (loan.userId.isNotBlank()) loan.userId else currentUserId()
        return loanDao.insertLoan(loan.copy(userId = uid))
    }

    suspend fun recordEmiPayment(id: Int, amountPaid: Double) = recordEmiPayment(id.toLong(), amountPaid)

    suspend fun recordEmiPayment(id: Long, amountPaid: Double) {
        if (amountPaid <= 0.0) {
            throw IllegalArgumentException("EMI payment amount must be strictly positive. Received: $amountPaid")
        }
        database.withTransaction {
            val uid = currentUserId()
            val loan = loanDao.getLoanById(id, uid) ?: loanDao.getLoanById(id.toInt())
                ?: throw NoSuchElementException("Loan with ID $id not found")

            val totalRepaid = loanDao.getTotalPaidForLoanFlow(loan.id, uid).firstOrNull() ?: 0.0
            val remainingPrincipal = (loan.principalAmount - totalRepaid).coerceAtLeast(0.0)
            if (amountPaid > remainingPrincipal && remainingPrincipal > 0.0) {
                throw IllegalArgumentException("Payment amount ₹$amountPaid exceeds remaining principal ₹$remainingPrincipal")
            }

            val existingTxns = getTransactionsFlow(uid).firstOrNull() ?: emptyList()
            val overview = FinancialCalculationEngine.calculateOverview(
                startingBalance = MoneyUtils.of(startingBankBalance),
                transactions = existingTxns
            )
            if (MoneyUtils.of(amountPaid) > overview.currentBalance) {
                throw IllegalStateException("Insufficient balance to pay EMI for '${loan.loanName}'. Available: ₹${MoneyUtils.formatInr(overview.currentBalance)}, Payment: ₹$amountPaid")
            }

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val now = System.currentTimeMillis()
            val refId = "MANUAL-EMI-$now"
            
            transactionDao.insertTransaction(
                TransactionEntity(
                    userId = uid,
                    title = "EMI: ${loan.loanName}",
                    amount = amountPaid,
                    type = "EXPENSE",
                    category = "Loans & Debt",
                    paymentMethod = "Bank Transfer",
                    date = todayStr,
                    timestamp = now,
                    isParsed = false,
                    merchant = loan.lenderName.ifBlank { "Bank" },
                    note = "EMI payment for ${loan.loanName}",
                    eventType = FinancialEventType.LOAN_PAYMENT.name,
                    status = FinancialStatus.COMPLETED.name,
                    referenceId = refId
                )
            )

            loanDao.insertRepayment(
                LoanRepaymentEntity(
                    loanId = loan.id,
                    userId = uid,
                    paidAmount = amountPaid,
                    paymentDate = now,
                    transactionRef = refId,
                    isAutoDetected = false
                )
            )
        }
    }

    fun activeLoansForUser(userId: String = currentUserId()): Flow<List<LoanEntity>> =
        if (userId.isNotBlank()) loanDao.getActiveLoansFlow(userId) else flowOf(emptyList())

    fun repaymentsForLoan(loanId: Long, userId: String = currentUserId()): Flow<List<LoanRepaymentEntity>> =
        if (userId.isNotBlank()) loanDao.getRepaymentsForLoanFlow(loanId, userId) else flowOf(emptyList())

    fun allRepaymentsForUser(userId: String = currentUserId()): Flow<List<LoanRepaymentEntity>> =
        if (userId.isNotBlank()) loanDao.getAllRepaymentsForUserFlow(userId) else flowOf(emptyList())

    fun getTotalRemainingDebtFlow(userId: String = currentUserId()): Flow<Double> =
        if (userId.isNotBlank()) loanDao.getTotalRemainingDebtFlow(userId) else flowOf(0.0)

    suspend fun closeLoan(loanId: Long, userId: String = currentUserId()) {
        loanDao.closeLoan(loanId, userId)
    }

    suspend fun recordManualLoanRepayment(
        loanId: Long,
        amount: Double,
        paymentMethod: String = "Bank Transfer",
        userId: String? = null
    ): Long = database.withTransaction {
        val uid = if (!userId.isNullOrBlank()) userId else currentUserId()
        val loan = (if (uid.isNotBlank()) loanDao.getLoanById(loanId, uid) else null)
            ?: loanDao.getLoanById(loanId.toInt())
            ?: throw NoSuchElementException("Loan with ID $loanId not found for user $uid")
        if (loan.isClosed) {
            throw IllegalStateException("Loan '${loan.loanName}' is already closed.")
        }
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            throw IllegalArgumentException("Repayment amount must be positive and finite.")
        }

        val effectiveUid = uid.ifBlank { loan.userId }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val now = System.currentTimeMillis()
        val refId = "MANUAL-EMI-$now"

        // 1. Record general financial transaction
        val txnId = transactionDao.insertTransaction(
            TransactionEntity(
                userId = effectiveUid,
                title = "EMI: ${loan.loanName}",
                amount = amount,
                type = "EXPENSE",
                category = "Loans & Debt",
                paymentMethod = paymentMethod,
                date = todayStr,
                timestamp = now,
                isParsed = false,
                merchant = loan.lenderName.ifBlank { "Bank" },
                note = "Manual EMI repayment for ${loan.loanName}",
                eventType = FinancialEventType.LOAN_PAYMENT.name,
                status = FinancialStatus.COMPLETED.name,
                referenceId = refId
            )
        )

        // 2. Record repayment entry
        loanDao.insertRepayment(
            LoanRepaymentEntity(
                loanId = loan.id,
                userId = effectiveUid,
                paidAmount = amount,
                paymentDate = now,
                transactionRef = refId,
                isAutoDetected = false
            )
        )

        txnId
    }

    suspend fun deleteLoan(id: Int) {
        loanDao.deleteLoan(id)
    }

    /**
     * Clears all local financial data for user isolation.
     * Ensures User A's data is never visible to User B upon logout/login.
     * All tables are purged atomically within a single Room transaction.
     */
    suspend fun clearUserLocalData(userId: String = currentUserId()) {
        database.withTransaction {
            if (userId.isNotBlank() && userId != "local_user") {
                transactionDao.deleteAll(userId)
                budgetDao.deleteAll(userId)
                categoryDao.deleteAll(userId)
                goalDao.deleteAll(userId)
                billDao.deleteAll(userId)
                subscriptionDao.deleteAll(userId)
                notificationDao.deleteAll(userId)
                loanDao.deleteAll(userId)
                loanDao.deleteAllRepayments(userId)
            } else {
                transactionDao.deleteAll()
                budgetDao.deleteAll()
                categoryDao.deleteAll()
                goalDao.deleteAll()
                billDao.deleteAll()
                subscriptionDao.deleteAll()
                notificationDao.deleteAll()
                loanDao.deleteAll()
                loanDao.deleteAllRepayments()
            }
            Unit
        }
        clearRejectedSubscriptionMerchants()
        com.example.notification.WealthWiseNotificationDeduplicator(context).resetAll()
        startingBankBalance = 0.0
        userName = ""
        userEmail = ""
        setCompletedInitialBalanceSetupForUser(false, userId)
        has_completed_initial_balance_setup = false
        hasCompletedSetup = false
        isLoggedIn = false
    }

    /**
     * Seeds synchronized default budget categories scoped to userId when onboarding is completed.
     */
    suspend fun seedDefaultBudgetCategories(userId: String = currentUserId()) {
        val defaultBudgets = listOf(
            BudgetEntity(userId = userId, category = "Food", monthlyLimit = 2000.0, colorHex = "#10B981"),
            BudgetEntity(userId = userId, category = "Shopping", monthlyLimit = 8000.0, colorHex = "#3B82F6"),
            BudgetEntity(userId = userId, category = "Transport", monthlyLimit = 5000.0, colorHex = "#F59E0B"),
            BudgetEntity(userId = userId, category = "Personal Transfer", monthlyLimit = 5000.0, colorHex = "#6366F1"),
            BudgetEntity(userId = userId, category = "Bills", monthlyLimit = 12000.0, colorHex = "#EF4444"),
            BudgetEntity(userId = userId, category = "Health", monthlyLimit = 5000.0, colorHex = "#EC4899"),
            BudgetEntity(userId = userId, category = "Entertainment", monthlyLimit = 2500.0, colorHex = "#8B5CF6"),
            BudgetEntity(userId = userId, category = "Other", monthlyLimit = 5000.0, colorHex = "#64748B")
        )
        val defaultCategories = defaultBudgets.map {
            CategoryEntity(userId = it.userId, name = it.category, budgetLimit = it.monthlyLimit, colorHex = it.colorHex)
        }
        categoryDao.insertAll(defaultCategories)
        budgetDao.insertAll(defaultBudgets)
    }

    /**
     * Ensures clean slate for newly authenticated user without demo seeding.
     */
    suspend fun ensureDefaultDataSeeded() {
        // Fresh database remains clean (₹0 balance, 0 records).
    }
}