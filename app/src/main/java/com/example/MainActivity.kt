/**
 * WealthWise - Smart Personal Finance Application
 *
 * Authors / Development Team:
 * - Purushotham
 * - Surya
 * - Srinivas
 * - Karthik
 */

package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.FirebaseManager
import com.example.notification.WealthWiseNotificationManager
import com.example.notification.WealthWiseNotificationWorker
import com.example.ui.activity.ActivityLedgerScreen
import com.example.ui.auth.EmailVerificationScreen
import com.example.ui.auth.EnterCurrentBalanceScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.RegisterScreen
import com.example.ui.budget.BudgetScreen
import com.example.ui.components.WealthWiseBottomNav
import com.example.ui.components.WealthWiseDrawerContent
import com.example.ui.components.WealthWiseTopBar
import com.example.ui.dashboard.HomeScreen
import com.example.ui.dialogs.*
import com.example.ui.goals.GoalsScreen
import com.example.ui.investments.InvestmentsScreen
import com.example.ui.loans.LoansDebtsScreen
import com.example.ui.market.MarketScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.search.SearchNavigationTarget
import com.example.ui.search.UniversalSearchDialog
import com.example.ui.subscriptions.AddSubscriptionDialog
import com.example.ui.subscriptions.SubscriptionKillerScreen
import com.example.ui.subscriptions.SubscriptionScreen
import com.example.ui.theme.LightBg
import com.example.ui.theme.Slate950
import com.example.ui.theme.WealthWiseTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.payment.RazorpayPaymentBridge
import com.example.ui.viewmodel.WealthWiseViewModel
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private val viewModel: WealthWiseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Preload Razorpay checkout resources for responsive payments
        try {
            Checkout.preload(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("WealthWiseRazorpay", "Razorpay preload failed: ${e.message}", e)
        }

        // Initialize local notification channel and periodic WorkManager safely
        try {
            WealthWiseNotificationManager.createNotificationChannel(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("WealthWiseNotifications", "Notification channel creation failed: ${e.message}", e)
        }

        try {
            WealthWiseNotificationWorker.schedulePeriodicWork(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("WealthWiseNotifications", "Periodic work scheduling failed: ${e.message}", e)
        }

        // Initialize live Firebase project with inline FirebaseOptions safely
        try {
            FirebaseManager.initialize(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("WealthWiseFirebase", "Firebase initialization failed: ${e.message}", e)
        }

        // Handle navigation if launched from notification shade safely
        try {
            handleNotificationNavigation(intent)
        } catch (e: Exception) {
            android.util.Log.e("WealthWiseNavigation", "Notification navigation failed: ${e.message}", e)
        }

        @OptIn(ExperimentalMaterial3Api::class)
        setContent {
            WealthWiseTheme {
                val state by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()

                // Permission launcher for Android 13+ POST_NOTIFICATIONS
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    viewModel.refreshSystemAccessStatuses()
                    if (isGranted) {
                        viewModel.evaluateDeviceNotificationsNow()
                    }
                }

                // Refresh permission and access states when user returns to app from system Settings
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshSystemAccessStatuses()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Display toast messages via Snackbar
                LaunchedEffect(state.toastMessage) {
                    state.toastMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearToast()
                    }
                }

                val isAuthScreen = state.currentScreen == AppScreen.LOGIN ||
                        state.currentScreen == AppScreen.REGISTER ||
                        state.currentScreen == AppScreen.EMAIL_VERIFICATION ||
                        state.currentScreen == AppScreen.ENTER_BALANCE

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !isAuthScreen,
                    drawerContent = {
                        WealthWiseDrawerContent(
                            currentScreen = state.currentScreen,
                            userName = state.userName,
                            userEmail = state.userEmail,
                            onNavigate = { screen -> viewModel.navigateTo(screen) },
                            onNavigateWithFilter = { _, filter ->
                                viewModel.navigateToActivityWithFilter(filter)
                            },
                            onOpenBudget = { viewModel.setShowBudgetAdjust(true) },
                            onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
                        )
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.White,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            if (!isAuthScreen &&
                                state.currentScreen != AppScreen.HOME &&
                                state.currentScreen != AppScreen.SUBSCRIPTION_KILLER &&
                                state.currentScreen != AppScreen.UPI_PAYMENT
                            ) {
                                WealthWiseTopBar(
                                    userName = state.userName,
                                    notificationCount = state.notifications.count { !it.isRead },
                                    onOpenDrawer = {
                                        coroutineScope.launch { drawerState.open() }
                                    },
                                    onOpenNotifications = { viewModel.setShowNotifications(true) },
                                    onOpenProfile = { viewModel.navigateTo(AppScreen.MORE) },
                                    onLogout = { viewModel.logout() }
                                )
                            }
                        },
                        bottomBar = {
                            if (!isAuthScreen &&
                                state.currentScreen != AppScreen.SUBSCRIPTION_KILLER &&
                                state.currentScreen != AppScreen.UPI_PAYMENT
                            ) {
                                WealthWiseBottomNav(
                                    currentScreen = state.currentScreen,
                                    onNavigate = { viewModel.navigateTo(it) }
                                )
                            }
                        },
                        floatingActionButtonPosition = FabPosition.Center,
                        floatingActionButton = {
                            if (!isAuthScreen && state.currentScreen == AppScreen.HOME) {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        viewModel.navigateTo(AppScreen.UPI_PAYMENT)
                                    },
                                    containerColor = Slate950,
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(28.dp),
                                    elevation = FloatingActionButtonDefaults.elevation(
                                        defaultElevation = 4.dp,
                                        pressedElevation = 2.dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Pay via UPI",
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Pay",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        // Responsive Back Handling: allows returning to LOGIN from REGISTER or VERIFY
                        BackHandler(enabled = state.currentScreen != AppScreen.HOME && state.currentScreen != AppScreen.LOGIN) {
                            if (isAuthScreen) {
                                viewModel.navigateTo(AppScreen.LOGIN)
                            } else if (viewModel.canNavigateBack()) {
                                viewModel.navigateBack()
                            } else {
                                viewModel.navigateTo(AppScreen.HOME)
                            }
                        }

                        var isPullRefreshing by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            PullToRefreshBox(
                                isRefreshing = isPullRefreshing,
                                onRefresh = {
                                    if (!isAuthScreen) {
                                        isPullRefreshing = true
                                        coroutineScope.launch {
                                            viewModel.refreshSystemAccessStatuses()
                                            viewModel.evaluateDeviceNotificationsNow()
                                            kotlinx.coroutines.delay(600)
                                            isPullRefreshing = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                AnimatedContent(
                                    targetState = state.currentScreen,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "ScreenTransition"
                                ) { screen ->
                                when (screen) {
                                    AppScreen.LOGIN -> LoginScreen(
                                        onLogin = { email, pass -> viewModel.login(email, pass) },
                                        onNavigateToRegister = {
                                            viewModel.clearAuthError()
                                            viewModel.navigateTo(AppScreen.REGISTER)
                                        },
                                        onNavigateToVerification = {
                                            viewModel.navigateTo(AppScreen.EMAIL_VERIFICATION)
                                        },
                                        onNavigateToHome = {
                                            viewModel.navigateTo(AppScreen.HOME)
                                        },
                                        onNavigateToSetBalance = {
                                            viewModel.navigateTo(AppScreen.ENTER_BALANCE)
                                        },
                                        errorMessage = state.authErrorMessage,
                                        onClearError = { viewModel.clearAuthError() }
                                    )

                                    AppScreen.REGISTER -> RegisterScreen(
                                        onRegister = { name, email, pass -> viewModel.register(name, email, pass) },
                                        onNavigateToLogin = {
                                            viewModel.clearAuthError()
                                            viewModel.navigateTo(AppScreen.LOGIN)
                                        },
                                        onNavigateToVerification = {
                                            viewModel.navigateTo(AppScreen.EMAIL_VERIFICATION)
                                        },
                                        errorMessage = state.authErrorMessage,
                                        onClearError = { viewModel.clearAuthError() }
                                    )

                                    AppScreen.EMAIL_VERIFICATION -> EmailVerificationScreen(
                                        userEmail = state.userEmail,
                                        onVerified = {
                                            viewModel.checkEmailVerificationStatus()
                                        },
                                        onResendVerification = {
                                            viewModel.resendVerificationEmail()
                                        },
                                        onBackToLogin = {
                                            viewModel.backToLoginFromVerification()
                                        },
                                        onDebugSkip = {
                                            viewModel.bypassEmailVerificationForDebug()
                                        },
                                        errorMessage = state.authErrorMessage,
                                        isLoading = state.isLoading
                                    )

                                    AppScreen.ENTER_BALANCE -> EnterCurrentBalanceScreen(
                                        onSubmitBalance = { balance -> viewModel.submitStartingBalance(balance) }
                                    )

                                    AppScreen.HOME -> HomeScreen(
                                        state = state,
                                        onAddIncome = { viewModel.setShowAddTransaction(true) },
                                        onAddExpense = { viewModel.setShowAddTransaction(true) },
                                        onViewReports = { viewModel.navigateTo(AppScreen.ACTIVITY) },
                                        onAdjustBudget = { viewModel.setShowBudgetAdjust(true) },
                                        onReviewSubscriptions = { viewModel.navigateTo(AppScreen.SUBSCRIPTION_KILLER) },
                                        onCancelSubscription = { sub -> viewModel.cancelSubscription(sub) },
                                        onPayBill = { bill -> viewModel.setShowPayBill(bill) },
                                        onFundGoal = { goal -> viewModel.fundGoal(goal.id, 5000.0) },
                                        onAddGoal = { viewModel.setShowAddGoal(true) },
                                        onViewFullLedger = { viewModel.navigateTo(AppScreen.ACTIVITY) },
                                        onDismissNotification = { key, entityId -> viewModel.dismissNotification(key, entityId) },
                                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                        onOpenSettings = { viewModel.navigateTo(AppScreen.MORE) },
                                        onOpenBudgets = { viewModel.navigateTo(AppScreen.BUDGET) },
                                        onOpenGoals = { viewModel.navigateTo(AppScreen.GOALS) },
                                        onOpenUniversalSearch = { viewModel.setShowUniversalSearch(true) },
                                        onSyncNow = {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("All accounts and notifications are up to date.")
                                            }
                                        },
                                        onOpenTotalBalance = { viewModel.navigateTo(AppScreen.ACTIVITY) },
                                        onOpenIncome = { viewModel.navigateToActivityWithFilter("INCOME") },
                                        onOpenExpenses = { viewModel.navigateToActivityWithFilter("EXPENSE") },
                                        onOpenSavings = { viewModel.navigateTo(AppScreen.GOALS) },
                                        onOpenInvestments = { viewModel.navigateTo(AppScreen.INVESTMENTS) },
                                        onOpenLoans = { viewModel.navigateTo(AppScreen.LOANS_DEBTS) }
                                    )

                                    AppScreen.ACTIVITY -> ActivityLedgerScreen(
                                        transactions = state.transactions,
                                        initialFilter = state.activityFilter,
                                        currentBalance = state.overview.currentBalance.toDouble(),
                                        totalIncome = state.totalInflow,
                                        totalExpenses = state.totalOutflow,
                                        onBack = { viewModel.navigateBack() }
                                    )

                                    AppScreen.AI_TOOLS -> MarketScreen(
                                        focusedSymbol = state.marketFocusedSymbol,
                                        onBack = { viewModel.navigateBack() }
                                    )

                                    AppScreen.GOALS -> GoalsScreen(
                                        goals = state.goals,
                                        transactions = state.transactions,
                                        safeToSpend = state.safeToSpend,
                                        onFundGoal = { goal, amount -> viewModel.fundGoal(goal.id, amount) },
                                        onOpenFundDialog = { goal -> viewModel.setShowFundGoal(goal) },
                                        onAddGoal = { viewModel.setShowAddGoal(true) },
                                        onBack = { viewModel.navigateBack() }
                                    )

                                    AppScreen.LOANS_DEBTS -> LoansDebtsScreen(
                                        loansRemaining = state.loansRemaining,
                                        loans = state.loans,
                                        transactions = state.transactions,
                                        safeToSpend = state.safeToSpend,
                                        onAddLoanClick = { viewModel.setShowAddLoan(true) },
                                        onPayEmi = { loan -> viewModel.payLoanEmi(loan) },
                                        onDeleteLoan = { id -> viewModel.deleteLoan(id) },
                                        onBack = { viewModel.navigateBack() }
                                    )

                                    AppScreen.BUDGET -> BudgetScreen(
                                        budgets = state.budgets,
                                        transactions = state.transactions,
                                        availableFunds = state.availableBudgetFunds,
                                        safeToSpend = state.safeToSpend,
                                        onAdjustBudget = { viewModel.setShowBudgetAdjust(true) },
                                        onAddBudgetCategory = { viewModel.setShowAddBudgetCategory(true) },
                                        onBack = { viewModel.navigateBack() }
                                    )

                                    AppScreen.INVESTMENTS -> InvestmentsScreen(
                                        investmentsTotal = state.investmentsTotal,
                                        transactions = state.transactions.filter { it.category.equals("Investment", ignoreCase = true) },
                                        availableCashBalance = state.overview.currentBalance.toDouble(),
                                        onBack = { viewModel.navigateBack() }
                                    )

                                    AppScreen.SUBSCRIPTION_KILLER -> SubscriptionScreen(
                                        onBack = { viewModel.navigateBack() }
                                    )

                                    AppScreen.UPI_PAYMENT -> com.example.ui.payment.UpiPaymentScreen(
                                        onNavigateBack = { viewModel.navigateBack() }
                                    )

                                    AppScreen.MORE, AppScreen.SETTINGS -> ProfileScreen(
                                        state = state,
                                        onEditProfile = { viewModel.setShowEditProfile(true) },
                                        onEditBalance = { viewModel.setShowEditBalance(true) },
                                        onOpenBillReminders = { viewModel.navigateTo(AppScreen.HOME) },
                                        onOpenGuide = { viewModel.setShowFAQGuide(true) },
                                        onTogglePushNotifications = { viewModel.togglePushNotifications() },
                                        onToggleBudgetAlerts = { viewModel.toggleBudgetAlerts() },
                                        onToggleBillEmiAlerts = { viewModel.toggleBillEmiAlerts() },
                                        onToggleGoalAlerts = { viewModel.toggleGoalAlerts() },
                                        onToggleSyncAlerts = { viewModel.toggleSyncAlerts() },
                                        onTriggerTestNotification = { viewModel.triggerTestNotification() },
                                        onEvaluateAlertsNow = { viewModel.evaluateDeviceNotificationsNow() },
                                        onToggleAccountAggregator = { viewModel.toggleAccountAggregator() },
                                        onRequestNotificationAccess = { viewModel.toggleAccountAggregator() },
                                        onRequestNotificationPermission = { viewModel.togglePushNotifications() },
                                        onRequestUsageAccess = { viewModel.requestSubscriptionUsageInsights() },
                                        onLogout = { viewModel.logout() }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // Modals, Sheets & Interactive Dialogs
                // ==========================================
                if (state.showUniversalSearch) {
                    UniversalSearchDialog(
                        state = state,
                        onDismiss = { viewModel.setShowUniversalSearch(false) },
                        onNavigateTarget = { target ->
                            viewModel.setShowUniversalSearch(false)
                            when (target) {
                                is SearchNavigationTarget.Activity -> viewModel.navigateTo(AppScreen.ACTIVITY)
                                is SearchNavigationTarget.ActivityFiltered -> viewModel.navigateToActivityWithFilter(target.filter)
                                is SearchNavigationTarget.Goals -> viewModel.navigateTo(AppScreen.GOALS)
                                is SearchNavigationTarget.Budgets -> viewModel.navigateTo(AppScreen.BUDGET)
                                is SearchNavigationTarget.Investments -> viewModel.navigateTo(AppScreen.INVESTMENTS)
                                is SearchNavigationTarget.Loans -> viewModel.navigateTo(AppScreen.LOANS_DEBTS)
                                is SearchNavigationTarget.Subscriptions -> viewModel.navigateTo(AppScreen.SUBSCRIPTION_KILLER)
                                is SearchNavigationTarget.Market -> {
                                    viewModel.setMarketFocusedSymbol(target.focusedSymbol)
                                    viewModel.navigateTo(AppScreen.AI_TOOLS)
                                }
                                is SearchNavigationTarget.Settings,
                                is SearchNavigationTarget.Profile -> viewModel.navigateTo(AppScreen.MORE)
                            }
                        },
                        onSelectTransaction = { _ ->
                            viewModel.setShowUniversalSearch(false)
                            viewModel.navigateTo(AppScreen.ACTIVITY)
                        }
                    )
                }

                if (state.showAddTransactionDialog) {
                    AddTransactionDialog(
                        onDismiss = { viewModel.setShowAddTransaction(false) },
                        onConfirm = { title, amount, type, category, paymentMethod ->
                            viewModel.addManualTransaction(title, amount, type, category, paymentMethod)
                        },
                        onOpenSimulate = {
                            viewModel.setShowAddTransaction(false)
                            viewModel.setShowSimulateTxn(true)
                        }
                    )
                }

                if (state.showBudgetAdjustDialog) {
                    BudgetAdjustDialog(
                        budgets = state.budgets,
                        availableAmount = state.availableBudgetFunds,
                        onSaveLimit = { category, limit -> viewModel.updateBudgetLimit(category, limit) },
                        onDismiss = { viewModel.setShowBudgetAdjust(false) }
                    )
                }

                if (state.showAddBudgetCategoryDialog) {
                    AddBudgetCategoryDialog(
                        existingBudgets = state.budgets,
                        availableAmount = state.availableBudgetFunds,
                        onDismiss = { viewModel.setShowAddBudgetCategory(false) },
                        onSave = { category, limit ->
                            viewModel.addBudgetCategory(category, limit)
                        }
                    )
                }

                if (state.showNotificationsDrawer) {
                    NotificationsDialog(
                        notifications = state.notifications,
                        onMarkAllRead = { viewModel.markAllNotificationsRead() },
                        onClearAll = { viewModel.clearNotifications() },
                        onDismiss = { viewModel.setShowNotifications(false) }
                    )
                }

                state.showPayBillDialog?.let { bill ->
                    PayBillDialog(
                        bill = bill,
                        onConfirmPay = { billToPay -> viewModel.payBill(billToPay) },
                        onDismiss = { viewModel.setShowPayBill(null) }
                    )
                }

                if (state.showEditProfileDialog) {
                    EditProfileDialog(
                        initialName = state.userName,
                        initialEmail = state.userEmail,
                        onSave = { name, email -> viewModel.updateProfile(name, email) },
                        onDismiss = { viewModel.setShowEditProfile(false) }
                    )
                }

                if (state.showEditBalanceDialog) {
                    EditBalanceDialog(
                        currentOpening = state.startingBalance,
                        currentNet = state.overview.currentBalance.toDouble(),
                        totalInflow = state.totalInflow,
                        totalOutflow = state.totalOutflow,
                        onSaveBalance = { newOpening ->
                            viewModel.updateStartingBalance(newOpening)
                            viewModel.setShowEditBalance(false)
                        },
                        onDismiss = { viewModel.setShowEditBalance(false) }
                    )
                }

                state.validationBlockedError?.let { blocked ->
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissValidationError() },
                        title = {
                            Text(blocked.title, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(blocked.message, style = MaterialTheme.typography.bodyMedium)
                                if (blocked.reason.isNotBlank()) {
                                    Text(
                                        "Reason: ${blocked.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.dismissValidationError() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Understood")
                            }
                        }
                    )
                }

                state.validationWarning?.let { warning ->
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissValidationWarning() },
                        title = {
                            Text(warning.title, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(warning.message, style = MaterialTheme.typography.bodyMedium)
                                if (warning.actionSuggestion.isNotBlank()) {
                                    Text(
                                        warning.actionSuggestion,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.dismissValidationWarning() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                            ) {
                                Text("Acknowledge")
                            }
                        }
                    )
                }

                if (state.showFAQGuideDialog) {
                    SimpleGuideDialog(
                        onDismiss = { viewModel.setShowFAQGuide(false) }
                    )
                }

                if (state.showAddGoalDialog) {
                    AddGoalDialog(
                        availableFunds = state.availableBudgetFunds,
                        totalPlannedBudget = state.totalBudget,
                        onDismiss = { viewModel.setShowAddGoal(false) },
                        onSave = { goal ->
                            viewModel.addGoal(goal)
                        }
                    )
                }

                state.showFundGoalDialog?.let { goal ->
                    FundGoalDialog(
                        goal = goal,
                        availableBalance = state.overview.availableBalance.toDouble(),
                        onDismiss = { viewModel.setShowFundGoal(null) },
                        onFund = { amount ->
                            viewModel.fundGoal(goal.id, amount)
                        }
                    )
                }

                if (state.showAddLoanDialog) {
                    AddLoanDialog(
                        onDismiss = { viewModel.setShowAddLoan(false) },
                        onSaveLoan = { title, lender, principal, rate, tenure ->
                            viewModel.addLoan(title, lender, principal, rate, tenure)
                        }
                    )
                }

                if (state.showAddSubscriptionDialog) {
                    AddSubscriptionDialog(
                        onDismiss = { viewModel.setShowAddSubscription(false) },
                        onSaveSubscription = { name, amount, billingPeriod, category, linkedPackage, nextDate ->
                            viewModel.addSubscription(
                                name = name,
                                amount = amount,
                                billingPeriod = billingPeriod,
                                category = category,
                                linkedPackageName = linkedPackage,
                                nextExpectedPaymentDate = nextDate
                            )
                        }
                    )
                }

                if (state.showTaxAnalyzerDialog) {
                    Tax80CAnalyzerDialog(
                        transactions = state.transactions,
                        totalInflow = state.totalInflow,
                        onDismiss = { viewModel.setShowTaxAnalyzer(false) }
                    )
                }

                if (state.showAnomalyGuardianDialog) {
                    SpendAnomalyGuardianDialog(
                        transactions = state.transactions,
                        onDismiss = { viewModel.setShowAnomalyGuardian(false) }
                    )
                }

                // --- Privacy & Permission Rationale Dialogs ---
                if (state.showNotificationAccessRationale) {
                    NotificationAccessRationaleDialog(
                        onConfirm = { viewModel.onNotificationAccessRationaleConfirmed() },
                        onDismiss = { viewModel.dismissNotificationAccessRationale() }
                    )
                }

                if (state.showPostNotificationRationale) {
                    PostNotificationRationaleDialog(
                        onConfirm = {
                            viewModel.onPostNotificationRationaleConfirmed()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onDismiss = { viewModel.dismissPostNotificationRationale() }
                    )
                }

                if (state.showUsageAccessRationale) {
                    com.example.ui.dialogs.UsageAccessRationaleDialog(
                        onConfirm = { viewModel.onUsageAccessRationaleConfirmed() },
                        onDismiss = { viewModel.dismissUsageAccessRationale() }
                    )
                }

                state.appLaunchFallback?.let { fallback ->
                    val context = androidx.compose.ui.platform.LocalContext.current
                    AppLaunchFallbackDialog(
                        state = fallback,
                        onOpenPlayStore = {
                            com.example.permission.PermissionManager.openPlayStore(context, fallback.packageName)
                            viewModel.dismissAppLaunchFallback()
                        },
                        onOpenWebsite = {
                            fallback.webUrl?.let { url ->
                                com.example.permission.PermissionManager.launchUrl(context, url)
                            }
                            viewModel.dismissAppLaunchFallback()
                        },
                        onDismiss = { viewModel.dismissAppLaunchFallback() }
                    )
                }
            }
        }
    }
}

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemAccessStatuses()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationNavigation(intent)
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        RazorpayPaymentBridge.onPaymentSuccess(razorpayPaymentId, paymentData)
    }

    override fun onPaymentError(errorCode: Int, errorDescription: String?, paymentData: PaymentData?) {
        RazorpayPaymentBridge.onPaymentError(errorCode, errorDescription, paymentData)
    }

    private fun handleNotificationNavigation(intent: Intent?) {
        val targetScreenName = intent?.getStringExtra(WealthWiseNotificationManager.EXTRA_NAV_SCREEN) ?: return
        try {
            val screen = AppScreen.valueOf(targetScreenName)
            viewModel.navigateTo(screen)
        } catch (e: Exception) {
            // fallback gracefully
        }
    }
}