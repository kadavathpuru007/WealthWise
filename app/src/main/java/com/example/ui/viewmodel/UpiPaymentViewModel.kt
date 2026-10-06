package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.local.*
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.FinancialCalculationEngine
import com.example.domain.financial.MoneyUtils
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.wealthwise.app.utils.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

data class UpiPaymentUiState(
    val paymentState: UpiPaymentState = UpiPaymentState.Idle,
    val vpa: String = "",
    val merchantName: String = "",
    val resolvedRecipientName: String = "",
    val amount: String = "",
    val qrAmount: Double? = null,
    val isAmountFromQr: Boolean = false,
    val isAmountModifiedFromQr: Boolean = false,
    val selectedCategory: String = "Food",
    val selectedCategoryId: Long? = null,
    val isCategoryAutoPredicted: Boolean = true,
    val note: String = "WealthWise Payment",
    val budgetValidation: UpiBudgetValidation? = null,
    val availableCashBalance: Double = 0.0,
    val isStartingBalanceUnset: Boolean = false,
    val categories: List<CategoryEntity> = emptyList(),
    val recentUpiPayments: List<TransactionEntity> = emptyList(),
    val isLaunching: Boolean = false,
    val errorMessage: String? = null,
    val showConfirmationDialog: Boolean = false
)

class UpiPaymentViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: WealthWiseRepository = WealthWiseRepository(
        WealthWiseDatabase.getDatabase(application),
        application
    )
) : AndroidViewModel(application), PaymentResultWithDataListener {

    private val _activeUserId = MutableStateFlow(repository.currentUserId())
    val activeUserId: StateFlow<String> = _activeUserId.asStateFlow()

    private val _paymentState = MutableStateFlow<UpiPaymentState>(UpiPaymentState.Idle)
    val paymentState: StateFlow<UpiPaymentState> = _paymentState.asStateFlow()
    private val _vpa = MutableStateFlow("")
    private val _merchantName = MutableStateFlow("")
    private val _amount = MutableStateFlow("")
    private val _qrAmount = MutableStateFlow<Double?>(null)
    private val _selectedCategory = MutableStateFlow("Food")
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _isCategoryAutoPredicted = MutableStateFlow(true)
    private val _note = MutableStateFlow("WealthWise Payment")
    private val _isLaunching = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _showConfirmationDialog = MutableStateFlow(false)

    init {
        try {
            FirebaseManager.auth?.addAuthStateListener { auth ->
                val uid = auth.currentUser?.uid.orEmpty()
                _activeUserId.value = uid
            }
        } catch (_: Throwable) {}
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UpiPaymentUiState> = _activeUserId
        .flatMapLatest { userId ->
            val effectiveUserId = if (userId.isNotBlank()) userId else repository.currentUserId()
            combine(
                repository.getCategoriesFlow(effectiveUserId),
                repository.budgetsForUser(effectiveUserId),
                repository.observeTransactions(effectiveUserId),
                _paymentState,
                _vpa,
                _merchantName,
                _amount,
                _qrAmount,
                _selectedCategory,
                _selectedCategoryId,
                _isCategoryAutoPredicted,
                _note,
                _isLaunching,
                _errorMessage,
                _showConfirmationDialog
            ) { flows ->
                @Suppress("UNCHECKED_CAST")
                val roomCategories = flows[0] as List<CategoryEntity>
                @Suppress("UNCHECKED_CAST")
                val legacyBudgets = flows[1] as List<BudgetEntity>
                @Suppress("UNCHECKED_CAST")
                val transactions = flows[2] as List<TransactionEntity>
                val state = flows[3] as UpiPaymentState
                val vpa = flows[4] as String
                val merchant = flows[5] as String
                val amountStr = flows[6] as String
                val qrAmt = flows[7] as Double?
                val category = flows[8] as String
                val catId = flows[9] as Long?
                val isAutoCat = flows[10] as Boolean
                val note = flows[11] as String
                val launching = flows[12] as Boolean
                val error = flows[13] as String?
                val showDialog = flows[14] as Boolean

                buildUiState(
                    userId = effectiveUserId,
                    roomCategories = roomCategories,
                    legacyBudgets = legacyBudgets,
                    transactions = transactions,
                    paymentState = state,
                    vpa = vpa,
                    merchant = merchant,
                    amountStr = amountStr,
                    qrAmount = qrAmt,
                    selectedCategory = category,
                    selectedCategoryId = catId,
                    isCategoryAutoPredicted = isAutoCat,
                    note = note,
                    isLaunching = launching,
                    errorMessage = error,
                    showConfirmationDialog = showDialog
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UpiPaymentUiState()
        )

    private fun buildUiState(
        userId: String,
        roomCategories: List<CategoryEntity>,
        legacyBudgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>,
        paymentState: UpiPaymentState,
        vpa: String,
        merchant: String,
        amountStr: String,
        qrAmount: Double?,
        selectedCategory: String,
        selectedCategoryId: Long?,
        isCategoryAutoPredicted: Boolean,
        note: String,
        isLaunching: Boolean,
        errorMessage: String?,
        showConfirmationDialog: Boolean
    ): UpiPaymentUiState {
        // Effective categories: roomCategories or fallback from legacyBudgets
        val effectiveCategories = if (roomCategories.isNotEmpty()) {
            roomCategories
        } else if (legacyBudgets.isNotEmpty()) {
            legacyBudgets.mapIndexed { index, b ->
                CategoryEntity(
                    id = (index + 1).toLong(),
                    userId = b.userId,
                    name = b.category,
                    budgetLimit = b.monthlyLimit,
                    colorHex = b.colorHex
                )
            }
        } else {
            emptyList()
        }

        // Check if starting balance is explicitly configured in repository
        val initialBalance = repository.getInitialBalance(userId)
        val isExplicitlyUnset = (initialBalance == null)
        val startingBalToUse = initialBalance ?: repository.startingBankBalance

        // Calculate available cash balance from FinancialCalculationEngine
        val overview = FinancialCalculationEngine.calculateOverview(
            startingBalance = MoneyUtils.of(startingBalToUse),
            transactions = transactions,
            budgets = legacyBudgets
        )
        val availableCash = overview.currentBalance.toDouble().coerceAtLeast(0.0)

        // Starting balance is treated as unset if initialBalance was never set,
        // OR if the calculated balance evaluates to 0 due to an unset/zero starting balance with no income.
        val hasRecordedIncome = transactions.any { it.type.equals("INCOME", ignoreCase = true) || !it.isOutflow && it.amount > 0 }
        val isStartingBalanceUnset = isExplicitlyUnset || (startingBalToUse == 0.0 && availableCash == 0.0 && !hasRecordedIncome)

        // Calculate current calendar month expenses for the selected category
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

        val completedMonthlyExpenses = transactions.filter { txn ->
            HomeViewModel.isCompletedTransaction(txn) &&
                    !txn.isTransfer &&
                    (txn.type.equals("EXPENSE", ignoreCase = true) || txn.type.equals("debit", ignoreCase = true) || txn.isOutflow) &&
                    txn.timestamp in startOfMonth..endOfMonth
        }

        val categorySpent = completedMonthlyExpenses
            .filter { it.category.trim().equals(selectedCategory.trim(), ignoreCase = true) }
            .sumOf { kotlin.math.abs(it.amount) }

        // Find configured budget for selected category
        val categoryLimit = effectiveCategories
            .firstOrNull { it.name.trim().equals(selectedCategory.trim(), ignoreCase = true) }
            ?.budgetLimit
            ?: legacyBudgets.firstOrNull { it.category.trim().equals(selectedCategory.trim(), ignoreCase = true) }?.monthlyLimit
            ?: 0.0

        // Parse amount entered
        val parsedAmt = amountStr.toDoubleOrNull() ?: 0.0

        val budgetValidation = if (parsedAmt > 0.0 && categoryLimit > 0.0) {
            UpiPaymentHelper.evaluateUpiBudgetAndBalance(
                paymentAmount = parsedAmt,
                categoryName = selectedCategory,
                availableCashBalance = availableCash,
                budgetLimit = categoryLimit,
                currentSpent = categorySpent,
                isStartingBalanceUnset = isStartingBalanceUnset
            )
        } else {
            null
        }

        // Recent UPI payments (completed outflows where paymentMethod == 'UPI' or category isNotBlank)
        val recentUpi = transactions
            .filter { txn ->
                HomeViewModel.isCompletedTransaction(txn) &&
                        !txn.isTransfer &&
                        (txn.paymentMethod.equals("UPI", ignoreCase = true) || txn.category.isNotBlank()) &&
                        txn.isOutflow
            }
            .take(5)

        val isFromQr = qrAmount != null
        val isModified = qrAmount != null && parsedAmt > 0.0 && kotlin.math.abs(qrAmount - parsedAmt) > 0.01
        val resolvedName = UpiPaymentHelper.resolveRecipientName(vpa, merchant)

        return UpiPaymentUiState(
            paymentState = paymentState,
            vpa = vpa,
            merchantName = merchant,
            resolvedRecipientName = resolvedName,
            amount = amountStr,
            qrAmount = qrAmount,
            isAmountFromQr = isFromQr,
            isAmountModifiedFromQr = isModified,
            selectedCategory = selectedCategory,
            selectedCategoryId = selectedCategoryId,
            isCategoryAutoPredicted = isCategoryAutoPredicted,
            note = note,
            budgetValidation = budgetValidation,
            availableCashBalance = availableCash,
            isStartingBalanceUnset = isStartingBalanceUnset,
            categories = effectiveCategories,
            recentUpiPayments = recentUpi,
            isLaunching = isLaunching,
            errorMessage = errorMessage,
            showConfirmationDialog = showConfirmationDialog
        )
    }

    /**
     * Smart auto-prediction function that inspects the entered merchant name, UPI handle,
     * and note / description (e.g. keywords like "food", "lunch at restaurant" to Food; "bill", "electricity" to Bills)
     * and auto-selects the category silently.
     */
    private fun silentPredictCategory(merchant: String, vpa: String, note: String = "") {
        val predicted = UpiPaymentHelper.suggestCategory(merchant, vpa, note)
        if (!predicted.isNullOrBlank()) {
            _selectedCategory.value = predicted
            _isCategoryAutoPredicted.value = true
        }
    }

    fun startQrScan(context: Context) {
        _paymentState.value = UpiPaymentState.Scanning
        _errorMessage.value = null

        UpiPaymentHelper.startQrScan(
            context = context,
            onResult = { parsed ->
                if (parsed != null) {
                    _paymentState.value = UpiPaymentState.Scanned(parsed)
                    _vpa.value = parsed.vpa
                    _merchantName.value = parsed.name
                    if (parsed.amount != null && parsed.amount > 0.0) {
                        _amount.value = String.format(Locale.US, "%.2f", parsed.amount)
                        _qrAmount.value = parsed.amount
                    } else {
                        _qrAmount.value = null
                    }
                    if (!parsed.note.isNullOrBlank()) {
                        _note.value = parsed.note
                    }
                    // Auto-predict category silently from scanned details including note
                    silentPredictCategory(parsed.name, parsed.vpa, parsed.note.orEmpty())
                } else {
                    _paymentState.value = UpiPaymentState.Failed("Unable to read QR code. Please verify the code and try again.")
                }
            },
            onCanceled = {
                _paymentState.value = UpiPaymentState.Idle
            }
        )
    }

    fun onVpaChanged(newVpa: String) {
        _vpa.value = newVpa
        _errorMessage.value = null
        if (_paymentState.value is UpiPaymentState.Failed || _paymentState.value is UpiPaymentState.Cancelled) {
            _paymentState.value = UpiPaymentState.Idle
        }
        silentPredictCategory(_merchantName.value, newVpa, _note.value)
    }

    fun onMerchantChanged(newName: String) {
        _merchantName.value = newName
        silentPredictCategory(newName, _vpa.value, _note.value)
    }

    fun onAmountChanged(newAmount: String) {
        _amount.value = newAmount
        _errorMessage.value = null
        if (_paymentState.value is UpiPaymentState.Failed || _paymentState.value is UpiPaymentState.Cancelled) {
            _paymentState.value = UpiPaymentState.Idle
        }
    }

    fun onCategorySelected(category: String, categoryId: Long? = null) {
        _selectedCategory.value = category
        _selectedCategoryId.value = categoryId
        _isCategoryAutoPredicted.value = false
    }

    fun onNoteChanged(newNote: String) {
        _note.value = newNote
        silentPredictCategory(_merchantName.value, _vpa.value, newNote)
    }

    fun openConfirmation() {
        val currentVpa = _vpa.value.trim()
        if (!UpiPaymentHelper.isValidVpa(currentVpa)) {
            _errorMessage.value = "Please enter a valid UPI ID (e.g. name@upi) or 10-digit mobile number."
            return
        }

        val amt = _amount.value.toDoubleOrNull()
        if (amt == null || amt <= 0.0 || amt.isNaN() || amt.isInfinite()) {
            _errorMessage.value = "Please enter a valid payment amount greater than ₹0.00."
            return
        }

        val validation = uiState.value.budgetValidation
        if (validation != null && validation.status == BudgetValidationStatus.BLOCKED) {
            _paymentState.value = UpiPaymentState.BudgetBlocked(validation.message)
            _errorMessage.value = validation.message
            return
        }

        _showConfirmationDialog.value = true
    }

    fun dismissConfirmation() {
        _showConfirmationDialog.value = false
    }

    /**
     * Initializes and opens official Razorpay Checkout SDK.
     * Passes amount in paisa (INR * 100), currency "INR", company name "WealthWise",
     * prefill details (email, phone, VPA), and theme colors.
     */
    fun openRazorpayCheckout(activity: Activity) {
        if (_isLaunching.value) return // Prevent duplicate launch clicks

        val currentVpa = _vpa.value.trim()
        val currentMerchant = _merchantName.value.ifBlank {
            if (UpiPaymentHelper.isMobileNumber(currentVpa)) currentVpa else "Merchant"
        }.trim()
        val currentAmount = _amount.value.toDoubleOrNull() ?: 0.0
        val currentNote = _note.value.ifBlank { "WealthWise Payment" }.trim()

        val validation = uiState.value.budgetValidation
        if (validation != null && validation.status == BudgetValidationStatus.BLOCKED) {
            _paymentState.value = UpiPaymentState.BudgetBlocked(validation.message)
            _errorMessage.value = validation.message
            _showConfirmationDialog.value = false
            return
        }

        if (currentAmount <= 0.0) {
            _errorMessage.value = "Please enter a valid payment amount greater than ₹0.00."
            _showConfirmationDialog.value = false
            return
        }

        _isLaunching.value = true
        _paymentState.value = UpiPaymentState.LaunchingUpi
        _showConfirmationDialog.value = false

        try {
            val checkout = Checkout()
            checkout.setKeyID("rzp_test_1DP5mmOlF5G5ag")

            // Construct JSON options payload properly
            val options = JSONObject()
            options.put("name", "WealthWise")
            options.put("description", currentNote.ifBlank { "Payment to $currentMerchant" })
            options.put("currency", "INR")
            // Pass amount in paisa (multiply INR amount by 100)
            val amountInPaisa = (currentAmount * 100).roundToLong()
            options.put("amount", amountInPaisa)

            // User prefill details
            val prefill = JSONObject()
            val userEmail = FirebaseManager.auth?.currentUser?.email
            if (!userEmail.isNullOrBlank()) {
                prefill.put("email", userEmail)
            }
            val userPhone = FirebaseManager.auth?.currentUser?.phoneNumber
            val contactPhone = if (UpiPaymentHelper.isMobileNumber(currentVpa)) {
                UpiPaymentHelper.cleanMobileNumber(currentVpa)
            } else {
                userPhone
            }
            if (!contactPhone.isNullOrBlank()) {
                prefill.put("contact", contactPhone)
            }
            if (currentVpa.contains("@")) {
                prefill.put("vpa", currentVpa)
            }
            options.put("prefill", prefill)

            val theme = JSONObject()
            theme.put("color", "#0F172A")
            options.put("theme", theme)

            val retryObj = JSONObject()
            retryObj.put("enabled", true)
            retryObj.put("max_count", 2)
            options.put("retry", retryObj)

            checkout.open(activity, options)
        } catch (e: Exception) {
            _isLaunching.value = false
            val errorMsg = e.message ?: "Failed to open Razorpay checkout."
            _paymentState.value = UpiPaymentState.Failed(errorMsg)
            _errorMessage.value = errorMsg
        }
    }

    /**
     * Resolves the verified recipient or merchant name.
     */
    fun resolveRecipient(): String {
        return UpiPaymentHelper.resolveRecipientName(_vpa.value, _merchantName.value)
    }


    fun recordManualPayment() {
        viewModelScope.launch {
            try {
                val currentAmt = _amount.value.toDoubleOrNull() ?: 0.0
                if (currentAmt <= 0.0) return@launch
                
                recordConfirmedUpiPayment(
                    refId = "MANUAL_${System.currentTimeMillis()}"
                )
            } catch (e: Exception) {
                _paymentState.value = UpiPaymentState.Failed("Unable to record payment: ${e.localizedMessage ?: "Unknown error"}")
                _errorMessage.value = "Unable to record payment: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    suspend fun recordConfirmedUpiPayment(refId: String) {
        _isLaunching.value = false
        val currentAmt = _amount.value.toDoubleOrNull() ?: 0.0
        val currentVpa = _vpa.value.trim()
        val resolvedName = resolveRecipient()
        val currentCategory = _selectedCategory.value.ifBlank { "Other" }
        val currentNote = _note.value.ifBlank { "Payment to $resolvedName" }.trim()
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val currentUserId = _activeUserId.value.ifBlank { repository.currentUserId() }

        // Generate SHA-256 cryptographic fingerprint to drop duplicates
        val fpHash = UpiPaymentHelper.generateFingerprintHash(now, currentAmt, resolvedName)

        try {
            val txn = TransactionEntity(
                userId = currentUserId,
                title = "Payment to $resolvedName",
                amount = currentAmt,
                type = "EXPENSE",
                category = currentCategory,
                paymentMethod = "UPI",
                date = dateStr,
                timestamp = now,
                isParsed = false,
                merchant = resolvedName,
                note = currentNote,
                eventType = "EXPENSE",
                status = "COMPLETED",
                referenceId = refId,
                fingerprint = fpHash
            )
            val insertedId = repository.insertTransaction(txn)
            // Synchronize with Firebase Firestore
            FirebaseManager.syncTransactionToFirestore(currentUserId, txn.copy(id = insertedId.toInt()))

            _paymentState.value = UpiPaymentState.Completed
            _errorMessage.value = null
        } catch (e: Exception) {
            _paymentState.value = UpiPaymentState.Failed(
                "Payment succeeded ($refId) but failed to record in ledger: ${e.message}"
            )
            _errorMessage.value = "Payment succeeded ($refId) but failed to record in ledger: ${e.message}"
        }
    }

    fun proceedToLaunchUpi(context: Context) {
        if (context is Activity) {
            openRazorpayCheckout(context)
        } else {
            _errorMessage.value = "Activity context is required for Razorpay checkout."
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        viewModelScope.launch {
            handlePaymentSuccess(razorpayPaymentId, paymentData)
        }
    }

    suspend fun handlePaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        _isLaunching.value = false
        val paymentId = razorpayPaymentId
            ?: paymentData?.paymentId
            ?: "RPAY_${System.currentTimeMillis()}"

        val currentAmt = _amount.value.toDoubleOrNull() ?: 0.0
        val currentVpa = _vpa.value.trim()
        val currentMerchant = _merchantName.value.ifBlank {
            UpiPaymentHelper.resolveRecipientName(currentVpa)
        }.trim()
        val currentCategory = _selectedCategory.value.ifBlank { "Other" }
        val currentNote = _note.value.ifBlank { "WealthWise Payment" }.trim()
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        val currentUserId = _activeUserId.value.ifBlank { repository.currentUserId() }

        val fpHash = UpiPaymentHelper.generateFingerprintHash(now, currentAmt, currentMerchant)

        try {
            val txn = TransactionEntity(
                userId = currentUserId,
                title = "Payment to $currentMerchant",
                amount = currentAmt,
                type = "EXPENSE",
                category = currentCategory,
                paymentMethod = "UPI",
                date = dateStr,
                timestamp = now,
                isParsed = false,
                merchant = currentMerchant,
                note = currentNote,
                eventType = "EXPENSE",
                status = "COMPLETED",
                referenceId = paymentId,
                fingerprint = fpHash
            )
            val insertedId = repository.insertTransaction(txn)
            FirebaseManager.syncTransactionToFirestore(currentUserId, txn.copy(id = insertedId.toInt()))
            _paymentState.value = UpiPaymentState.Completed
            _errorMessage.value = null
        } catch (e: Exception) {
            _paymentState.value = UpiPaymentState.Failed(
                "Payment succeeded ($paymentId) but failed to record in ledger: ${e.message}"
            )
            _errorMessage.value = "Payment succeeded ($paymentId) but failed to record in ledger: ${e.message}"
        }
    }

    override fun onPaymentError(errorCode: Int, errorDescription: String?, paymentData: PaymentData?) {
        _isLaunching.value = false
        if (errorCode == Checkout.PAYMENT_CANCELED) {
            _paymentState.value = UpiPaymentState.Cancelled
            _errorMessage.value = "Payment was cancelled. No amount was deducted."
        } else {
            val desc = errorDescription?.ifBlank { "Payment failed (Error code: $errorCode)" }
                ?: "Payment failed (Error code: $errorCode)"
            _paymentState.value = UpiPaymentState.Failed(desc)
            _errorMessage.value = desc
        }
    }

    fun onPaymentCancelled() {
        _paymentState.value = UpiPaymentState.Cancelled
    }

    fun resetPayment() {
        _paymentState.value = UpiPaymentState.Idle
        _vpa.value = ""
        _merchantName.value = ""
        _amount.value = ""
        _qrAmount.value = null
        _selectedCategory.value = "Food"
        _selectedCategoryId.value = null
        _isCategoryAutoPredicted.value = true
        _note.value = "WealthWise Payment"
        _errorMessage.value = null
        _showConfirmationDialog.value = false
        _isLaunching.value = false
    }
}
