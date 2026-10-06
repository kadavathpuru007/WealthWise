/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development Team:
 * - Purushotham
 *
 * Module: Main ViewModel & Central State Engine
 */

package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.local.*
import com.example.data.parser.ParserConfidence
import com.example.data.parser.TransactionDirection
import com.example.data.parser.TransactionParser
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.*
import com.example.notification.*
import com.example.ui.auth.AuthViewModel
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen {
    LOGIN,
    REGISTER,
    EMAIL_VERIFICATION,
    ENTER_BALANCE,
    HOME,
    ACTIVITY,
    AI_TOOLS,
    GOALS,
    SETTINGS,
    MORE,
    LOANS_DEBTS,
    BUDGET,
    INVESTMENTS,
    SUBSCRIPTION_KILLER,
    UPI_PAYMENT
}

data class WealthWiseUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val activityFilter: String = "ALL",
    val isLoggedIn: Boolean = false,
    val hasCompletedSetup: Boolean = false,
    val userName: String = "",
    val userEmail: String = "",
    val startingBalance: Double = 0.0,
    val isMobilePhoneView: Boolean = false,
    val isLoading: Boolean = false,
    val authErrorMessage: String? = null,

    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
    val dismissedSessionNotificationIds: Set<String> = emptySet(),

    // Authoritative Central Financial Engine Overview
    val overview: FinancialOverview = FinancialOverview(),

    // Computed financial metrics (synced from overview)
    val totalInflow: Double = 0.0,
    val totalOutflow: Double = 0.0,
    val netCashflow: Double = 0.0,
    val savingsRate: Int = 0,
    val bankSavingsTotal: Double = 0.0,
    val investmentsTotal: Double = 0.0,
    val loansRemaining: Double = 0.0,

    // Validation Dialog States
    val validationBlockedError: ValidationResult.Blocked? = null,
    val validationWarning: ValidationResult.Warning? = null,

    // Permissions & System Access States (Android-Derived)
    val pushNotificationsEnabled: Boolean = true,
    val budgetAlertsEnabled: Boolean = true,
    val billEmiAlertsEnabled: Boolean = true,
    val goalAlertsEnabled: Boolean = true,
    val syncAlertsEnabled: Boolean = true,
    val billReminderDaysAhead: Int = 1,
    val isSystemNotificationDisabled: Boolean = false,
    val showNotificationPermissionRationale: Boolean = false,
    val showNotificationAccessRationale: Boolean = false,
    val showPostNotificationRationale: Boolean = false,
    val showUsageAccessRationale: Boolean = false,
    val notificationAccessStatus: com.example.permission.AccessStatus = com.example.permission.AccessStatus.NOT_CONNECTED,
    val postNotificationStatus: com.example.permission.AccessStatus = com.example.permission.AccessStatus.NOT_CONNECTED,
    val usageAccessStatus: com.example.permission.AccessStatus = com.example.permission.AccessStatus.NOT_CONNECTED,
    val internetStatus: com.example.permission.AccessStatus = com.example.permission.AccessStatus.CONNECTED,
    val appLaunchFallback: com.example.permission.AppLaunchFallbackState? = null,
    val subscriptionUsageMap: Map<Int, com.example.permission.SubscriptionUsageInfo> = emptyMap(),
    val potentialSubscriptions: List<com.example.domain.subscription.PotentialSubscription> = emptyList(),
    val subscriptionFilter: String = "ALL", // "ALL", "FLAGGED", "ACTIVE", "PAUSED", "CANCELLED"
    val subscriptionSettings: com.example.domain.subscription.SubscriptionSettings = com.example.domain.subscription.SubscriptionSettings(),
    val upcomingAlertsEnabled: Boolean = true,
    val selectedSubscriptionForDetail: SubscriptionEntity? = null,
    val showSubscriptionDetailDialog: SubscriptionEntity? = null,
    val showConfirmSubscriptionPaymentDialog: SubscriptionEntity? = null,
    val priceChangeAlerts: List<com.example.domain.subscription.SubscriptionPriceChange> = emptyList(),
    val smsParsingEnabled: Boolean = true,
    val accountAggregatorSyncEnabled: Boolean = true,

    // Dialogs & Sheets
    val showAddTransactionDialog: Boolean = false,
    val showBudgetAdjustDialog: Boolean = false,
    val showAddBudgetDialog: Boolean = false,
    val showAddBudgetCategoryDialog: Boolean = false,
    val showNotificationsDrawer: Boolean = false,
    val showPayBillDialog: BillEntity? = null,
    val showEditProfileDialog: Boolean = false,
    val showEditBalanceDialog: Boolean = false,
    val showFAQGuideDialog: Boolean = false,
    val showAddGoalDialog: Boolean = false,
    val showFundGoalDialog: GoalEntity? = null,
    val showAddLoanDialog: Boolean = false,
    val showAddSubscriptionDialog: Boolean = false,
    val showTaxAnalyzerDialog: Boolean = false,
    val showAnomalyGuardianDialog: Boolean = false,
    val showSimulateTxnSheet: Boolean = false,

    val toastMessage: String? = null,
    val firebaseProjectId: String = FirebaseManager.PROJECT_ID,
    val isFirebaseConnected: Boolean = true,
    val showUniversalSearch: Boolean = false,
    val marketFocusedSymbol: String? = null
) {
    val totalBudget: Double get() = overview.totalPlannedBudget.toDouble()
    val availableBudgetFunds: Double get() = overview.currentBalance.toDouble()
    val safeToSpend: Double get() = overview.safeToSpend.toDouble()
    val availableBalance: Double get() = overview.availableBalance.toDouble()
    val actualBalance: Double get() = overview.currentBalance.toDouble()
    val totalSavingsAmount: Double get() = overview.totalSavings.toDouble()
}

private data class OverviewData(
    val txns: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<GoalEntity>,
    val loans: List<LoanEntity>,
    val subs: List<SubscriptionEntity>,
    val startingBalance: Double
)

class WealthWiseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WealthWiseRepository(
        WealthWiseDatabase.getDatabase(application),
        application
    )
    private var userListenerRegistration: ListenerRegistration? = null

    private val _uiState = MutableStateFlow(
        run {
            val initialUser = FirebaseManager.currentUser
            val isUserLoggedIn = repository.isLoggedIn || initialUser != null
            val hasCompletedInitialSetup = repository.has_completed_initial_balance_setup || repository.hasCompletedSetup

            val initialScreen = when {
                !isUserLoggedIn -> AppScreen.LOGIN
                !hasCompletedInitialSetup -> AppScreen.ENTER_BALANCE
                else -> AppScreen.HOME
            }

            WealthWiseUiState(
                currentScreen = initialScreen,
                isLoggedIn = isUserLoggedIn,
                hasCompletedSetup = hasCompletedInitialSetup,
                userName = repository.userName.ifBlank { initialUser?.displayName.orEmpty() },
                userEmail = repository.userEmail.ifBlank { initialUser?.email.orEmpty() },
                startingBalance = repository.startingBankBalance,
                isMobilePhoneView = repository.isMobilePhoneView,
                pushNotificationsEnabled = repository.pushNotificationsEnabled,
                budgetAlertsEnabled = repository.budgetAlertsEnabled,
                billEmiAlertsEnabled = repository.billEmiAlertsEnabled,
                goalAlertsEnabled = repository.goalAlertsEnabled,
                syncAlertsEnabled = repository.syncAlertsEnabled,
                billReminderDaysAhead = repository.billReminderDaysAhead,
                accountAggregatorSyncEnabled = repository.accountAggregatorSyncEnabled,
                smsParsingEnabled = repository.smsParsingEnabled,
                isSystemNotificationDisabled = !NotificationAccessUtils.areNotificationsEnabledOnDevice(application)
            )
        }
    )
    val uiState: StateFlow<WealthWiseUiState> = _uiState.asStateFlow()

    private val _currentUserId = MutableStateFlow(
        FirebaseManager.currentUser?.uid.takeUnless { it.isNullOrBlank() } ?: "local_user"
    )
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    init {
        FirebaseManager.initialize(application)
        FirebaseManager.currentUser?.let { user ->
            _currentUserId.value = user.uid
            attachFirestoreSync(user.uid)
        }

        viewModelScope.launch {
            repository.ensureDefaultDataSeeded()
            refreshSystemAccessStatuses()
            evaluateDeviceNotifications()
        }

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        _currentUserId.flatMapLatest { uid ->
            if (uid.isBlank()) {
                flowOf(OverviewData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0.0))
            } else {
                combine(
                    combine(
                        repository.getTransactionsFlow(uid).catch { emit(emptyList()) },
                        repository.budgetsForUser(uid).catch { emit(emptyList()) },
                        repository.goalsForUser(uid).catch { emit(emptyList()) }
                    ) { txns, budgets, goals -> Triple(txns, budgets, goals) },
                    combine(
                        repository.loansForUser(uid).catch { emit(emptyList()) },
                        repository.subscriptionsForUser(uid).catch { emit(emptyList()) },
                        repository.getInitialBalanceFlow(uid).catch { emit(0.0) }
                    ) { loans, subs, initialBal -> Triple(loans, subs, initialBal) }
                ) { (txns, budgets, goals), (loans, subs, initialBal) ->
                    OverviewData(txns, budgets, goals, loans, subs, initialBal)
                }.catch { e ->
                    Log.e("WealthWiseVM", "Error combining overview flows", e)
                    emit(OverviewData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0.0))
                }
            }
        }.onEach { data ->
            val overview = FinancialCalculationEngine.calculateOverview(
                startingBalance = MoneyUtils.of(data.startingBalance),
                transactions = data.txns,
                budgets = data.budgets,
                goals = data.goals,
                loans = data.loans,
                subscriptions = data.subs
            )
            val usageMap = com.example.permission.PermissionManager.querySubscriptionUsage(getApplication(), data.subs)
            val potential = com.example.domain.subscription.SubscriptionRecurringDetector.detectPotentialSubscriptions(
                transactions = data.txns,
                existingSubscriptions = data.subs,
                rejectedMerchants = repository.getRejectedSubscriptionMerchants()
            )
            val priceChanges = com.example.domain.subscription.SubscriptionRecurringDetector.detectPriceChanges(
                subscriptions = data.subs,
                transactions = data.txns
            )
            val subSettings = com.example.domain.subscription.SubscriptionSettings(
                usageInsightsEnabled = repository.subscriptionUsageInsightsEnabled,
                upcomingAlertsEnabled = true,               
                unusedAlertsEnabled = repository.unusedSubscriptionAlertsEnabled,
                priceChangeAlertsEnabled = repository.priceChangeAlertsEnabled,
                observationPeriodDays = repository.subscriptionObservationPeriodDays
)
            _uiState.update { current ->
                current.copy(
                    startingBalance = data.startingBalance,
                    transactions = data.txns,
                    budgets = data.budgets,
                    goals = data.goals,
                    loans = data.loans,
                    subscriptions = data.subs,
                    subscriptionUsageMap = usageMap,
                    potentialSubscriptions = potential,
                    priceChangeAlerts = priceChanges,
                    subscriptionSettings = subSettings,
                    overview = overview,
                    totalInflow = overview.totalIncome.toDouble(),
                    totalOutflow = overview.totalExpenses.toDouble(),
                    netCashflow = overview.netCashflow.toDouble(),
                    savingsRate = overview.savingsRate,
                    bankSavingsTotal = overview.totalSavings.toDouble(),
                    investmentsTotal = overview.totalInvestments.toDouble(),
                    loansRemaining = overview.totalLoanLiabilities.toDouble()
                )
            }
        }.launchIn(viewModelScope)

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        _currentUserId.flatMapLatest { uid ->
            if (uid.isBlank()) flowOf(emptyList()) else repository.billsForUser(uid)
        }.onEach { bills ->
            _uiState.update { it.copy(bills = bills) }
        }.launchIn(viewModelScope)

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        _currentUserId.flatMapLatest { uid ->
            if (uid.isBlank()) flowOf(emptyList()) else repository.notificationsForUser(uid)
        }.onEach { notifs ->
            _uiState.update { it.copy(notifications = notifs) }
        }.launchIn(viewModelScope)
    }

    private fun refreshOverview(newStartingBalance: Double? = null) {
        val balanceToUse = newStartingBalance ?: _uiState.value.startingBalance
        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(balanceToUse),
            transactions = _uiState.value.transactions,
            budgets = _uiState.value.budgets,
            goals = _uiState.value.goals,
            loans = _uiState.value.loans,
            subscriptions = _uiState.value.subscriptions
        )
        _uiState.update { current ->
            current.copy(
                startingBalance = balanceToUse,
                overview = overview,
                totalInflow = overview.totalIncome.toDouble(),
                totalOutflow = overview.totalExpenses.toDouble(),
                netCashflow = overview.netCashflow.toDouble(),
                savingsRate = overview.savingsRate,
                bankSavingsTotal = overview.totalSavings.toDouble(),
                investmentsTotal = overview.totalInvestments.toDouble(),
                loansRemaining = overview.totalLoanLiabilities.toDouble()
            )
        }
    }

    private fun attachFirestoreSync(uid: String) {
        userListenerRegistration?.remove()
        userListenerRegistration = FirebaseManager.attachUserListener(uid) { balance, hasSet, name ->
            _uiState.update { current ->
                current.copy(
                    startingBalance = balance ?: current.startingBalance,
                    hasCompletedSetup = hasSet ?: current.hasCompletedSetup,
                    userName = if (!name.isNullOrBlank()) name else current.userName
                )
            }
            if (balance != null) {
                repository.startingBankBalance = balance
            }
            if (hasSet != null) {
                repository.hasCompletedSetup = hasSet
                repository.has_completed_initial_balance_setup = hasSet
            }
            if (!name.isNullOrBlank()) {
                repository.userName = name
            }
        }
    }

    private fun syncTxToFirestore(tx: TransactionEntity) {
        val uid = FirebaseManager.currentUser?.uid ?: return
        viewModelScope.launch {
            FirebaseManager.syncTransactionToFirestore(uid, tx)
        }
    }

    private var lastResendTimestamp: Long = 0L

    fun login(email: String, pass: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            val msg = "Please enter your email address."
            _uiState.update { it.copy(toastMessage = msg, authErrorMessage = msg) }
            return
        }
        if (pass.isBlank()) {
            val msg = "Please enter your password."
            _uiState.update { it.copy(toastMessage = msg, authErrorMessage = msg) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, authErrorMessage = null) }
            val fbResult = FirebaseManager.loginUser(trimmedEmail, pass)
            val fbUser = fbResult.getOrNull()
            val fbError = fbResult.exceptionOrNull()

            if (fbUser == null || fbError != null) {
                val errorMsg = when {
                    fbError is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ||
                    fbError?.message?.contains("password", ignoreCase = true) == true ||
                    fbError?.message?.contains("invalid-credential", ignoreCase = true) == true ->
                        "Incorrect password. Please try again."
                    fbError is com.google.firebase.auth.FirebaseAuthInvalidUserException ||
                    fbError?.message?.contains("user-not-found", ignoreCase = true) == true ->
                        "No account found with this email. Please check spelling or register."
                    fbError is com.google.firebase.FirebaseNetworkException ||
                    fbError?.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Please check your internet connection."
                    else -> fbError?.localizedMessage ?: "Sign in failed. Please try again."
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        toastMessage = errorMsg,
                        authErrorMessage = errorMsg,
                        isLoggedIn = false
                    )
                }
                return@launch
            }

            val effectiveName = fbUser.displayName?.takeIf { it.isNotBlank() }
                ?: trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            val effectiveEmail = fbUser.email ?: trimmedEmail

            _currentUserId.value = fbUser.uid
            repository.userName = effectiveName
            repository.userEmail = effectiveEmail
            repository.isLoggedIn = true

            val completed = repository.hasCompletedInitialBalanceSetupForUser(fbUser.uid) ||
                    repository.has_completed_initial_balance_setup ||
                    repository.hasCompletedSetup

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    hasCompletedSetup = completed,
                    userName = effectiveName,
                    userEmail = effectiveEmail,
                    authErrorMessage = null,
                    currentScreen = if (completed) AppScreen.HOME else AppScreen.ENTER_BALANCE,
                    toastMessage = "Welcome back, $effectiveName!"
                )
            }

            attachFirestoreSync(fbUser.uid)
        }
    }

    fun register(name: String, email: String, pass: String) {
        val trimmedEmail = email.trim()
        val trimmedName = name.trim()

        if (trimmedEmail.isBlank() || !AuthViewModel.isValidEmail(trimmedEmail)) {
            val errorMsg = "Please enter a valid email address."
            _uiState.update { it.copy(toastMessage = errorMsg, authErrorMessage = errorMsg) }
            return
        }
        if (pass.length < 6) {
            val errorMsg = "Password must contain at least 6 characters."
            _uiState.update { it.copy(toastMessage = errorMsg, authErrorMessage = errorMsg) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, authErrorMessage = null) }
            val fbResult = FirebaseManager.registerUser(trimmedName, trimmedEmail, pass)
            val fbUser = fbResult.getOrNull()
            val fbError = fbResult.exceptionOrNull()

            if (fbUser == null || fbError != null) {
                val errorMsg = when {
                    fbError is FirebaseAuthUserCollisionException || fbError?.message?.contains("already registered", ignoreCase = true) == true ->
                        "This email is already registered. Please sign in."
                    fbError is FirebaseAuthWeakPasswordException || fbError?.message?.contains("weak", ignoreCase = true) == true ->
                        "Password must contain at least 6 characters."
                    else -> fbError?.localizedMessage ?: "Registration failed. Please try again."
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        toastMessage = errorMsg,
                        authErrorMessage = errorMsg,
                        isLoggedIn = false
                    )
                }
                return@launch
            }

            val effectiveName = fbUser.displayName?.takeIf { it.isNotBlank() } ?: trimmedName
            val effectiveEmail = fbUser.email ?: trimmedEmail

            _currentUserId.value = fbUser.uid
            repository.userName = effectiveName
            repository.userEmail = effectiveEmail
            repository.isLoggedIn = true
            repository.has_completed_initial_balance_setup = false
            repository.hasCompletedSetup = false

            try {
                FirebaseManager.sendVerificationEmail(fbUser)
            } catch (_: Exception) {}

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    hasCompletedSetup = false,
                    userName = effectiveName,
                    userEmail = effectiveEmail,
                    authErrorMessage = null,
                    currentScreen = AppScreen.EMAIL_VERIFICATION,
                    toastMessage = "Account created! Verification link sent to $effectiveEmail."
                )
            }
        }
    }

    fun checkEmailVerificationStatus(onVerified: () -> Unit = {}) {
        val user = FirebaseManager.currentUser
        val effectiveName = user?.displayName?.takeIf { it.isNotBlank() } ?: _uiState.value.userName
        val effectiveEmail = user?.email ?: _uiState.value.userEmail
        val uid = user?.uid ?: _currentUserId.value

        _currentUserId.value = uid
        repository.userName = effectiveName
        repository.userEmail = effectiveEmail
        repository.isLoggedIn = true

        val completed = repository.hasCompletedInitialBalanceSetupForUser(uid) ||
                repository.has_completed_initial_balance_setup ||
                repository.hasCompletedSetup

        _uiState.update {
            it.copy(
                isLoading = false,
                isLoggedIn = true,
                hasCompletedSetup = completed,
                userName = effectiveName,
                userEmail = effectiveEmail,
                authErrorMessage = null,
                currentScreen = if (completed) AppScreen.HOME else AppScreen.ENTER_BALANCE,
                toastMessage = "Email verified successfully!"
            )
        }
        if (user != null) {
            attachFirestoreSync(user.uid)
        }
        onVerified()
    }

    fun bypassEmailVerificationForDebug() {
        val user = FirebaseManager.currentUser
        val effectiveName = user?.displayName?.takeIf { it.isNotBlank() } ?: _uiState.value.userName
        val effectiveEmail = user?.email ?: _uiState.value.userEmail
        val uid = user?.uid ?: "local_user"

        _currentUserId.value = uid
        repository.userName = effectiveName
        repository.userEmail = effectiveEmail
        repository.isLoggedIn = true

        val completed = repository.hasCompletedInitialBalanceSetupForUser(uid) ||
                repository.has_completed_initial_balance_setup ||
                repository.hasCompletedSetup

        _uiState.update {
            it.copy(
                isLoading = false,
                isLoggedIn = true,
                hasCompletedSetup = completed,
                userName = effectiveName,
                userEmail = effectiveEmail,
                authErrorMessage = null,
                currentScreen = if (completed) AppScreen.HOME else AppScreen.ENTER_BALANCE,
                toastMessage = "Continuing to setup..."
            )
        }
        if (user != null) {
            attachFirestoreSync(user.uid)
        }
    }

    fun resendVerificationEmail() {
        val user = FirebaseManager.currentUser
        if (user == null) {
            val msg = "Your session has expired. Please log in again."
            _uiState.update { it.copy(authErrorMessage = msg, toastMessage = msg) }
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastResendTimestamp < AuthViewModel.RESEND_COOLDOWN_MS) {
            val remainingSecs = ((AuthViewModel.RESEND_COOLDOWN_MS - (currentTime - lastResendTimestamp)) / 1000).coerceAtLeast(1)
            val msg = "Please wait $remainingSecs seconds before requesting another email."
            _uiState.update { it.copy(authErrorMessage = msg, toastMessage = msg) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, authErrorMessage = null) }
            val sendResult = FirebaseManager.sendVerificationEmail(user)
            lastResendTimestamp = System.currentTimeMillis()
            val msg = if (sendResult.isSuccess) {
                "Verification link resent! Check Spam/All Mail."
            } else {
                "Email request submitted. Please check Spam/All Mail."
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    authErrorMessage = null,
                    toastMessage = msg
                )
            }
        }
    }

    fun backToLoginFromVerification() {
        FirebaseManager.signOut()
        _currentUserId.value = ""
        repository.isLoggedIn = false
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                authErrorMessage = null,
                currentScreen = AppScreen.LOGIN,
                toastMessage = null
            )
        }
    }

    fun clearAuthError() {
        if (_uiState.value.authErrorMessage != null) {
            _uiState.update { it.copy(authErrorMessage = null) }
        }
    }

    fun submitStartingBalance(balance: Double) {
        val validBalance = if (balance < 0) 0.0 else balance
        viewModelScope.launch(Dispatchers.IO) {
            val uid = FirebaseManager.currentUser?.uid.takeUnless { it.isNullOrBlank() } ?: _currentUserId.value.takeUnless { it.isBlank() } ?: "local_user"

            try {
                repository.setInitialBalance(validBalance, uid)
                repository.setCompletedInitialBalanceSetupForUser(true, uid)
                repository.seedDefaultBudgetCategories(uid)
            } catch (e: Exception) {
                Log.e("WealthWiseVM", "Error setting balance in Room", e)
            }

            repository.startingBankBalance = validBalance
            repository.has_completed_initial_balance_setup = true
            repository.hasCompletedSetup = true
            repository.isLoggedIn = true

            try {
                FirebaseManager.setInitialBalanceInFirestore(uid, validBalance)
            } catch (_: Throwable) {}

            withContext(Dispatchers.Main) {
                refreshOverview(validBalance)
                _uiState.update {
                    it.copy(
                        hasCompletedSetup = true,
                        isLoggedIn = true,
                        startingBalance = validBalance,
                        currentScreen = AppScreen.HOME,
                        toastMessage = "Starting balance of ₹${MoneyUtils.formatInr(MoneyUtils.of(validBalance))} configured!"
                    )
                }
            }
        }
    }

    fun logout() {
        userListenerRegistration?.remove()
        userListenerRegistration = null
        FirebaseManager.signOut()
        _currentUserId.value = ""
        repository.isLoggedIn = false
        repository.has_completed_initial_balance_setup = false
        repository.hasCompletedSetup = false
        _uiState.value = WealthWiseUiState(
            isLoggedIn = false,
            hasCompletedSetup = false,
            currentScreen = AppScreen.LOGIN,
            startingBalance = 0.0,
            toastMessage = "You have been logged out securely."
        )
    }

    private val backStack = mutableListOf<AppScreen>()
    private var lastNavTimestamp: Long = 0L

    fun navigateTo(screen: AppScreen, addToBackStack: Boolean = true) {
        val now = android.os.SystemClock.uptimeMillis()
        val current = _uiState.value.currentScreen
        if (current == screen) return
        if (now - lastNavTimestamp < 200L) return
        lastNavTimestamp = now

        synchronized(backStack) {
            if (screen == AppScreen.HOME) {
                backStack.clear()
            } else if (addToBackStack &&
                current != AppScreen.LOGIN &&
                current != AppScreen.REGISTER &&
                current != AppScreen.EMAIL_VERIFICATION &&
                current != AppScreen.ENTER_BALANCE
            ) {
                backStack.remove(screen)
                if (backStack.lastOrNull() != current) {
                    backStack.add(current)
                }
            }
        }
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun navigateBack(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastNavTimestamp < 150L) return false
        lastNavTimestamp = now

        synchronized(backStack) {
            if (backStack.isNotEmpty()) {
                val prev = backStack.removeAt(backStack.size - 1)
                _uiState.update { it.copy(currentScreen = prev) }
                return true
            } else if (_uiState.value.currentScreen != AppScreen.HOME) {
                _uiState.update { it.copy(currentScreen = AppScreen.HOME) }
                return true
            }
            return false
        }
    }

    fun canNavigateBack(): Boolean {
        synchronized(backStack) {
            return backStack.isNotEmpty() || _uiState.value.currentScreen != AppScreen.HOME
        }
    }

    fun navigateToActivityWithFilter(filter: String) {
        _uiState.update { it.copy(activityFilter = filter) }
        navigateTo(AppScreen.ACTIVITY)
    }

    fun fundGoal(goalId: Long, amount: Double) {
        val goal = _uiState.value.goals.find { it.id == goalId } ?: return
        val validation = FinancialValidationEngine.validateGoalContribution(
            goal = goal,
            contributionAmount = MoneyUtils.of(amount),
            availableBalance = _uiState.value.overview.availableBalance
        )
        when (validation) {
            is ValidationResult.Blocked -> {
                _uiState.update { it.copy(validationBlockedError = validation) }
                return
            }
            is ValidationResult.Warning -> {
                _uiState.update { it.copy(validationWarning = validation) }
            }
            is ValidationResult.Valid -> {}
        }

        viewModelScope.launch {
            repository.fundGoal(goalId, amount)
            _uiState.update {
                it.copy(
                    showFundGoalDialog = null,
                    toastMessage = "Allocated ₹${MoneyUtils.formatInr(MoneyUtils.of(amount))} to '${goal.title}'!"
                )
            }
            evaluateDeviceNotifications()
        }
    }

    fun fundGoal(goalId: Int, amount: Double) = fundGoal(goalId.toLong(), amount)

    fun setShowSimulateTxn(show: Boolean) {
        _uiState.update { it.copy(showSimulateTxnSheet = show) }
    }

    fun parseAndAddNotification(rawText: String, overrideCategory: String? = null) {
        try {
            val parsed = TransactionParser.parse(rawText)
            if (parsed.confidence == ParserConfidence.REJECTED || parsed.amount <= 0.0) {
                _uiState.update {
                    it.copy(
                        toastMessage = "Ignored non-transaction message (${parsed.rejectionReason}): ${parsed.title}"
                    )
                }
                return
            }

            if (parsed.status == FinancialStatus.FAILED) {
                _uiState.update {
                    it.copy(
                        toastMessage = "Transaction failed (${parsed.rejectionReason}). No ledger entry logged."
                    )
                }
                return
            }

            val finalCategory = overrideCategory ?: parsed.category
            val txType = if (parsed.direction.isInflow || parsed.isInflow) "credit" else "debit"
            val eventType = if (txType == "credit") FinancialEventType.INCOME else FinancialEventType.EXPENSE

            val validation = FinancialValidationEngine.validateTransaction(
                amount = MoneyUtils.of(parsed.amount),
                eventType = eventType,
                merchantOrTitle = parsed.title,
                currentBalance = _uiState.value.overview.currentBalance,
                existingTransactions = _uiState.value.transactions,
                referenceId = parsed.referenceId
            )

            when (validation) {
                is ValidationResult.Blocked -> {
                    _uiState.update { it.copy(validationBlockedError = validation) }
                    return
                }
                is ValidationResult.Warning -> {
                    _uiState.update { it.copy(validationWarning = validation) }
                }
                is ValidationResult.Valid -> {}
            }

            viewModelScope.launch {
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(parsed.timestamp))
                val tx = TransactionEntity(
                    title = parsed.title,
                    amount = kotlin.math.abs(parsed.amount),
                    type = txType,
                    category = finalCategory,
                    paymentMethod = parsed.paymentMethod,
                    date = dateStr,
                    timestamp = parsed.timestamp,
                    isParsed = true,
                    merchant = parsed.merchant ?: "",
                    note = "Parsed from notification: ${rawText.take(40)}...",
                    eventType = txType,
                    status = parsed.status.name,
                    referenceId = parsed.referenceId,
                    sourceAccount = parsed.accountHint
                )
                val insertedId = repository.insertTransaction(tx)
                syncTxToFirestore(tx.copy(id = insertedId.toInt()))
                repository.addNotification(
                    NotificationEntity(
                        title = "Parsed Transaction: ${parsed.title}",
                        message = "Extracted ₹${parsed.amount.toInt()} -> $finalCategory (${parsed.paymentMethod})",
                        type = "PARSED"
                    )
                )
                _uiState.update {
                    it.copy(
                        toastMessage = "Successfully logged ₹${parsed.amount.toInt()} ($txType - $finalCategory)!"
                    )
                }
                evaluateDeviceNotifications()
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    toastMessage = "Could not parse notification: ${e.localizedMessage}"
                )
            }
        }
    }

    fun addManualTransaction(
        title: String,
        amount: Double,
        type: String,
        category: String,
        paymentMethod: String,
        isParsed: Boolean = false
    ) {
        val eventType = if (type.equals("INCOME", ignoreCase = true)) {
            FinancialEventType.INCOME
        } else {
            FinancialEventType.EXPENSE
        }

        val validation = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(amount),
            eventType = eventType,
            merchantOrTitle = title,
            currentBalance = _uiState.value.overview.currentBalance,
            existingTransactions = _uiState.value.transactions
        )

        when (validation) {
            is ValidationResult.Blocked -> {
                _uiState.update { it.copy(validationBlockedError = validation) }
                return
            }
            is ValidationResult.Warning -> {
                _uiState.update { it.copy(validationWarning = validation) }
            }
            is ValidationResult.Valid -> {}
        }

        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val tx = TransactionEntity(
                title = title.trim(),
                amount = kotlin.math.abs(amount),
                type = type,
                category = category,
                paymentMethod = paymentMethod,
                date = dateStr,
                isParsed = isParsed,
                merchant = title.trim(),
                eventType = eventType.name,
                status = FinancialStatus.COMPLETED.name
            )
            val insertedId = repository.insertTransaction(tx)
            syncTxToFirestore(tx.copy(id = insertedId.toInt()))

            repository.addNotification(
                NotificationEntity(
                    title = "New Transaction Logged",
                    message = "$title: ₹${amount.toInt()} ($type) recorded under $category.",
                    type = "TRANSACTION"
                )
            )
            _uiState.update {
                it.copy(
                    showAddTransactionDialog = false,
                    toastMessage = "Added $type of ₹${amount.toInt()} under $category"
                )
            }
            evaluateDeviceNotifications()
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _uiState.update { it.copy(toastMessage = "Transaction removed") }
        }
    }

    fun payBill(bill: BillEntity) {
        val validation = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(bill.amount),
            eventType = FinancialEventType.EXPENSE,
            merchantOrTitle = bill.title,
            currentBalance = _uiState.value.overview.currentBalance
        )
        if (validation is ValidationResult.Blocked) {
            _uiState.update { it.copy(validationBlockedError = validation) }
            return
        }

        viewModelScope.launch {
            repository.payBill(bill.id)
            repository.addNotification(
                NotificationEntity(
                    title = "Bill Paid: ${bill.title}",
                    message = "₹${bill.amount.toInt()} paid for ${bill.title}.",
                    type = "BILL"
                )
            )
            _uiState.update {
                it.copy(
                    showPayBillDialog = null,
                    toastMessage = "Paid ₹${bill.amount.toInt()} for ${bill.title}!"
                )
            }
            evaluateDeviceNotifications()
        }
    }

    fun cancelSubscription(sub: SubscriptionEntity) {
        viewModelScope.launch {
            repository.updateSubscriptionStatus(sub.id, "CANCELLED")
            val annualSaving = com.example.domain.subscription.SubscriptionCalculations.calculateAnnualCost(
                if (sub.amount > 0) sub.amount else sub.monthlyCost,
                sub.billingPeriod
            )
            repository.addNotification(
                NotificationEntity(
                    title = "Subscription Cancelled: ${sub.name}",
                    message = "Cancelled ${sub.name}. You will save ₹${annualSaving.toInt()} per year!",
                    type = "INFO"
                )
            )
            _uiState.update {
                it.copy(
                    showSubscriptionDetailDialog = null,
                    toastMessage = "Cancelled ${sub.name}. Saving ₹${annualSaving.toInt()}/year!"
                )
            }
        }
    }

    fun pauseSubscription(sub: SubscriptionEntity) {
        viewModelScope.launch {
            repository.updateSubscriptionStatus(sub.id, "PAUSED")
            _uiState.update {
                it.copy(
                    showSubscriptionDetailDialog = null,
                    toastMessage = "Paused ${sub.name}."
                )
            }
        }
    }

    fun reactivateSubscription(sub: SubscriptionEntity) {
        viewModelScope.launch {
            repository.updateSubscriptionStatus(sub.id, "ACTIVE")
            _uiState.update {
                it.copy(
                    showSubscriptionDetailDialog = null,
                    toastMessage = "Reactivated ${sub.name}."
                )
            }
        }
    }

    fun archiveSubscription(sub: SubscriptionEntity) {
        viewModelScope.launch {
            repository.updateSubscriptionStatus(sub.id, "ARCHIVED")
            _uiState.update {
                it.copy(
                    showSubscriptionDetailDialog = null,
                    toastMessage = "Archived ${sub.name}."
                )
            }
        }
    }

    fun deleteSubscription(subId: Int) {
        viewModelScope.launch {
            repository.deleteSubscription(subId)
            _uiState.update {
                it.copy(
                    showSubscriptionDetailDialog = null,
                    toastMessage = "Subscription removed from tracking."
                )
            }
        }
    }

    fun addSubscription(
        name: String,
        amount: Double,
        billingPeriod: String = "MONTHLY",
        category: String = "Subscription",
        linkedPackageName: String = "",
        nextExpectedPaymentDate: String = ""
    ) {
        if (name.isBlank() || amount <= 0.0) {
            _uiState.update { it.copy(toastMessage = "Please enter a valid subscription name and amount.") }
            return
        }
        val monthlyEquivalent = com.example.domain.subscription.SubscriptionCalculations.calculateMonthlyEquivalent(
            amount,
            billingPeriod
        )
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val computedNextDate = if (nextExpectedPaymentDate.isNotBlank()) {
            nextExpectedPaymentDate
        } else {
            com.example.domain.subscription.SubscriptionCalculations.estimateNextPaymentDate(todayStr, billingPeriod)
        }

        viewModelScope.launch {
            val sub = SubscriptionEntity(
                name = name.trim(),
                merchantName = name.trim(),
                amount = amount,
                billingPeriod = billingPeriod,
                category = category.ifBlank { "Subscription" },
                linkedPackageName = linkedPackageName.trim(),
                monthlyCost = monthlyEquivalent,
                nextExpectedPaymentDate = computedNextDate,
                source = "MANUAL",
                status = "ACTIVE"
            )
            repository.insertSubscription(sub)
            _uiState.update {
                it.copy(
                    showAddSubscriptionDialog = false,
                    toastMessage = "Added '$name' (₹${amount.toInt()} / ${billingPeriod.lowercase()}) to subscriptions."
                )
            }
        }
    }

    fun updateSubscription(subscription: SubscriptionEntity) {
        viewModelScope.launch {
            repository.updateSubscription(subscription)
            _uiState.update {
                it.copy(
                    showSubscriptionDetailDialog = null,
                    toastMessage = "Updated ${subscription.name} details."
                )
            }
        }
    }

    fun confirmDetectedSubscription(candidate: com.example.domain.subscription.PotentialSubscription) {
        viewModelScope.launch {
            val monthlyEquivalent = com.example.domain.subscription.SubscriptionCalculations.calculateMonthlyEquivalent(
                candidate.amount,
                candidate.billingPeriod
            )
            val nextDate = com.example.domain.subscription.SubscriptionCalculations.estimateNextPaymentDate(
                candidate.lastTransactionDate,
                candidate.billingPeriod
            )
            val sub = SubscriptionEntity(
                name = candidate.merchant,
                merchantName = candidate.merchant,
                amount = candidate.amount,
                billingPeriod = candidate.billingPeriod,
                monthlyCost = monthlyEquivalent,
                category = "Subscription",
                source = "DETECTED",
                lastConfirmedPaymentDate = candidate.lastTransactionDate,
                lastConfirmedAmount = candidate.amount,
                nextExpectedPaymentDate = nextDate,
                linkedPackageName = candidate.suggestedPackage ?: "",
                status = "ACTIVE"
            )
            repository.insertSubscription(sub)
            _uiState.update {
                it.copy(toastMessage = "Confirmed and tracked '${candidate.merchant}'!")
            }
        }
    }

    fun rejectDetectedSubscription(candidate: com.example.domain.subscription.PotentialSubscription) {
        viewModelScope.launch {
            repository.addRejectedSubscriptionMerchant(candidate.merchant)
            _uiState.update {
                it.copy(toastMessage = "Ignored '${candidate.merchant}'. Will not suggest again.")
            }
        }
    }

    fun recordSubscriptionPayment(
        subscription: SubscriptionEntity,
        amount: Double = subscription.amount,
        paymentMethod: String = "UPI",
        paymentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ) {
        val validation = FinancialValidationEngine.validateTransaction(
            amount = MoneyUtils.of(amount),
            eventType = FinancialEventType.SUBSCRIPTION_PAYMENT,
            merchantOrTitle = subscription.name,
            currentBalance = _uiState.value.overview.currentBalance,
            existingTransactions = _uiState.value.transactions
        )

        when (validation) {
            is ValidationResult.Blocked -> {
                _uiState.update { it.copy(validationBlockedError = validation) }
                return
            }
            is ValidationResult.Warning -> {
                _uiState.update { it.copy(validationWarning = validation) }
            }
            is ValidationResult.Valid -> {}
        }

        viewModelScope.launch {
            repository.recordSubscriptionPayment(
                subscriptionId = subscription.id,
                amount = amount,
                paymentMethod = paymentMethod,
                paymentDate = paymentDate
            )
            _uiState.update {
                it.copy(
                    showConfirmSubscriptionPaymentDialog = null,
                    showSubscriptionDetailDialog = null,
                    toastMessage = "Logged payment of ₹${amount.toInt()} for ${subscription.name} in ledger."
                )
            }
        }
    }

    fun openSubscriptionAppOrFallback(subscription: SubscriptionEntity) {
        val context = getApplication<Application>()
        val known = com.example.domain.subscription.SubscriptionAppResolver.resolveApp(
            subscription.name,
            subscription.linkedPackageName.ifBlank { null }
        )
        val packageName = if (subscription.linkedPackageName.isNotBlank()) {
            subscription.linkedPackageName
        } else {
            known?.packageName ?: ""
        }

        if (packageName.isNotBlank() && com.example.domain.subscription.SubscriptionAppResolver.isAppInstalled(context, packageName)) {
            val launched = com.example.domain.subscription.SubscriptionAppResolver.launchApp(context, packageName)
            if (!launched) {
                _uiState.update {
                    it.copy(appLaunchFallback = com.example.domain.subscription.SubscriptionAppResolver.createFallbackState(subscription))
                }
            }
        } else {
            _uiState.update {
                it.copy(appLaunchFallback = com.example.domain.subscription.SubscriptionAppResolver.createFallbackState(subscription))
            }
        }
    }

    fun setSubscriptionFilter(filter: String) {
        _uiState.update { it.copy(subscriptionFilter = filter) }
    }

    fun setShowSubscriptionDetail(sub: SubscriptionEntity?) {
        _uiState.update { it.copy(showSubscriptionDetailDialog = sub, selectedSubscriptionForDetail = sub) }
    }

    fun setShowConfirmSubscriptionPayment(sub: SubscriptionEntity?) {
        _uiState.update { it.copy(showConfirmSubscriptionPaymentDialog = sub) }
    }

    fun setShowAddSubscription(show: Boolean) = _uiState.update { it.copy(showAddSubscriptionDialog = show) }

    fun updateSubscriptionSettings(settings: com.example.domain.subscription.SubscriptionSettings) {
        repository.subscriptionUsageInsightsEnabled = settings.usageInsightsEnabled
        repository.upcomingSubscriptionAlertsEnabled = settings.upcomingAlertsEnabled // <--- Fix property name here
        repository.unusedSubscriptionAlertsEnabled = settings.unusedAlertsEnabled
        repository.priceChangeAlertsEnabled = settings.priceChangeAlertsEnabled
        repository.subscriptionObservationPeriodDays = settings.observationPeriodDays
        _uiState.update { it.copy(subscriptionSettings = settings) }
    }

    fun updateBudgetLimit(category: String, limit: Double) {
        val validation = FinancialValidationEngine.validateBudget(
            category = category,
            newLimit = MoneyUtils.of(limit),
            existingBudgets = _uiState.value.budgets,
            availableMoney = _uiState.value.overview.currentBalance
        )
        if (validation is ValidationResult.Blocked) {
            _uiState.update { it.copy(validationBlockedError = validation) }
            return
        }
        if (validation is ValidationResult.Warning) {
            _uiState.update { it.copy(validationWarning = validation) }
        }

        viewModelScope.launch {
            repository.updateBudget(category, limit)
            _uiState.update {
                it.copy(
                    showBudgetAdjustDialog = false,
                    toastMessage = "Updated $category limit to ₹${limit.toInt()}"
                )
            }
            evaluateDeviceNotifications()
        }
    }

    fun updateProfile(name: String, email: String) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        if (trimmedName.isBlank() || trimmedEmail.isBlank()) {
            _uiState.update { it.copy(toastMessage = "Name and email cannot be blank") }
            return
        }
        repository.userName = trimmedName
        repository.userEmail = trimmedEmail
        val uid = FirebaseManager.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                FirebaseManager.updateUserProfile(trimmedName, trimmedEmail)
            }
        }
        _uiState.update {
            it.copy(
                userName = trimmedName,
                userEmail = trimmedEmail,
                showEditProfileDialog = false,
                toastMessage = "Profile updated successfully"
            )
        }
    }

    fun updateStartingBalance(newBalance: Double) {
        if (newBalance < 0) {
            _uiState.update { it.copy(toastMessage = "Balance cannot be negative") }
            return
        }
        repository.startingBankBalance = newBalance
        val uid = FirebaseManager.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                FirebaseManager.setInitialBalanceInFirestore(uid, newBalance)
            }
        }
        refreshOverview(newBalance)
        _uiState.update {
            it.copy(
                startingBalance = newBalance,
                showEditBalanceDialog = false,
                toastMessage = "Opening balance updated successfully!"
            )
        }
    }

    fun addBudgetCategory(category: String, limit: Double) {
        val trimmedCategory = category.trim()
        val validation = FinancialValidationEngine.validateBudget(
            category = trimmedCategory,
            newLimit = MoneyUtils.of(limit),
            existingBudgets = _uiState.value.budgets,
            availableMoney = _uiState.value.overview.currentBalance
        )
        if (validation is ValidationResult.Blocked) {
            _uiState.update { it.copy(validationBlockedError = validation) }
            return
        }
        if (validation is ValidationResult.Warning) {
            _uiState.update { it.copy(validationWarning = validation) }
        }

        viewModelScope.launch {
            repository.updateBudget(trimmedCategory, limit)
            _uiState.update {
                it.copy(
                    showAddBudgetDialog = false,
                    toastMessage = "Budget category '$trimmedCategory' added successfully!"
                )
            }
            evaluateDeviceNotifications()
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun dismissValidationError() {
        _uiState.update { it.copy(validationBlockedError = null) }
    }

    fun dismissValidationWarning() {
        _uiState.update { it.copy(validationWarning = null) }
    }

    fun setShowAddTransaction(show: Boolean) = _uiState.update { it.copy(showAddTransactionDialog = show) }
    fun setShowBudgetAdjust(show: Boolean) = _uiState.update { it.copy(showBudgetAdjustDialog = show) }
    fun setShowAddBudget(show: Boolean) = _uiState.update { it.copy(showAddBudgetDialog = show, showAddBudgetCategoryDialog = show) }
    fun setShowAddBudgetCategory(show: Boolean) = setShowAddBudget(show)
    fun setShowNotifications(show: Boolean) = _uiState.update { it.copy(showNotificationsDrawer = show) }
    fun setShowPayBill(bill: BillEntity?) = _uiState.update { it.copy(showPayBillDialog = bill) }
    fun setShowEditProfile(show: Boolean) = _uiState.update { it.copy(showEditProfileDialog = show) }
    fun setShowEditBalance(show: Boolean) = _uiState.update { it.copy(showEditBalanceDialog = show) }
    fun setShowFAQGuide(show: Boolean) = _uiState.update { it.copy(showFAQGuideDialog = show) }
    fun setShowAddGoal(show: Boolean) = _uiState.update { it.copy(showAddGoalDialog = show) }
    fun setShowFundGoal(goal: GoalEntity?) = _uiState.update { it.copy(showFundGoalDialog = goal) }
    fun setShowAddLoan(show: Boolean) = _uiState.update { it.copy(showAddLoanDialog = show) }
    fun setShowTaxAnalyzer(show: Boolean) = _uiState.update { it.copy(showTaxAnalyzerDialog = show) }
    fun setShowAnomalyGuardian(show: Boolean) = _uiState.update { it.copy(showAnomalyGuardianDialog = show) }
    fun setShowUniversalSearch(show: Boolean) = _uiState.update { it.copy(showUniversalSearch = show) }
    fun setMarketFocusedSymbol(symbol: String?) = _uiState.update { it.copy(marketFocusedSymbol = symbol) }

    fun addGoal(goal: GoalEntity) {
        val validation = FinancialValidationEngine.validateGoal(
            title = goal.title,
            targetAmount = MoneyUtils.of(goal.targetAmount),
            monthlyTarget = MoneyUtils.of(goal.monthlyTarget),
            availableCashAfterBudgets = _uiState.value.overview.availableAfterPlannedBudgets
        )
        if (validation is ValidationResult.Blocked) {
            _uiState.update { it.copy(validationBlockedError = validation) }
            return
        }
        if (validation is ValidationResult.Warning) {
            _uiState.update { it.copy(validationWarning = validation) }
        }

        viewModelScope.launch {
            repository.addGoal(goal)
            _uiState.update {
                it.copy(
                    showAddGoalDialog = false,
                    toastMessage = "New goal '${goal.title}' created successfully!"
                )
            }
            evaluateDeviceNotifications()
        }
    }

    fun addLoan(title: String, lender: String, principal: Double, interestRate: Double, tenureMonths: Int) {
        if (title.isBlank() || principal <= 0) {
            _uiState.update { it.copy(toastMessage = "Please enter a valid loan title and positive amount.") }
            return
        }
        viewModelScope.launch {
            val tenure = if (tenureMonths > 0) tenureMonths else 12
            val monthlyRate = (interestRate / 100.0) / 12.0
            val emi = if (monthlyRate > 0) {
                val factor = Math.pow(1.0 + monthlyRate, tenure.toDouble())
                (principal * monthlyRate * factor) / (factor - 1.0)
            } else {
                principal / tenure
            }
            val dueDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
                Date(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
            )
            val loan = LoanEntity(
                title = title,
                lender = lender.ifBlank { "Bank" },
                principal = principal,
                remaining = principal,
                interestRate = interestRate,
                monthlyEmi = Math.round(emi).toDouble(),
                nextDueDate = dueDate,
                tenureMonthsLeft = tenure
            )
            repository.insertLoan(loan)
            repository.addNotification(
                NotificationEntity(
                    title = "New Loan Added: $title",
                    message = "Added ₹${principal.toInt()} liability with ${loan.lender}. EMI is ₹${loan.monthlyEmi.toInt()}/mo.",
                    type = "INFO"
                )
            )
            _uiState.update {
                it.copy(
                    showAddLoanDialog = false,
                    toastMessage = "Added loan '$title' (Monthly EMI: ₹${loan.monthlyEmi.toInt()})"
                )
            }
        }
    }

    fun payLoanEmi(loan: LoanEntity) {
        val emiToPay = if (loan.remaining < loan.monthlyEmi) loan.remaining else loan.monthlyEmi
        if (emiToPay <= 0) return

        val validation = FinancialValidationEngine.validateLoanPayment(
            loan = loan,
            paymentAmount = MoneyUtils.of(emiToPay),
            availableBalance = _uiState.value.overview.currentBalance
        )
        if (validation is ValidationResult.Blocked) {
            _uiState.update { it.copy(validationBlockedError = validation) }
            return
        }

        viewModelScope.launch {
            repository.recordEmiPayment(loan.id, emiToPay)
            repository.addNotification(
                NotificationEntity(
                    title = "Loan EMI Paid: ${loan.title}",
                    message = "Payment of ₹${emiToPay.toInt()} recorded for ${loan.title}.",
                    type = "BILL"
                )
            )
            _uiState.update {
                it.copy(toastMessage = "Paid ₹${emiToPay.toInt()} EMI for ${loan.title}!")
            }
            evaluateDeviceNotifications()
        }
    }

    fun deleteLoan(loanId: Int) {
        viewModelScope.launch {
            repository.deleteLoan(loanId)
            _uiState.update { it.copy(toastMessage = "Loan record removed.") }
        }
    }

    fun dismissNotification(key: String, entityId: Int? = null) {
        _uiState.update { current ->
            current.copy(
                dismissedSessionNotificationIds = current.dismissedSessionNotificationIds + key
            )
        }
        if (entityId != null) {
            viewModelScope.launch {
                repository.deleteNotification(entityId)
            }
        }
    }

    fun resetDismissedNotifications() {
        _uiState.update { it.copy(dismissedSessionNotificationIds = emptySet()) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
            _uiState.update { it.copy(toastMessage = "All notifications marked as read.") }
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
            _uiState.update { it.copy(toastMessage = "Notifications cleared.") }
        }
    }

    fun getCsvData(): String {
        val txns = _uiState.value.transactions
        val sb = StringBuilder()
        sb.append("ID,Date,Title,Type,Category,Amount,PaymentMethod,Merchant\n")
        txns.forEach { t ->
            val safeTitle = t.title.replace("\"", "\"\"")
            val safeMerchant = t.merchant.replace("\"", "\"\"")
            sb.append("${t.id},${t.date},\"$safeTitle\",${t.type},${t.category},${t.amount},${t.paymentMethod},\"$safeMerchant\"\n")
        }
        return sb.toString()
    }

    fun evaluateDeviceNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = WealthWiseDatabase.getDatabase(getApplication())
                WealthWiseNotificationEvaluator.evaluateFinancialAlerts(
                    getApplication(),
                    db,
                    repository
                )
            } catch (e: Exception) {
                Log.e("WealthWiseVM", "Error evaluating device notifications", e)
            }
        }
    }

    fun refreshSystemAccessStatuses() {
        val app = getApplication<Application>()
        val listenerStatus = com.example.permission.PermissionManager.checkNotificationListenerAccess(app)
        val notifStatus = com.example.permission.PermissionManager.checkNotificationPermission(app)
        val usageStatus = com.example.permission.PermissionManager.checkUsageAccess(app)
        val internetStatus = com.example.permission.PermissionManager.checkInternetAccess(app)

        val previousUsageGranted = _uiState.value.usageAccessStatus.isGranted
        val previousListenerGranted = _uiState.value.notificationAccessStatus.isGranted
        val disabledInSystem = !notifStatus.isGranted

        val updatedUsage = com.example.permission.PermissionManager.querySubscriptionUsage(
            app,
            _uiState.value.subscriptions
        )

        _uiState.update { current ->
            current.copy(
                notificationAccessStatus = listenerStatus,
                postNotificationStatus = notifStatus,
                usageAccessStatus = usageStatus,
                internetStatus = internetStatus,
                isSystemNotificationDisabled = disabledInSystem,
                subscriptionUsageMap = updatedUsage
            )
        }

        if (!previousListenerGranted && listenerStatus == com.example.permission.AccessStatus.CONNECTED) {
            _uiState.update { it.copy(toastMessage = "✅ Notification Access Connected") }
        } else if (!previousUsageGranted && usageStatus == com.example.permission.AccessStatus.CONNECTED) {
            _uiState.update { it.copy(toastMessage = "✅ Usage Access Connected • Subscription audit updated") }
        }
    }

    fun checkSystemNotificationStatus() {
        refreshSystemAccessStatuses()
    }

    fun setShowNotificationPermissionRationale(show: Boolean) {
        _uiState.update { it.copy(showPostNotificationRationale = show) }
    }

    fun togglePushNotifications() {
        val currentEnabled = _uiState.value.pushNotificationsEnabled
        val app = getApplication<Application>()
        val permissionStatus = com.example.permission.PermissionManager.checkNotificationPermission(app)

        if (!currentEnabled) {
            if (!permissionStatus.isGranted) {
                _uiState.update { it.copy(showPostNotificationRationale = true) }
            } else {
                repository.pushNotificationsEnabled = true
                _uiState.update { it.copy(pushNotificationsEnabled = true) }
                evaluateDeviceNotifications()
            }
        } else {
            repository.pushNotificationsEnabled = false
            _uiState.update { it.copy(pushNotificationsEnabled = false) }
        }
    }

    fun onPostNotificationRationaleConfirmed() {
        _uiState.update { it.copy(showPostNotificationRationale = false) }
    }

    fun dismissPostNotificationRationale() {
        _uiState.update { it.copy(showPostNotificationRationale = false) }
    }

    fun toggleAccountAggregator() {
        val currentEnabled = _uiState.value.accountAggregatorSyncEnabled
        val app = getApplication<Application>()
        val accessStatus = com.example.permission.PermissionManager.checkNotificationListenerAccess(app)

        if (!currentEnabled) {
            if (accessStatus != com.example.permission.AccessStatus.CONNECTED) {
                _uiState.update { it.copy(showNotificationAccessRationale = true) }
            } else {
                repository.accountAggregatorSyncEnabled = true
                _uiState.update { it.copy(accountAggregatorSyncEnabled = true) }
                evaluateDeviceNotifications()
            }
        } else {
            repository.accountAggregatorSyncEnabled = false
            _uiState.update { it.copy(accountAggregatorSyncEnabled = false) }
        }
    }

    fun onNotificationAccessRationaleConfirmed() {
        _uiState.update { it.copy(showNotificationAccessRationale = false) }
        val ok = com.example.permission.PermissionManager.openNotificationAccessSettings(getApplication())
        if (!ok) {
            _uiState.update {
                it.copy(toastMessage = "We couldn't open settings. Please enable Notification Access manually.")
            }
        }
    }

    fun dismissNotificationAccessRationale() {
        _uiState.update { it.copy(showNotificationAccessRationale = false) }
    }

    fun requestSubscriptionUsageInsights() {
        val app = getApplication<Application>()
        val accessStatus = com.example.permission.PermissionManager.checkUsageAccess(app)

        if (accessStatus == com.example.permission.AccessStatus.CONNECTED) {
            val usageMap = com.example.permission.PermissionManager.querySubscriptionUsage(
                app,
                _uiState.value.subscriptions
            )
            _uiState.update {
                it.copy(
                    subscriptionUsageMap = usageMap,
                    toastMessage = "Subscription usage insights updated"
                )
            }
        } else {
            _uiState.update { it.copy(showUsageAccessRationale = true) }
        }
    }

    fun onUsageAccessRationaleConfirmed() {
        _uiState.update { it.copy(showUsageAccessRationale = false) }
        val ok = com.example.permission.PermissionManager.openUsageAccessSettings(getApplication())
        if (!ok) {
            _uiState.update {
                it.copy(toastMessage = "We couldn't open settings. Please enable Usage Access manually.")
            }
        }
    }

    fun dismissUsageAccessRationale() {
        _uiState.update { it.copy(showUsageAccessRationale = false) }
    }

    fun openSubscriptionApp(subscription: SubscriptionEntity) {
        openSubscriptionAppOrFallback(subscription)
    }

    fun dismissAppLaunchFallback() {
        _uiState.update { it.copy(appLaunchFallback = null) }
    }

    fun dismissRationaleDialogs() {
        _uiState.update {
            it.copy(
                showNotificationAccessRationale = false,
                showPostNotificationRationale = false,
                showUsageAccessRationale = false
            )
        }
    }

    fun toggleBudgetAlerts() {
        val newVal = !_uiState.value.budgetAlertsEnabled
        repository.budgetAlertsEnabled = newVal
        _uiState.update { it.copy(budgetAlertsEnabled = newVal) }
        if (newVal) evaluateDeviceNotifications()
    }

    fun toggleBillEmiAlerts() {
        val newVal = !_uiState.value.billEmiAlertsEnabled
        repository.billEmiAlertsEnabled = newVal
        _uiState.update { it.copy(billEmiAlertsEnabled = newVal) }
        if (newVal) evaluateDeviceNotifications()
    }

    fun toggleGoalAlerts() {
        val newVal = !_uiState.value.goalAlertsEnabled
        repository.goalAlertsEnabled = newVal
        _uiState.update { it.copy(goalAlertsEnabled = newVal) }
        if (newVal) evaluateDeviceNotifications()
    }

    fun toggleSyncAlerts() {
        val newVal = !_uiState.value.syncAlertsEnabled
        repository.syncAlertsEnabled = newVal
        _uiState.update { it.copy(syncAlertsEnabled = newVal) }
        if (newVal) evaluateDeviceNotifications()
    }

    fun setBillReminderDays(days: Int) {
        val safeDays = days.coerceIn(1, 3)
        repository.billReminderDaysAhead = safeDays
        _uiState.update { it.copy(billReminderDaysAhead = safeDays) }
        evaluateDeviceNotifications()
    }

    fun triggerTestNotification() {
        WealthWiseNotificationManager.postTestNotification(getApplication())
        _uiState.update { it.copy(toastMessage = "Sent test alert to Android notification shade!") }
    }

    fun evaluateDeviceNotificationsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = WealthWiseDatabase.getDatabase(getApplication())
            val alerts = WealthWiseNotificationEvaluator.evaluateFinancialAlerts(
                getApplication(),
                db,
                repository
            )
            withContext(Dispatchers.Main) {
                val msg = if (alerts.isNotEmpty()) {
                    "Dispatched ${alerts.size} financial alert(s) to notification shade!"
                } else {
                    "Financial check complete: No new alerts required at this time."
                }
                _uiState.update { it.copy(toastMessage = msg) }
            }
        }
    }

    fun toggleSmsParsing() {
        val newVal = !_uiState.value.smsParsingEnabled
        repository.smsParsingEnabled = newVal
        _uiState.update { it.copy(smsParsingEnabled = newVal) }
    }
}