package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.local.AppDatabaseProvider
import com.example.data.local.SubscriptionEntity
import com.example.data.repository.SubscriptionRepository
import com.example.data.repository.WealthWiseRepository
import com.example.domain.subscription.PotentialSubscription
import com.example.domain.subscription.SubscriptionAppResolver
import com.example.domain.subscription.SubscriptionPriceChange
import com.example.domain.subscription.SubscriptionSummary
import com.example.domain.subscription.SubscriptionUiModel
import com.example.domain.subscription.SubscriptionValueEvaluator
import com.example.domain.subscription.UsageState
import com.example.domain.subscription.ValueRating
import com.example.util.UsageHelper
import com.example.util.UsageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reactive UI State for Subscription Insights & Management.
 * Follows strict principles:
 * - No fake subscriptions
 * - No fake usage numbers
 * - Usage permission denied != 0 usage
 */
data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val summary: SubscriptionSummary = SubscriptionSummary(),
    val subscriptionItems: List<SubscriptionUiModel> = emptyList(),
    val filteredItems: List<SubscriptionUiModel> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val potentialSubscriptions: List<PotentialSubscription> = emptyList(),
    val priceChangeAlerts: List<SubscriptionPriceChange> = emptyList(),
    val usagePermissionGranted: Boolean = false,
    val selectedFilter: String = "ALL", // "ALL", "GREAT_VALUE", "MODERATE", "WASTED", "UNKNOWN", "CANCELLED"
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val selectedSubscriptionForDetail: SubscriptionUiModel? = null,
    val subToConfirmCancel: SubscriptionUiModel? = null,
    val subToRecordPayment: SubscriptionUiModel? = null,
    val showAddDialog: Boolean = false,
    // Legacy compatibility fields:
    val monthlyRecurringCommitment: Double = 0.0,
    val activeServices: Int = 0,
    val wastedOnInactiveApps: Double = 0.0,
    val savedByCancelling: Double = 0.0,
    val usageStatusMap: Map<Int, UsageStatus> = emptyMap()
)

private data class SubscriptionCoreData(
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val subscriptionItems: List<SubscriptionUiModel> = emptyList(),
    val summary: SubscriptionSummary = SubscriptionSummary(),
    val usageGranted: Boolean = false,
    val usageStatusMap: Map<Int, UsageStatus> = emptyMap(),
    val potentialSubscriptions: List<PotentialSubscription> = emptyList(),
    val priceChangeAlerts: List<SubscriptionPriceChange> = emptyList(),
    val savedByCancelling: Double = 0.0
)

private data class SubscriptionUiControls(
    val selectedFilter: String = "ALL",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val selectedSubscriptionForDetail: SubscriptionUiModel? = null,
    val subToConfirmCancel: SubscriptionUiModel? = null,
    val subToRecordPayment: SubscriptionUiModel? = null,
    val showAddDialog: Boolean = false
)

/**
 * ViewModel managing Subscription Insights, Detection, Usage Tracking, and Safe Cancellation.
 */
class SubscriptionViewModel @JvmOverloads constructor(
    application: Application,
    private val subscriptionRepository: SubscriptionRepository = SubscriptionRepository(
        AppDatabaseProvider.getDatabase(application).subscriptionDao(),
        AppDatabaseProvider.getDatabase(application).transactionDao()
    ),
    private val wealthWiseRepository: WealthWiseRepository = WealthWiseRepository(
        AppDatabaseProvider.getDatabase(application),
        application
    )
) : AndroidViewModel(application) {

    private val _activeUserId = MutableStateFlow(wealthWiseRepository.currentUserId())
    private val _usageGranted = MutableStateFlow(UsageHelper.hasUsagePermission(application))
    private val _usageStatusMap = MutableStateFlow<Map<Int, UsageStatus>>(emptyMap())
    private val _usageMinutesMap = MutableStateFlow<Map<Long, Long?>>(emptyMap())
    private val _potentialSubscriptions = MutableStateFlow<List<PotentialSubscription>>(emptyList())
    private val _priceChangeAlerts = MutableStateFlow<List<SubscriptionPriceChange>>(emptyList())
    private val _uiControls = MutableStateFlow(SubscriptionUiControls())

    // Base subscriptions flow directly from Room reactively responding to active user ID
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _subscriptionsFlow = _activeUserId.flatMapLatest { uid ->
        subscriptionRepository.getSubscriptionsFlow(uid)
    }

    private val _usageDataFlow = combine(
        _usageGranted,
        _usageMinutesMap,
        _usageStatusMap
    ) { granted, minutesMap, statusMap ->
        Triple(granted, minutesMap, statusMap)
    }

    private val _discoveryFlow = combine(
        _potentialSubscriptions,
        _priceChangeAlerts
    ) { potential, priceChanges ->
        Pair(potential, priceChanges)
    }

    private val _coreDataFlow = combine(
        _subscriptionsFlow,
        _usageDataFlow,
        _discoveryFlow
    ) { subs, usageData, discovery ->
        val (granted, usageMinutesMap, usageStatusMap) = usageData
        val (potential, priceChanges) = discovery
        val context = getApplication<Application>()
        val uiModels = subs.map { sub ->
            val pkg = sub.packageName ?: sub.linkedPackageName.ifBlank { null }
            val isInstalled = if (!pkg.isNullOrBlank()) UsageHelper.isAppInstalled(context, pkg) else false
            val minutes = usageMinutesMap[sub.id.toLong()]

            val costPerHour = SubscriptionValueEvaluator.calculateCostPerHour(sub.monthlyCost, minutes)
            val valueRating = SubscriptionValueEvaluator.evaluateValueRating(
                usageGranted = granted,
                packageName = pkg,
                isAppInstalled = isInstalled,
                usageMinutes = minutes
            )
            val usageState = SubscriptionValueEvaluator.evaluateUsageState(
                usageGranted = granted,
                packageName = pkg,
                isAppInstalled = isInstalled,
                usageMinutes = minutes
            )

            val parsedDueDate = try {
                if (sub.nextExpectedPaymentDate.isNotBlank()) {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .parse(sub.nextExpectedPaymentDate)?.time ?: 0L
                } else 0L
            } catch (_: Throwable) { 0L }

            SubscriptionUiModel(
                id = sub.id.toLong(),
                serviceName = sub.name.ifBlank { sub.merchantName },
                monthlyPrice = sub.monthlyCost,
                packageName = pkg,
                nextDueDate = parsedDueDate,
                usageMinutesLast30Days = minutes,
                costPerHour = costPerHour,
                valueStatus = valueRating,
                usageState = usageState,
                category = sub.category,
                billingPeriod = sub.billingPeriod,
                originalAmount = sub.amount,
                status = sub.status,
                isAutoDetected = sub.isAutoDetected,
                lastChargedDate = sub.lastChargedDate
            )
        }

        val summary = SubscriptionValueEvaluator.calculateSummary(uiModels)
        val savedCancelling = subscriptionRepository.calculateSavedByCancelling(subs)

        SubscriptionCoreData(
            subscriptions = subs,
            subscriptionItems = uiModels,
            summary = summary,
            usageGranted = granted,
            usageStatusMap = usageStatusMap,
            potentialSubscriptions = potential,
            priceChangeAlerts = priceChanges,
            savedByCancelling = savedCancelling
        )
    }

    val uiState: StateFlow<SubscriptionUiState> = combine(
        _coreDataFlow,
        _uiControls
    ) { data, controls ->
        val query = controls.searchQuery.trim().lowercase()
        val filtered = data.subscriptionItems.filter { item ->
            val matchesQuery = query.isEmpty() ||
                    item.serviceName.lowercase().contains(query) ||
                    item.category.lowercase().contains(query)

            val matchesFilter = when (controls.selectedFilter) {
                "GREAT_VALUE" -> item.valueStatus == ValueRating.GREAT_VALUE && item.status == "ACTIVE"
                "MODERATE" -> item.valueStatus == ValueRating.MODERATE && item.status == "ACTIVE"
                "WASTED" -> item.valueStatus == ValueRating.WASTED && item.status == "ACTIVE"
                "UNKNOWN" -> item.valueStatus == ValueRating.UNKNOWN && item.status == "ACTIVE"
                "ACTIVE" -> item.status == "ACTIVE"
                "CANCELLED" -> item.status == "CANCELLED"
                else -> true
            }
            matchesQuery && matchesFilter
        }

        SubscriptionUiState(
            isLoading = controls.isLoading,
            summary = data.summary,
            subscriptionItems = data.subscriptionItems,
            filteredItems = filtered,
            subscriptions = data.subscriptions,
            potentialSubscriptions = data.potentialSubscriptions,
            priceChangeAlerts = data.priceChangeAlerts,
            usagePermissionGranted = data.usageGranted,
            selectedFilter = controls.selectedFilter,
            searchQuery = controls.searchQuery,
            errorMessage = controls.errorMessage,
            toastMessage = controls.toastMessage,
            selectedSubscriptionForDetail = controls.selectedSubscriptionForDetail,
            subToConfirmCancel = controls.subToConfirmCancel,
            subToRecordPayment = controls.subToRecordPayment,
            showAddDialog = controls.showAddDialog,
            monthlyRecurringCommitment = data.summary.monthlyRecurringTotal,
            activeServices = data.summary.activeServicesCount,
            wastedOnInactiveApps = data.summary.wastedAnnualTotal,
            savedByCancelling = data.savedByCancelling,
            usageStatusMap = data.usageStatusMap
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubscriptionUiState()
    )

    init {
        setupAuthListener()
        refreshUsageAndAudit()
        runDetector()
    }

    private fun setupAuthListener() {
        try {
            FirebaseManager.auth?.addAuthStateListener { auth ->
                val newUid = auth.currentUser?.uid ?: ""
                if (newUid != _activeUserId.value) {
                    _activeUserId.value = newUid
                    refreshUsageAndAudit()
                    runDetector()
                }
            }
        } catch (_: Throwable) {
            // Local mode fallback
        }
    }

    /**
     * Checks usage access permission and audits all tracked subscriptions on IO dispatcher.
     * Caches usage results for the cycle; does not run on main thread or recomposition.
     */
    fun refreshUsageAndAudit() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val hasPermission = UsageHelper.hasUsagePermission(context)
            _usageGranted.value = hasPermission

            val currentSubs = uiState.value.subscriptions
            if (currentSubs.isNotEmpty()) {
                val usageMap = mutableMapOf<Long, Long?>()
                for (sub in currentSubs) {
                    val pkg = sub.packageName ?: sub.linkedPackageName.ifBlank { null }
                    if (hasPermission && !pkg.isNullOrBlank() && UsageHelper.isAppInstalled(context, pkg)) {
                        val minutes = UsageHelper.getMonthlyAppUsageMinutes(context, pkg)
                        usageMap[sub.id.toLong()] = minutes
                    } else {
                        usageMap[sub.id.toLong()] = null
                    }
                }
                _usageMinutesMap.value = usageMap
                val legacyMap = subscriptionRepository.auditSubscriptionUsage(context, currentSubs)
                _usageStatusMap.value = legacyMap
            }
        }
    }

    /**
     * Scans transactions for recurring candidate subscriptions and price change alerts.
     */
    fun runDetector() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = _activeUserId.value
                val rejected = wealthWiseRepository.getRejectedSubscriptionMerchants()
                val potential = subscriptionRepository.detectCandidates(uid, rejected)
                val priceChanges = subscriptionRepository.detectPriceChanges(uid)
                _potentialSubscriptions.value = potential
                _priceChangeAlerts.value = priceChanges
            } catch (_: Exception) {
                // Ignore to prevent disruption
            }
        }
    }

    /**
     * Confirms a candidate subscription discovered by the detector.
     */
    fun confirmDetectedSubscription(candidate: PotentialSubscription) {
        viewModelScope.launch(Dispatchers.IO) {
            val monthly = SubscriptionValueEvaluator.calculateMonthlyEquivalent(candidate.amount, candidate.billingPeriod)
            val newSub = SubscriptionEntity(
                userId = _activeUserId.value,
                name = candidate.merchant,
                merchantName = candidate.merchant,
                amount = candidate.amount,
                billingPeriod = candidate.billingPeriod,
                linkedPackageName = candidate.suggestedPackage ?: "",
                source = "DETECTED",
                status = "ACTIVE",
                monthlyCost = monthly
            )
            subscriptionRepository.saveOrUpdateSubscription(newSub)
            _potentialSubscriptions.value = _potentialSubscriptions.value.filter { it != candidate }
            _uiControls.update { it.copy(toastMessage = "Tracking '${candidate.merchant}' subscription") }
            refreshUsageAndAudit()
        }
    }

    /**
     * Rejects a candidate subscription (adds merchant to rejected list).
     */
    fun rejectDetectedSubscription(candidate: PotentialSubscription) {
        viewModelScope.launch(Dispatchers.IO) {
            wealthWiseRepository.addRejectedSubscriptionMerchant(candidate.merchant)
            _potentialSubscriptions.value = _potentialSubscriptions.value.filter { it != candidate }
            _uiControls.update { it.copy(toastMessage = "'${candidate.merchant}' dismissed") }
        }
    }

    /**
     * Opens the service app if installed, or official management webpage if available.
     * Never claims cancellation occurred.
     */
    fun openSubscriptionAppOrFallback(context: Context, sub: SubscriptionUiModel) {
        val pkg = sub.packageName
        val knownApp = SubscriptionAppResolver.resolveApp(sub.serviceName, pkg?.ifBlank { null })

        if (!pkg.isNullOrBlank() && UsageHelper.isAppInstalled(context, pkg)) {
            val launched = SubscriptionAppResolver.launchApp(context, pkg)
            if (!launched) {
                _uiControls.update { it.copy(toastMessage = "Failed to launch app for ${sub.serviceName}") }
            }
        } else if (knownApp != null && knownApp.websiteUrl.isNotBlank()) {
            SubscriptionAppResolver.openWeb(context, knownApp.websiteUrl)
            _uiControls.update { it.copy(toastMessage = "Opening ${knownApp.appName} account page") }
        } else {
            _uiControls.update { it.copy(toastMessage = "App not installed on this device. Open ${sub.serviceName} to manage your subscription.") }
        }
    }

    /**
     * Confirms manual cancellation in local WealthWise database.
     * WealthWise does NOT pretend to cancel real-world subscriptions automatically.
     */
    fun confirmManualCancellation(sub: SubscriptionUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionRepository.recordCancellation(sub.id.toInt())
            val savedAnnual = sub.monthlyPrice * 12.0
            _uiControls.update {
                it.copy(
                    subToConfirmCancel = null,
                    selectedSubscriptionForDetail = null,
                    toastMessage = "Marked '${sub.serviceName}' as cancelled. Potential annual avoidance: ${SubscriptionValueEvaluator.formatInr(savedAnnual)}"
                )
            }
            refreshUsageAndAudit()
        }
    }

    fun pauseSubscription(sub: SubscriptionUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionRepository.updateSubscriptionStatus(sub.id.toInt(), "PAUSED")
            _uiControls.update {
                it.copy(
                    selectedSubscriptionForDetail = null,
                    toastMessage = "'${sub.serviceName}' paused"
                )
            }
            refreshUsageAndAudit()
        }
    }

    fun reactivateSubscription(sub: SubscriptionUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionRepository.updateSubscriptionStatus(sub.id.toInt(), "ACTIVE")
            _uiControls.update {
                it.copy(
                    selectedSubscriptionForDetail = null,
                    toastMessage = "'${sub.serviceName}' reactivated"
                )
            }
            refreshUsageAndAudit()
        }
    }

    fun deleteSubscription(subId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionRepository.deleteSubscription(subId.toInt())
            _uiControls.update {
                it.copy(
                    selectedSubscriptionForDetail = null,
                    toastMessage = "Subscription removed from tracking (ledger transactions preserved)"
                )
            }
            refreshUsageAndAudit()
        }
    }

    /**
     * Adds a subscription manually with validation and monthly equivalent calculation.
     */
    fun addManualSubscription(
        name: String,
        amount: Double,
        billingPeriod: String,
        category: String,
        linkedPackage: String,
        nextDate: String
    ) {
        if (name.isBlank()) {
            _uiControls.update { it.copy(errorMessage = "Service name cannot be blank") }
            return
        }
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            _uiControls.update { it.copy(errorMessage = "Please enter a valid positive price") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val monthly = SubscriptionValueEvaluator.calculateMonthlyEquivalent(amount, billingPeriod)
            val sub = SubscriptionEntity(
                userId = _activeUserId.value,
                name = name.trim(),
                merchantName = name.trim(),
                amount = amount,
                billingPeriod = billingPeriod.uppercase(),
                category = category.ifBlank { "Subscription" },
                linkedPackageName = linkedPackage.trim(),
                nextExpectedPaymentDate = nextDate,
                source = "MANUAL",
                status = "ACTIVE",
                monthlyCost = monthly
            )
            subscriptionRepository.saveOrUpdateSubscription(sub)
            _uiControls.update {
                it.copy(
                    showAddDialog = false,
                    errorMessage = null,
                    toastMessage = "Added '$name' subscription"
                )
            }
            refreshUsageAndAudit()
        }
    }

    fun recordSubscriptionPayment(sub: SubscriptionUiModel, amount: Double, method: String, date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                wealthWiseRepository.recordSubscriptionPayment(
                    subscriptionId = sub.id.toInt(),
                    amount = amount,
                    paymentMethod = method,
                    paymentDate = date
                )
                _uiControls.update {
                    it.copy(
                        subToRecordPayment = null,
                        selectedSubscriptionForDetail = null,
                        toastMessage = "Payment of ${SubscriptionValueEvaluator.formatInr(amount)} recorded for '${sub.serviceName}'"
                    )
                }
            } catch (e: Exception) {
                _uiControls.update {
                    it.copy(toastMessage = e.message ?: "Failed to record payment")
                }
            }
        }
    }

    fun setFilter(filter: String) {
        _uiControls.update { it.copy(selectedFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiControls.update { it.copy(searchQuery = query) }
    }

    fun selectSubscriptionForDetail(sub: SubscriptionUiModel?) {
        _uiControls.update { it.copy(selectedSubscriptionForDetail = sub) }
    }

    fun showCancelConfirmation(sub: SubscriptionUiModel?) {
        _uiControls.update { it.copy(subToConfirmCancel = sub) }
    }

    fun showRecordPayment(sub: SubscriptionUiModel?) {
        _uiControls.update { it.copy(subToRecordPayment = sub) }
    }

    fun setShowAddDialog(show: Boolean) {
        _uiControls.update { it.copy(showAddDialog = show, errorMessage = null) }
    }

    fun clearToast() {
        _uiControls.update { it.copy(toastMessage = null) }
    }

    fun clearError() {
        _uiControls.update { it.copy(errorMessage = null) }
    }
}
