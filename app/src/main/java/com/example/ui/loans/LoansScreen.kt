/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development By:
 * - Purushotham
 */

package com.example.ui.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.LoanEntity
import com.example.data.local.LoanRepaymentEntity
import com.example.domain.loan.LoanCalculations
import com.example.domain.loan.LoanProgressItem
import com.example.domain.loan.LoanValidationResult
import com.example.domain.loan.UpcomingEmiInfo
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*
import com.example.ui.viewmodel.LoansViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production Loans & Debt Screen for WealthWise.
 * Follows Material 3 and WealthWise corporate fintech design guidelines.
 * Displays reactive debt portfolio card, active loans, repayment history,
 * add loan bottom sheet with live EMI preview, and safe loan closure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onBack: () -> Unit,
    viewModel: LoansViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showAddLoanSheet by remember { mutableStateOf(false) }
    var selectedLoanForHistory by remember { mutableStateOf<LoanProgressItem?>(null) }
    var selectedLoanForPayment by remember { mutableStateOf<LoanProgressItem?>(null) }
    var loanToClose by remember { mutableStateOf<LoanProgressItem?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LightBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Loans & Debt",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Text(
                            text = "Manage liabilities & EMI repayments",
                            fontSize = 12.sp,
                            color = TextBlueGrey
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = TextNavy
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightBg
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddLoanSheet = true },
                containerColor = Slate950,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Loan Icon"
                    )
                },
                text = {
                    Text(
                        text = "Add Loan",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Slate900)
                    }
                }
                uiState.loansWithProgress.isEmpty() -> {
                    EmptyLoansView()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                    ) {
                        // 1. TOP DEBT PORTFOLIO CARD
                        item {
                            TopDebtPortfolioCard(
                                totalRemainingDebt = uiState.totalRemainingDebt,
                                totalDebtRepaid = uiState.totalDebtRepaid,
                                activeLoanCount = uiState.loansWithProgress.size,
                                nextUpcomingEmi = uiState.nextUpcomingEmi
                            )
                        }

                        // 2. ACTIVE LOANS SECTION HEADER
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Active Loans (${uiState.loansWithProgress.size})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextNavy
                                )
                            }
                        }

                        // 3. ACTIVE LOAN CARDS
                        items(uiState.loansWithProgress, key = { it.loan.id }) { item ->
                            ActiveLoanCard(
                                item = item,
                                onOpenHistory = { selectedLoanForHistory = item },
                                onRecordPayment = { selectedLoanForPayment = item },
                                onCloseLoan = { loanToClose = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Add Loan Bottom Sheet
    if (showAddLoanSheet) {
        AddLoanBottomSheet(
            onDismiss = { showAddLoanSheet = false },
            onSave = { name, lender, type, principal, emi, rate, tenure, dueDay ->
                viewModel.addLoan(
                    loanName = name,
                    lenderName = lender,
                    loanType = type,
                    principalAmount = principal,
                    emiAmount = emi,
                    interestRate = rate,
                    totalTenureMonths = tenure,
                    emiDueDayOfMonth = dueDay
                ) { result ->
                    if (result is LoanValidationResult.Valid) {
                        showAddLoanSheet = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Loan added successfully.")
                        }
                    }
                }
            }
        )
    }

    // Modal: Payment History Bottom Sheet
    selectedLoanForHistory?.let { loanItem ->
        PaymentHistoryBottomSheet(
            loanItem = loanItem,
            repaymentsFlow = viewModel.getRepaymentsForLoan(loanItem.loan.id),
            onDismiss = { selectedLoanForHistory = null }
        )
    }

    // Modal: Record Manual Repayment Dialog
    selectedLoanForPayment?.let { loanItem ->
        RecordRepaymentDialog(
            loanItem = loanItem,
            onDismiss = { selectedLoanForPayment = null },
            onConfirm = { amount, method ->
                viewModel.recordManualRepayment(
                    loanId = loanItem.loan.id,
                    amount = amount,
                    paymentMethod = method
                ) { result ->
                    if (result is LoanValidationResult.Valid) {
                        selectedLoanForPayment = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Repayment of ${formatInr(amount)} recorded.")
                        }
                    }
                }
            }
        )
    }

    // Dialog: Close Loan Confirmation
    loanToClose?.let { loanItem ->
        CloseLoanDialog(
            loanItem = loanItem,
            onDismiss = { loanToClose = null },
            onConfirm = {
                viewModel.closeLoan(loanItem.loan.id) { result ->
                    loanToClose = null
                    if (result is LoanValidationResult.Valid) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Loan '${loanItem.loan.loanName}' closed.")
                        }
                    }
                }
            }
        )
    }
}

/**
 * Dark Navy Top Debt Portfolio Card.
 */
@Composable
private fun TopDebtPortfolioCard(
    totalRemainingDebt: Double,
    totalDebtRepaid: Double,
    activeLoanCount: Int,
    nextUpcomingEmi: UpcomingEmiInfo?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Slate950),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "TOTAL REMAINING DEBT",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate400,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formatInr(totalRemainingDebt),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(18.dp))

            HorizontalDivider(
                thickness = 1.dp,
                color = Slate800
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Paid So Far & Active Loans Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Paid So Far",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatInr(totalDebtRepaid),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechGreenLight
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Active Loans",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeLoanCount.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Next Upcoming EMI
            if (nextUpcomingEmi != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate900)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Slate800),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Upcoming Calendar Icon",
                            tint = RoyalBlueLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Next EMI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate300
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val badgeColor = if (nextUpcomingEmi.daysRemaining == 0) AmberWarning else RoyalBlueLight
                            Text(
                                text = if (nextUpcomingEmi.daysRemaining == 0) "Due Today" else "due in ${nextUpcomingEmi.daysRemaining} days",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                        Text(
                            text = "${formatInr(nextUpcomingEmi.amount)} • ${nextUpcomingEmi.loanName} (${nextUpcomingEmi.lenderName})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate100
                        )
                    }
                }
            }
        }
    }
}

/**
 * Active Loan Card.
 */
@Composable
private fun ActiveLoanCard(
    item: LoanProgressItem,
    onOpenHistory: () -> Unit,
    onRecordPayment: () -> Unit,
    onCloseLoan: () -> Unit
) {
    val loan = item.loan
    val icon = getLoanTypeIcon(loan.loanType)
    val percentRepaid = (item.progress * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon + Loan Name/Lender + Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FintechBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "${loan.loanType} Icon",
                        tint = FintechBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.loanName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "${loan.lenderName} • ${loan.loanType.replace("_", " ")}",
                        fontSize = 12.sp,
                        color = TextBlueGrey
                    )
                }

                IconButton(
                    onClick = onCloseLoan,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = "Close Loan",
                        tint = TextBlueGrey,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Repayment Progress",
                    fontSize = 12.sp,
                    color = TextBlueGrey
                )
                Text(
                    text = "$percentRepaid% repaid",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FintechGreen
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = FintechGreen,
                trackColor = BorderLight
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Paid vs Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Paid So Far",
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                    Text(
                        text = formatInr(item.amountPaid),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FintechGreen
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Estimated Remaining",
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                    Text(
                        text = formatInr(item.remainingAmount),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextNavy
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tenure & Next EMI Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LightBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.paidEmiCount} of ${loan.totalTenureMonths} EMIs paid",
                    fontSize = 12.sp,
                    color = TextBlueGrey
                )
                Text(
                    text = "EMI: ${formatInr(loan.emiAmount)} / mo",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextNavy
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions: View History & Record Repayment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextNavy),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "History Icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "History", fontSize = 12.sp)
                }

                Button(
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Slate900,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = "Pay EMI", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Payment History ModalBottomSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentHistoryBottomSheet(
    loanItem: LoanProgressItem,
    repaymentsFlow: Flow<List<LoanRepaymentEntity>>,
    onDismiss: () -> Unit
) {
    val repayments by repaymentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Repayment History",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
            Text(
                text = "${loanItem.loan.loanName} • ${loanItem.loan.lenderName}",
                fontSize = 13.sp,
                color = TextBlueGrey
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (repayments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No repayments recorded yet.",
                        fontSize = 14.sp,
                        color = TextBlueGrey
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(repayments, key = { it.id }) { rep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LightBg)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = formatInr(rep.paidAmount),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FintechGreen
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateFormat.format(Date(rep.paymentDate)),
                                    fontSize = 11.sp,
                                    color = TextBlueGrey
                                )
                                if (!rep.transactionRef.isNullOrBlank()) {
                                    Text(
                                        text = "Ref: ${rep.transactionRef}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (rep.isAutoDetected) FintechTealBg else Slate200)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (rep.isAutoDetected) "Auto detected" else "Manual",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (rep.isAutoDetected) FintechTeal else TextNavy
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Add Loan Modal Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLoanBottomSheet(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        lender: String,
        type: String,
        principal: Double,
        emi: Double,
        rate: Double,
        tenure: Int,
        dueDay: Int
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    var loanName by remember { mutableStateOf("") }
    var lenderName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(LoanEntity.TYPE_PERSONAL) }
    var principalStr by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("") }
    var tenureStr by remember { mutableStateOf("") }
    var dueDayStr by remember { mutableStateOf("5") }
    var manualEmiStr by remember { mutableStateOf("") }
    var isEmiManuallyEdited by remember { mutableStateOf(false) }

    val principal = principalStr.toDoubleOrNull() ?: 0.0
    val rate = rateStr.toDoubleOrNull() ?: 0.0
    val tenure = tenureStr.toIntOrNull() ?: 0

    // Live reducing-balance EMI calculation
    val calculatedEmi = remember(principal, rate, tenure) {
        if (principal > 0 && tenure > 0 && rate >= 0) {
            LoanCalculations.calculateMonthlyEmi(principal, rate, tenure)
        } else {
            0.0
        }
    }

    val finalEmiAmount = if (isEmiManuallyEdited) {
        manualEmiStr.toDoubleOrNull() ?: calculatedEmi
    } else {
        calculatedEmi
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Add New Loan",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )

            // Loan Name
            OutlinedTextField(
                value = loanName,
                onValueChange = { loanName = it },
                label = { Text("Loan Name") },
                placeholder = { Text("e.g. Home Renovation Loan") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Lender Name
            OutlinedTextField(
                value = lenderName,
                onValueChange = { lenderName = it },
                label = { Text("Lender / Bank") },
                placeholder = { Text("e.g. HDFC Bank, SBI, Bajaj") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Loan Type Selector
            Text(
                text = "Loan Type",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextNavy
            )

            val loanTypes = listOf(
                LoanEntity.TYPE_PERSONAL,
                LoanEntity.TYPE_HOME,
                LoanEntity.TYPE_EDUCATION,
                LoanEntity.TYPE_VEHICLE,
                LoanEntity.TYPE_CONSUMER_EMI
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                loanTypes.take(3).forEach { type ->
                    val isSelected = selectedType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedType = type },
                        label = { Text(type.replace("_", " "), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                loanTypes.drop(3).forEach { type ->
                    val isSelected = selectedType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedType = type },
                        label = { Text(type.replace("_", " "), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Principal Amount
            OutlinedTextField(
                value = principalStr,
                onValueChange = { principalStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Principal Amount") },
                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Annual Interest Rate & Tenure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = rateStr,
                    onValueChange = { rateStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Rate (% p.a.)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Right) }
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = tenureStr,
                    onValueChange = { tenureStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Tenure (Months)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // EMI Due Day
            OutlinedTextField(
                value = dueDayStr,
                onValueChange = { dueDayStr = it.filter { ch -> ch.isDigit() } },
                label = { Text("Monthly Due Day (1 - 31)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Live EMI Calculator Card
            if (calculatedEmi > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = FintechBlueBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Estimated Monthly EMI",
                            fontSize = 12.sp,
                            color = FintechBlue
                        )
                        Text(
                            text = "${formatInr(calculatedEmi)} / month",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate950
                        )
                    }
                }
            }

            // EMI Amount (Editable)
            OutlinedTextField(
                value = if (isEmiManuallyEdited) manualEmiStr else if (calculatedEmi > 0) String.format(Locale.US, "%.0f", calculatedEmi) else "",
                onValueChange = {
                    isEmiManuallyEdited = true
                    manualEmiStr = it.filter { ch -> ch.isDigit() || ch == '.' }
                },
                label = { Text("Actual Monthly EMI Amount") },
                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    val p = principalStr.toDoubleOrNull() ?: 0.0
                    val r = rateStr.toDoubleOrNull() ?: 0.0
                    val t = tenureStr.toIntOrNull() ?: 0
                    val d = dueDayStr.toIntOrNull() ?: 1
                    val emi = finalEmiAmount

                    onSave(
                        loanName,
                        lenderName,
                        selectedType,
                        p,
                        emi,
                        r,
                        t,
                        d
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Slate950,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Add Loan Obligation",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Record Repayment Dialog.
 */
@Composable
private fun RecordRepaymentDialog(
    loanItem: LoanProgressItem,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: String) -> Unit
) {
    var amountStr by remember { mutableStateOf(loanItem.loan.emiAmount.toInt().toString()) }
    var paymentMethod by remember { mutableStateOf("Bank Transfer") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Record EMI Repayment",
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Loan: ${loanItem.loan.loanName} (${loanItem.loan.lenderName})",
                    fontSize = 13.sp,
                    color = TextBlueGrey
                )
                Text(
                    text = "Remaining Balance: ${formatInr(loanItem.remainingAmount)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextNavy
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Repayment Amount") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = { paymentMethod = it },
                    label = { Text("Payment Method") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onConfirm(amount, paymentMethod)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Slate950)
            ) {
                Text("Confirm Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextBlueGrey)
            }
        }
    )
}

/**
 * Safe Loan Closure Dialog.
 */
@Composable
private fun CloseLoanDialog(
    loanItem: LoanProgressItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Close this loan?",
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Are you sure you want to mark '${loanItem.loan.loanName}' as closed?",
                    fontSize = 14.sp,
                    color = TextNavy
                )
                if (loanItem.remainingAmount > 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Closing the loan will mark it as closed in WealthWise even though repayment records indicate an outstanding amount of ${formatInr(loanItem.remainingAmount)}.",
                        fontSize = 13.sp,
                        color = CrimsonRed
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All past repayment history will be safely preserved.",
                    fontSize = 12.sp,
                    color = TextBlueGrey
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
            ) {
                Text("Close Loan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextBlueGrey)
            }
        }
    )
}

/**
 * Empty state for users with no loans.
 */
@Composable
private fun EmptyLoansView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(FintechBlueBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = "No Loans Icon",
                tint = FintechBlue,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No active loans",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextNavy
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Add a loan to start tracking your debt and EMI payments.",
            fontSize = 13.sp,
            color = TextBlueGrey,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

    }
}

/**
 * Returns the appropriate Material Icon for a given loan type.
 */
private fun getLoanTypeIcon(loanType: String): ImageVector {
    return when (loanType.uppercase(Locale.ROOT)) {
        LoanEntity.TYPE_PERSONAL -> Icons.Default.AccountBalance
        LoanEntity.TYPE_HOME -> Icons.Default.Home
        LoanEntity.TYPE_EDUCATION -> Icons.Default.School
        LoanEntity.TYPE_VEHICLE -> Icons.Default.DirectionsCar
        LoanEntity.TYPE_CONSUMER_EMI -> Icons.Default.PhoneAndroid
        else -> Icons.Default.AccountBalance
    }
}