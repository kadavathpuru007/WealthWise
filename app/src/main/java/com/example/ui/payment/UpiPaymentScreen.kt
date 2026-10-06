package com.example.ui.payment

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.TransactionEntity
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*
import com.example.ui.viewmodel.UpiPaymentUiState
import com.example.ui.viewmodel.UpiPaymentViewModel
import com.wealthwise.app.utils.BudgetValidationStatus
import com.wealthwise.app.utils.UpiBudgetValidation
import com.wealthwise.app.utils.UpiPaymentHelper
import com.wealthwise.app.utils.UpiPaymentState
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiPaymentScreen(
    onNavigateBack: () -> Unit,
    viewModel: UpiPaymentViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()


    // Register active payment listener with RazorpayPaymentBridge
    DisposableEffect(viewModel) {
        RazorpayPaymentBridge.listener = viewModel
        onDispose {
            if (RazorpayPaymentBridge.listener == viewModel) {
                RazorpayPaymentBridge.listener = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "UPI Payment Hub",
                        fontWeight = FontWeight.Bold,
                        color = TextNavy,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = TextNavy
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetPayment() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset payment form",
                            tint = TextBlueGrey
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = LightBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Primary Action Card (Google Play Services Code Scanner + Razorpay Security)
            PrimaryHeroCard(
                onScanQr = { viewModel.startQrScan(context) }
            )

            // 2. Status / Notification Banner
            StatusBanner(
                paymentState = state.paymentState,
                errorMessage = state.errorMessage,
                onReset = { viewModel.resetPayment() },
                onDismiss = { viewModel.onPaymentCancelled() }
            )

            // 3. Payment Details Entry Card (Streamlined for UPI ID or Mobile, No Quick Amount Chips)
            PaymentDetailsCard(
                state = state,
                onVpaChanged = { viewModel.onVpaChanged(it) },
                onMerchantChanged = { viewModel.onMerchantChanged(it) },
                onAmountChanged = { viewModel.onAmountChanged(it) },
                onCategorySelected = { name, id -> viewModel.onCategorySelected(name, id) },
                onNoteChanged = { viewModel.onNoteChanged(it) }
            )

            // 4. Live Pre-Transaction Budget Enforcement Card
            state.budgetValidation?.let { validation ->
                BudgetEnforcementCard(
                    validation = validation,
                    categoryName = state.selectedCategory,
                    availableCash = state.availableCashBalance,
                    isStartingBalanceUnset = state.isStartingBalanceUnset
                )
            }

            // 5. Pay Action Button (PhonePe / Navi Style with Razorpay Checkout Trigger)
            val isValidVpa = UpiPaymentHelper.isValidVpa(state.vpa)
            val amt = state.amount.toDoubleOrNull() ?: 0.0
            val isBlocked = state.budgetValidation?.status == BudgetValidationStatus.BLOCKED
            val isActionEnabled = isValidVpa && amt > 0.0 && !isBlocked && !state.isLaunching

            // Explicit warning banner when budget category cap is exceeded
            val budgetValidation = state.budgetValidation
            if (isBlocked && budgetValidation != null) {
                Surface(
                    color = CrimsonRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Budget Exceeded",
                            tint = CrimsonRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = budgetValidation.message,
                            color = CrimsonDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.openConfirmation()
                },
                enabled = isActionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBlocked) CrimsonRed else Slate950,
                    disabledContainerColor = BorderLight
                )
            ) {
                if (state.isLaunching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Launching Payment...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = if (isBlocked) Icons.Default.Block else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (isActionEnabled) Color.White else TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isBlocked -> "Payment Blocked by Budget"
                            amt > 0.0 -> "Pay ${formatInr(amt)} via UPI App"
                            else -> "Enter Payee & Amount"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActionEnabled) Color.White else TextMuted
                    )
                }
            }

            // 6. Recent Completed UPI Payments Section
            if (state.recentUpiPayments.isNotEmpty()) {
                RecentUpiPaymentsSection(payments = state.recentUpiPayments)
            }
        }
    }

    // Confirmation Sheet / Dialog with UPI Intent Handover
    if (state.showConfirmationDialog) {
        PaymentConfirmationDialog(
            state = state,
            onDismiss = { viewModel.dismissConfirmation() },
            onManualConfirm = {
                viewModel.recordManualPayment()
                viewModel.dismissConfirmation()
            },
            onConfirmPay = {
                if (activity != null) {
                    viewModel.openRazorpayCheckout(activity)
                } else {
                    viewModel.proceedToLaunchUpi(context)
                }
            }
        )
    }
}

@Composable
private fun PrimaryHeroCard(
    onScanQr: () -> Unit
) {
    Surface(
        color = Slate950,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RoyalBlue.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Secure Payment",
                        tint = RoyalBlueLight,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "UPI Intent & Google Pay Engine",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Fee-free peer-to-peer & retail payments via NPCI deep-link",
                        fontSize = 12.sp,
                        color = Slate300
                    )
                }
            }

            Button(
                onClick = onScanQr,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan UPI QR Code",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan Any UPI QR Code",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    paymentState: UpiPaymentState,
    errorMessage: String?,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    when (paymentState) {
        is UpiPaymentState.Completed -> {
            Surface(
                color = Color(0xFFF0FDF4),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Payment Successful",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Payment Successful!",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDark,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Transaction saved into your local ledger and budget metrics updated.",
                            color = TextNavy,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onReset) {
                        Text("New Pay", color = EmeraldDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        is UpiPaymentState.LaunchingUpi -> {
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = RoyalBlue
                    )
                    Text(
                        text = "Opening Razorpay checkout gateway...",
                        color = TextNavy,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        is UpiPaymentState.AwaitingConfirmation -> {
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = "Awaiting Confirmation",
                        tint = RoyalBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Processing Payment",
                            fontWeight = FontWeight.Bold,
                            color = TextNavy,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Awaiting confirmation callback from payment gateway.",
                            color = TextBlueGrey,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        is UpiPaymentState.Cancelled -> {
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Payment Cancelled",
                        tint = TextBlueGrey,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Payment was cancelled. No transaction was deducted.",
                        color = TextBlueGrey,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        is UpiPaymentState.Failed -> {
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Payment Error",
                        tint = CrimsonRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = paymentState.message,
                        color = CrimsonDark,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        else -> {
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Notice",
                            tint = CrimsonRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = errorMessage,
                            color = CrimsonDark,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentDetailsCard(
    state: UpiPaymentUiState,
    onVpaChanged: (String) -> Unit,
    onMerchantChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onCategorySelected: (String, Long?) -> Unit,
    onNoteChanged: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val isVpaValid = state.vpa.isNotBlank() && UpiPaymentHelper.isValidVpa(state.vpa)
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Payee & Amount",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )

            // 1. Payee Mobile or UPI ID Input with Helpful Guidance
            OutlinedTextField(
                value = state.vpa,
                onValueChange = onVpaChanged,
                label = { Text("UPI ID or Mobile Number") },
                placeholder = { Text("e.g. 9876543210 or name@upi") },
                supportingText = {
                    Text(
                        text = "Enter a 10-digit mobile number, UPI ID (e.g. name@upi), or scan QR code above.",
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                trailingIcon = {
                    if (state.vpa.isNotBlank()) {
                        Icon(
                            imageVector = if (isVpaValid) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = if (isVpaValid) "Valid recipient" else "Invalid format",
                            tint = if (isVpaValid) EmeraldGreen else AmberWarning
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isVpaValid) EmeraldGreen else RoyalBlue,
                    unfocusedBorderColor = BorderLight
                )
            )

            // 2. Payee / Merchant Name (Optional)
            OutlinedTextField(
                value = state.merchantName,
                onValueChange = onMerchantChanged,
                label = { Text("Payee / Merchant Name (Optional)") },
                placeholder = { Text("e.g. Swiggy, Zomato, Electricity Bill") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalBlue,
                    unfocusedBorderColor = BorderLight
                )
            )

            // 2b. Payee Validation & Name Resolution Display
            if (isVpaValid && state.resolvedRecipientName.isNotBlank()) {
                Surface(
                    color = EmeraldGreen.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Resolved Payee",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Resolved Payee: ${state.resolvedRecipientName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = EmeraldDark
                            )
                            Text(
                                text = "NPCI Verified UPI VPA (${state.vpa})",
                                fontSize = 11.sp,
                                color = TextBlueGrey
                            )
                        }
                    }
                }
            }

            // 3. Amount TextField with Bold Currency Prefix (No quick chips!)
            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChanged,
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                prefix = {
                    Text(
                        text = "₹ ",
                        color = TextNavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalBlue,
                    unfocusedBorderColor = BorderLight
                )
            )

            // QR Amount Modification Notice
            if (state.isAmountFromQr && state.qrAmount != null) {
                if (state.isAmountModifiedFromQr) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Amount Modified",
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "QR requested amount: ${formatInr(state.qrAmount)}",
                                    fontSize = 12.sp,
                                    color = TextNavy,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Payment amount modified to: ₹${state.amount}",
                                    fontSize = 11.sp,
                                    color = AmberDark
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "QR requested amount: ${formatInr(state.qrAmount)}",
                        fontSize = 12.sp,
                        color = TextBlueGrey,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 4. Payment Note / Description (Auto-detects category e.g. "Lunch at restaurant" -> Food)
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChanged,
                label = { Text("Note / Description") },
                placeholder = { Text("e.g. Lunch at restaurant, Electricity bill") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoyalBlue,
                    unfocusedBorderColor = BorderLight
                )
            )
        }
    }
}

@Composable
private fun BudgetEnforcementCard(
    validation: UpiBudgetValidation,
    categoryName: String,
    availableCash: Double,
    isStartingBalanceUnset: Boolean
) {
    val borderColor = when (validation.status) {
        BudgetValidationStatus.VALID -> EmeraldGreen.copy(alpha = 0.5f)
        BudgetValidationStatus.WARNING -> AmberWarning.copy(alpha = 0.7f)
        BudgetValidationStatus.BLOCKED -> CrimsonRed.copy(alpha = 0.7f)
    }

    val bgColor = when (validation.status) {
        BudgetValidationStatus.VALID -> Color(0xFFF0FDF4)
        BudgetValidationStatus.WARNING -> Color(0xFFFFFBEB)
        BudgetValidationStatus.BLOCKED -> Color(0xFFFEF2F2)
    }

    val icon = when (validation.status) {
        BudgetValidationStatus.VALID -> Icons.Default.CheckCircle
        BudgetValidationStatus.WARNING -> Icons.Default.Warning
        BudgetValidationStatus.BLOCKED -> Icons.Default.Block
    }

    val iconColor = when (validation.status) {
        BudgetValidationStatus.VALID -> EmeraldGreen
        BudgetValidationStatus.WARNING -> AmberWarning
        BudgetValidationStatus.BLOCKED -> CrimsonRed
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = when (validation.status) {
                        BudgetValidationStatus.VALID -> "Pre-Transaction Budget Check: Approved"
                        BudgetValidationStatus.WARNING -> "Pre-Transaction Budget Warning"
                        BudgetValidationStatus.BLOCKED -> "Pre-Transaction Budget Check: Blocked"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = when (validation.status) {
                        BudgetValidationStatus.VALID -> EmeraldDark
                        BudgetValidationStatus.WARNING -> AmberDark
                        BudgetValidationStatus.BLOCKED -> CrimsonDark
                    }
                )
            }

            Text(
                text = validation.message,
                fontSize = 13.sp,
                color = TextNavy
            )

            HorizontalDivider(color = borderColor.copy(alpha = 0.4f), thickness = 1.dp)

            // Detailed Metric Breakdown
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (validation.budgetLimit > 0.0) {
                    BudgetMetricRow(
                        label = "$categoryName Monthly Budget",
                        value = formatInr(validation.budgetLimit)
                    )
                    BudgetMetricRow(
                        label = "Spent So Far This Month",
                        value = formatInr(validation.currentSpent)
                    )
                    BudgetMetricRow(
                        label = "Projected Spending After Payment",
                        value = formatInr(validation.projectedSpent),
                        isEmphasized = true
                    )
                    BudgetMetricRow(
                        label = "Projected Budget Usage",
                        value = "${validation.percentageAfterPayment}%",
                        valueColor = if (validation.percentageAfterPayment > 100) CrimsonRed else if (validation.percentageAfterPayment >= 90) AmberWarning else TextNavy
                    )
                } else {
                    BudgetMetricRow(
                        label = "$categoryName Monthly Budget",
                        value = "No Limit Configured"
                    )
                }

                if (isStartingBalanceUnset) {
                    BudgetMetricRow(
                        label = "Spendable Cash Balance",
                        value = "Opening Balance Not Set (Budget Validated)",
                        valueColor = TextBlueGrey
                    )
                } else {
                    BudgetMetricRow(
                        label = "Spendable Cash Balance",
                        value = formatInr(availableCash),
                        valueColor = if (availableCash < validation.paymentAmount) CrimsonRed else EmeraldDark
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetMetricRow(
    label: String,
    value: String,
    valueColor: Color = TextNavy,
    isEmphasized: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextBlueGrey,
            fontWeight = if (isEmphasized) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = valueColor,
            fontWeight = if (isEmphasized) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun PaymentConfirmationDialog(
    state: UpiPaymentUiState,
    onDismiss: () -> Unit,
    onManualConfirm: () -> Unit,
    onConfirmPay: () -> Unit
) {
    val validation = state.budgetValidation
    val isBlocked = validation?.status == BudgetValidationStatus.BLOCKED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.Payment,
                    contentDescription = null,
                    tint = if (isBlocked) CrimsonRed else RoyalBlue
                )
                Text(
                    text = if (isBlocked) "Payment Blocked" else "Confirm UPI Transfer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = LightBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BudgetMetricRow(
                            label = "Amount",
                            value = formatInr(state.amount.toDoubleOrNull() ?: 0.0),
                            isEmphasized = true
                        )
                        BudgetMetricRow(
                            label = "Resolved Payee",
                            value = state.resolvedRecipientName.ifBlank { "Merchant" }
                        )
                        BudgetMetricRow(
                            label = "UPI VPA",
                            value = state.vpa
                        )
                        BudgetMetricRow(
                            label = "Category",
                            value = state.selectedCategory
                        )
                        if (state.note.isNotBlank()) {
                            BudgetMetricRow(
                                label = "Note",
                                value = state.note
                            )
                        }
                    }
                }


                if (validation != null) {
                    if (validation.status == BudgetValidationStatus.WARNING) {
                        Surface(
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = validation.message,
                                fontSize = 12.sp,
                                color = AmberDark,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else if (validation.status == BudgetValidationStatus.BLOCKED) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = validation.message,
                                fontSize = 12.sp,
                                color = CrimsonDark,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                if (!isBlocked) {
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onManualConfirm,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Already Paid / Record Now", fontSize = 11.sp, color = EmeraldDark, fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = onConfirmPay,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Use Gateway", fontSize = 11.sp, color = RoyalBlue)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBlocked) "Back" else "Cancel", color = TextBlueGrey)
            }
        },
        shape = RoundedCornerShape(18.dp),
        containerColor = CardWhite
    )
}

@Composable
private fun RecentUpiPaymentsSection(payments: List<TransactionEntity>) {
    Surface(
        color = CardWhite,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recent Confirmed UPI Payments",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )

            payments.forEach { txn ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(LightBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payment,
                                contentDescription = null,
                                tint = RoyalBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = txn.merchant.ifBlank { txn.title },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextNavy
                            )
                            Text(
                                text = "${txn.category} • ${txn.date}",
                                fontSize = 11.sp,
                                color = TextBlueGrey
                            )
                        }
                    }
                    Text(
                        text = "-${formatInr(kotlin.math.abs(txn.amount))}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                }
            }
        }
    }
}
