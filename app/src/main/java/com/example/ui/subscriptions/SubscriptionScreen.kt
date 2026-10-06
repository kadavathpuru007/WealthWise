/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development:
 * - Purushotham
 */

package com.example.ui.subscriptions

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.SubscriptionEntity
import com.example.domain.subscription.PotentialSubscription
import com.example.domain.subscription.SubscriptionPriceChange
import com.example.domain.subscription.SubscriptionUiModel
import com.example.domain.subscription.SubscriptionValueEvaluator
import com.example.domain.subscription.UsageState
import com.example.domain.subscription.ValueRating
import com.example.ui.theme.*
import com.example.ui.viewmodel.SubscriptionUiState
import com.example.ui.viewmodel.SubscriptionViewModel
import com.example.util.UsageHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production-ready Subscriptions Screen with Usage Insights.
 * Employs clean Material 3 design, reactive ViewModel state, and full privacy compliance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-refresh usage on ON_RESUME when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsageAndAudit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SubscriptionScreenContent(
        uiState = uiState,
        onBack = onBack,
        onRefreshUsage = { viewModel.refreshUsageAndAudit() },
        onRequestUsageAccess = { UsageHelper.openUsageSettings(context) },
        onSelectFilter = { viewModel.setFilter(it) },
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onAddSubscriptionClick = { viewModel.setShowAddDialog(true) },
        onConfirmDetected = { viewModel.confirmDetectedSubscription(it) },
        onRejectDetected = { viewModel.rejectDetectedSubscription(it) },
        onSelectSubscription = { viewModel.selectSubscriptionForDetail(it) },
        onOpenApp = { viewModel.openSubscriptionAppOrFallback(context, it) },
        onConfirmCancel = { viewModel.confirmManualCancellation(it) },
        onDismissCancel = { viewModel.showCancelConfirmation(null) },
        onShowCancelDialog = { viewModel.showCancelConfirmation(it) },
        onSaveManualSubscription = { name, amount, billingPeriod, category, packageName, nextDate ->
            viewModel.addManualSubscription(name, amount, billingPeriod, category, packageName, nextDate)
        },
        onDismissAddDialog = { viewModel.setShowAddDialog(false) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreenContent(
    uiState: SubscriptionUiState,
    onBack: () -> Unit,
    onRefreshUsage: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onSelectFilter: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddSubscriptionClick: () -> Unit,
    onConfirmDetected: (PotentialSubscription) -> Unit,
    onRejectDetected: (PotentialSubscription) -> Unit,
    onSelectSubscription: (SubscriptionUiModel) -> Unit,
    onOpenApp: (SubscriptionUiModel) -> Unit,
    onConfirmCancel: (SubscriptionUiModel) -> Unit,
    onDismissCancel: () -> Unit,
    onShowCancelDialog: (SubscriptionUiModel) -> Unit,
    onSaveManualSubscription: (String, Double, String, String, String, String) -> Unit,
    onDismissAddDialog: () -> Unit
) {
    var manageSheetSub by remember { mutableStateOf<SubscriptionUiModel?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Subscriptions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Text(
                            text = "Value for Money & Usage Insights",
                            fontSize = 11.sp,
                            color = TextBlueGrey
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextNavy
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshUsage) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextNavy
                        )
                    }
                    IconButton(onClick = onAddSubscriptionClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Subscription",
                            tint = TextNavy
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightBg,
                    titleContentColor = TextNavy
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Top Commitment Summary Card
            item {
                TopCommitmentCard(summary = uiState.summary)
            }

            // 2. App Inactivity Audit Banner
            item {
                UsageAuditBanner(
                    isGranted = uiState.usagePermissionGranted,
                    onRequestAccess = onRequestUsageAccess,
                    onRefresh = onRefreshUsage
                )
            }

            // 3. Price Change Alerts (if any detected)
            if (uiState.priceChangeAlerts.isNotEmpty()) {
                item {
                    PriceChangeBanner(alerts = uiState.priceChangeAlerts)
                }
            }

            // 4. Detected Recurring Candidate Payments
            if (uiState.potentialSubscriptions.isNotEmpty()) {
                item {
                    DetectedSubscriptionsCard(
                        candidates = uiState.potentialSubscriptions,
                        onConfirm = onConfirmDetected,
                        onReject = onRejectDetected
                    )
                }
            }

            // 5. Search Bar & Filter Chips
            item {
                FilterAndSearchSection(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    selectedFilter = uiState.selectedFilter,
                    onSelectFilter = onSelectFilter,
                    items = uiState.subscriptionItems
                )
            }

            // 6. Subscriptions List
            if (uiState.filteredItems.isEmpty()) {
                item {
                    EmptySubscriptionsCard(
                        hasAnySubscriptions = uiState.subscriptionItems.isNotEmpty(),
                        onAddClick = onAddSubscriptionClick
                    )
                }
            } else {
                items(uiState.filteredItems, key = { it.id }) { sub ->
                    SubscriptionItemCard(
                        sub = sub,
                        onManage = { manageSheetSub = sub },
                        onOpenApp = { onOpenApp(sub) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Manage Subscription Bottom Sheet
    manageSheetSub?.let { sub ->
        ManageSubscriptionBottomSheet(
            sub = sub,
            onDismiss = { manageSheetSub = null },
            onOpenApp = { onOpenApp(sub) },
            onCancelRequest = {
                manageSheetSub = null
                onShowCancelDialog(sub)
            }
        )
    }

    // Add Subscription Bottom Sheet
    if (uiState.showAddDialog) {
        AddSubscriptionBottomSheet(
            onDismiss = onDismissAddDialog,
            onSave = onSaveManualSubscription
        )
    }

    // Confirm Cancellation Dialog
    uiState.subToConfirmCancel?.let { sub ->
        AlertDialog(
            onDismissRequest = onDismissCancel,
            title = {
                Text(
                    text = "Confirm Local Cancellation",
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )
            },
            text = {
                Text(
                    text = "Mark '${sub.serviceName}' as cancelled in WealthWise? This updates your local financial planning. WealthWise does not modify real bank mandates.",
                    fontSize = 13.sp,
                    color = TextNavy
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmCancel(sub) },
                    colors = ButtonDefaults.buttonColors(containerColor = FintechRed)
                ) {
                    Text("Mark Cancelled", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCancel) {
                    Text("Keep Active")
                }
            }
        )
    }
}

@Composable
private fun TopCommitmentCard(summary: com.example.domain.subscription.SubscriptionSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = FintechNavyCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MONTHLY RECURRING COMMITMENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(FintechBlue.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${summary.activeServicesCount} Active Services",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${SubscriptionValueEvaluator.formatInr(summary.monthlyRecurringTotal)} / month",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Text(
                text = "Projected annual cost: ${SubscriptionValueEvaluator.formatInr(summary.annualRecurringTotal)} / year",
                fontSize = 12.sp,
                color = Slate400
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Slate800, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Potentially underutilized", fontSize = 11.sp, color = Slate400)
                    Text(
                        text = if (summary.wastedAnnualTotal > 0) {
                            "${SubscriptionValueEvaluator.formatInr(summary.wastedAnnualTotal)} / year"
                        } else {
                            "₹0/year · No measurable waste"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.wastedAnnualTotal > 0) FintechOrangeLight else FintechGreenLight
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Device Measurement", fontSize = 11.sp, color = Slate400)
                    Text(
                        text = "Rolling 30 days",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageAuditBanner(
    isGranted: Boolean,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .border(
                1.dp,
                if (isGranted) FintechGreen.copy(alpha = 0.3f) else FintechBlue.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) FintechGreenBg.copy(alpha = 0.4f) else Slate50
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isGranted) FintechGreenBg else Slate200),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = if (isGranted) FintechGreen else TextNavy,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isGranted) "App Usage Audit: Active" else "Enable App Usage Audit",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Text(
                            text = if (isGranted) {
                                "Synced with Android OS • Measuring foreground usage on this device"
                            } else {
                                "Allow WealthWise to measure how much time you spend in subscribed apps."
                            },
                            fontSize = 11.sp,
                            color = TextBlueGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (!isGranted) {
                    Button(
                        onClick = onRequestAccess,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FintechBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Enable Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Audit Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextNavy)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceChangeBanner(alerts: List<SubscriptionPriceChange>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FintechOrange.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = FintechOrangeBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = FintechOrange, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Price Increases Detected (${alerts.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            alerts.forEach { alert ->
                Text(
                    text = "• ${alert.name}: Increased from ₹${alert.oldAmount.toInt()} to ₹${alert.newAmount.toInt()} (+₹${alert.difference.toInt()}) on ${alert.detectedDate}",
                    fontSize = 11.sp,
                    color = TextNavy
                )
            }
        }
    }
}

@Composable
private fun DetectedSubscriptionsCard(
    candidates: List<PotentialSubscription>,
    onConfirm: (PotentialSubscription) -> Unit,
    onReject: (PotentialSubscription) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FintechBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = FintechBlueBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FintechBlue, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Recurring Payments Detected in Ledger",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "Found ${candidates.size} potential recurring subscription${if (candidates.size > 1) "s" else ""}",
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            candidates.forEach { candidate ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(candidate.merchant, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextNavy)
                            Text(
                                "₹${candidate.amount.toInt()} • ${candidate.billingPeriod.lowercase()}",
                                fontSize = 11.sp,
                                color = FintechBlue
                            )
                        }
                        Row {
                            TextButton(onClick = { onReject(candidate) }) {
                                Text("Dismiss", fontSize = 11.sp, color = TextBlueGrey)
                            }
                            Button(
                                onClick = { onConfirm(candidate) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Track", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterAndSearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    items: List<SubscriptionUiModel>
) {
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search subscriptions...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextBlueGrey) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = FintechBlue,
                unfocusedBorderColor = BorderLight
            )
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf(
                "ALL" to "All (${items.size})",
                "GREAT_VALUE" to "Great Value (${items.count { it.valueStatus == ValueRating.GREAT_VALUE }})",
                "MODERATE" to "Underutilized (${items.count { it.valueStatus == ValueRating.MODERATE }})",
                "WASTED" to "Unused (${items.count { it.valueStatus == ValueRating.WASTED }})",
                "UNKNOWN" to "Unavailable (${items.count { it.valueStatus == ValueRating.UNKNOWN }})",
                "CANCELLED" to "Cancelled (${items.count { it.status == "CANCELLED" }})"
            )
            items(filters) { (key, label) ->
                val isSelected = selectedFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilter(key) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FintechNavyCard,
                        selectedLabelColor = Color.White,
                        containerColor = Color.White,
                        labelColor = TextNavy
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) FintechNavyCard else BorderLight
                    )
                )
            }
        }
    }
}

@Composable
private fun SubscriptionItemCard(
    sub: SubscriptionUiModel,
    onManage: () -> Unit,
    onOpenApp: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                when (sub.valueStatus) {
                    ValueRating.WASTED -> FintechRed.copy(alpha = 0.35f)
                    ValueRating.MODERATE -> FintechOrange.copy(alpha = 0.35f)
                    ValueRating.GREAT_VALUE -> FintechGreen.copy(alpha = 0.35f)
                    ValueRating.UNKNOWN -> BorderLight
                },
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when (sub.valueStatus) {
                                    ValueRating.WASTED -> FintechRed.copy(alpha = 0.15f)
                                    ValueRating.MODERATE -> FintechOrange.copy(alpha = 0.15f)
                                    ValueRating.GREAT_VALUE -> FintechGreen.copy(alpha = 0.15f)
                                    ValueRating.UNKNOWN -> Slate200
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sub.serviceName.take(1).uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = when (sub.valueStatus) {
                                ValueRating.WASTED -> FintechRed
                                ValueRating.MODERATE -> FintechOrange
                                ValueRating.GREAT_VALUE -> FintechGreen
                                ValueRating.UNKNOWN -> TextNavy
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = sub.serviceName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${sub.category} • ${sub.billingPeriod.lowercase()}",
                            fontSize = 11.sp,
                            color = TextBlueGrey
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${SubscriptionValueEvaluator.formatInr(sub.monthlyPrice)}/mo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    if (sub.billingPeriod.uppercase() != "MONTHLY") {
                        Text(
                            text = "(${SubscriptionValueEvaluator.formatInr(sub.originalAmount)} billed ${sub.billingPeriod.lowercase()})",
                            fontSize = 10.sp,
                            color = Slate400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Usage & Cost Per Hour Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (sub.usageState) {
                            UsageState.ACTIVE_USAGE, UsageState.LOW_USAGE -> {
                                val hrs = "%.1f".format((sub.usageMinutesLast30Days ?: 0L) / 60.0)
                                "$hrs hrs used in last 30 days"
                            }
                            UsageState.NO_USAGE -> "No usage detected in last 30 days"
                            UsageState.APP_NOT_INSTALLED -> "App not installed on this device"
                            UsageState.UNAVAILABLE -> "Usage data unavailable"
                            UsageState.ERROR -> "Usage read error"
                        },
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )

                    if (sub.costPerHour != null) {
                        Text(
                            text = "Effective: ${SubscriptionValueEvaluator.formatCostPerHour(sub.costPerHour)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FintechBlue
                        )
                    }
                }

                // Value Rating Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (sub.valueStatus) {
                                ValueRating.GREAT_VALUE -> FintechGreenBg
                                ValueRating.MODERATE -> FintechOrangeBg
                                ValueRating.WASTED -> FintechRed.copy(alpha = 0.15f)
                                ValueRating.UNKNOWN -> Color(0xFFF1F5F9)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = sub.valueStatus.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (sub.valueStatus) {
                            ValueRating.GREAT_VALUE -> FintechGreen
                            ValueRating.MODERATE -> FintechOrange
                            ValueRating.WASTED -> FintechRed
                            ValueRating.UNKNOWN -> TextBlueGrey
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sub.nextDueDate > 0L) {
                    Text(
                        text = "Next: ${dateFormat.format(Date(sub.nextDueDate))}",
                        fontSize = 10.sp,
                        color = Slate400
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!sub.packageName.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onOpenApp,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Open App", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = onManage,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Manage", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySubscriptionsCard(
    hasAnySubscriptions: Boolean,
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Subscriptions,
                contentDescription = null,
                tint = TextBlueGrey,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (hasAnySubscriptions) "No matching subscriptions" else "No subscriptions detected",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasAnySubscriptions) {
                    "Try selecting a different filter or clearing search."
                } else {
                    "Add a subscription manually or allow WealthWise to identify recurring payments from your transaction history."
                },
                fontSize = 12.sp,
                color = TextBlueGrey,
                textAlign = TextAlign.Center
            )
            if (!hasAnySubscriptions) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard)
                ) {
                    Text("+ Add Subscription", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageSubscriptionBottomSheet(
    sub: SubscriptionUiModel,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
    onCancelRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Manage ${sub.serviceName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
            Text(
                text = "WealthWise provides management guidance. Subscriptions must be cancelled with the provider directly.",
                fontSize = 12.sp,
                color = TextBlueGrey
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate50),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("How to manage this subscription:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextNavy)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. If paid via Google Pay: Open Google Pay → Profile → Autopay → Select ${sub.serviceName} → Cancel.\n" +
                                "2. If paid via PhonePe: Open PhonePe → Profile → AutoPay → Select mandate → Pause/Remove.\n" +
                                "3. If paid via Paytm: Open Paytm → UPI & Payment Settings → Automatic Payments → Cancel.\n" +
                                "4. Otherwise, open ${sub.serviceName}'s official app or account settings.",
                        fontSize = 11.sp,
                        color = TextNavy,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: Menu names may vary slightly across app versions.",
                        fontSize = 10.sp,
                        color = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenApp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open App")
                }

                Button(
                    onClick = onCancelRequest,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = FintechRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Mark Cancelled", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionBottomSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, billingPeriod: String, category: String, packageName: String, nextDate: String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var billingPeriod by remember { mutableStateOf("MONTHLY") }
    var category by remember { mutableStateOf("Entertainment") }
    var packageName by remember { mutableStateOf("") }
    var nextDate by remember { mutableStateOf("") }

    val amount = amountStr.replace("₹", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
    val monthlyEquivalent = SubscriptionValueEvaluator.calculateMonthlyEquivalent(amount, billingPeriod)

    val isPackageInstalled = remember(packageName) {
        if (packageName.isNotBlank()) UsageHelper.isAppInstalled(context, packageName) else false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add Subscription",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )

            // Quick Suggestions
            Text("Quick Suggestions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextBlueGrey)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                items(SubscriptionValueEvaluator.POPULAR_SUGGESTIONS) { suggestion ->
                    SuggestionChip(
                        onClick = {
                            name = suggestion.name
                            packageName = suggestion.packageName
                            category = suggestion.category
                            amountStr = suggestion.defaultPrice.toInt().toString()
                            billingPeriod = suggestion.defaultBillingPeriod
                        },
                        label = { Text(suggestion.name, fontSize = 11.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Service Name (e.g. Netflix)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(10.dp)
            )

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Price") },
                prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Billing Period Selector
            Text("Billing Frequency", fontSize = 11.sp, color = TextBlueGrey)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MONTHLY", "QUARTERLY", "ANNUAL").forEach { period ->
                    FilterChip(
                        selected = billingPeriod == period,
                        onClick = { billingPeriod = period },
                        label = { Text(period, fontSize = 11.sp) }
                    )
                }
            }

            if (amount > 0) {
                Text(
                    text = "Planning monthly equivalent: ${SubscriptionValueEvaluator.formatInr(monthlyEquivalent)}/mo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = FintechBlue
                )
                Text(
                    text = "Actual ledger expense remains the full transaction amount.",
                    fontSize = 10.sp,
                    color = Slate400
                )
            }

            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text("Android Package Name (Optional for usage)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(10.dp)
            )
            if (packageName.isNotBlank()) {
                Text(
                    text = if (isPackageInstalled) "✓ App is installed on this device" else "⚠️ App not installed on this device",
                    fontSize = 10.sp,
                    color = if (isPackageInstalled) FintechGreen else FintechOrange
                )
            }

            OutlinedTextField(
                value = nextDate,
                onValueChange = { nextDate = it },
                label = { Text("Next Due Date (YYYY-MM-DD, optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSave(name, amount, billingPeriod, category, packageName, nextDate)
                },
                enabled = name.isNotBlank() && amount > 0.0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FintechNavyCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Subscription", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onSaveSubscription: (name: String, amount: Double, billingPeriod: String, category: String, linkedPackage: String, nextDate: String) -> Unit
) {
    AddSubscriptionBottomSheet(
        onDismiss = onDismiss,
        onSave = onSaveSubscription
    )
}