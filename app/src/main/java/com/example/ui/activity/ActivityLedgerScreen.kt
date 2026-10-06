package com.example.ui.activity

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.TransactionEntity
import com.example.domain.financial.PageAnalyticsEngine
import com.example.ui.components.charts.WealthWiseChartCard
import com.example.ui.components.charts.WealthWiseIncomeExpenseLineChart
import com.example.ui.components.charts.WealthWiseLineChart
import com.example.ui.components.charts.WealthWiseTimeRangeSelector
import com.example.ui.dashboard.formatInr
import com.example.ui.theme.*
import com.example.ui.viewmodel.ActivityViewModel

@Composable
fun ActivityLedgerScreen(
    transactions: List<TransactionEntity> = emptyList(),
    initialFilter: String = "ALL",
    currentBalance: Double = 0.0,
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    activityViewModel: ActivityViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val activityState by activityViewModel.uiState.collectAsState()
    val activeTxns = if (transactions.isNotEmpty()) transactions else activityState.transactions

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }

    // Primary filter state: ALL, INFLOW, OUTFLOW
    var selectedFilter by remember(initialFilter) {
        mutableStateOf(
            when (initialFilter.uppercase()) {
                "INFLOW", "INCOME" -> "INFLOW"
                "OUTFLOW", "EXPENSE" -> "OUTFLOW"
                else -> "ALL"
            }
        )
    }

    var spendingPeriod by remember { mutableStateOf("1 DAY") } // "1 DAY", "WEEK", "MONTH", "YEAR"

    // Real-time activity analytics across 1 DAY, WEEK, MONTH, YEAR
    val activityAnalytics = remember(activeTxns, spendingPeriod, selectedFilter) {
        PageAnalyticsEngine.calculateActivityAnalytics(activeTxns, spendingPeriod, selectedFilter)
    }

    val filteredTransactions = remember(activeTxns, searchQuery, selectedFilter) {
        val query = searchQuery.trim()
        activeTxns.filter { txn ->
            val matchesQuery = query.isEmpty() ||
                    txn.title.contains(query, ignoreCase = true) ||
                    txn.merchant.contains(query, ignoreCase = true) ||
                    txn.category.contains(query, ignoreCase = true) ||
                    txn.paymentMethod.contains(query, ignoreCase = true) ||
                    txn.amount.toString().contains(query) ||
                    txn.date.contains(query, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "INFLOW" -> txn.isInflow || txn.type.equals("INCOME", ignoreCase = true) || txn.type.equals("credit", ignoreCase = true)
                "OUTFLOW" -> txn.isOutflow || txn.type.equals("EXPENSE", ignoreCase = true) || txn.type.equals("debit", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // 1. Header with Back & CSV Export
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextNavy
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Activity Ledger",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                        Text(
                            text = "Real transaction ledger & audit trail",
                            fontSize = 12.sp,
                            color = TextBlueGrey
                        )
                    }
                }
            }
        }

        // 2. Active Passbook Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = FintechNavyCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Current Balance",
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatInr(currentBalance),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Inflow",
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+${formatInr(totalIncome)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FintechGreenLight
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Outflow",
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "-${formatInr(totalExpenses)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = FintechRedLight
                        )
                    }
                }
            }
        }

        // 3. Primary Filter Row: ALL / INFLOW / OUTFLOW
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "ALL",
                    "INFLOW" to "INFLOW",
                    "OUTFLOW" to "OUTFLOW"
                ).forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (key) {
                                "INFLOW" -> FintechGreenBg
                                "OUTFLOW" -> FintechRedBg
                                else -> FintechBlueBg
                            },
                            selectedLabelColor = when (key) {
                                "INFLOW" -> FintechGreen
                                "OUTFLOW" -> FintechRed
                                else -> FintechBlue
                            },
                            containerColor = CardWhite,
                            labelColor = TextBlueGrey
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = BorderLight,
                            selectedBorderColor = when (key) {
                                "INFLOW" -> FintechGreen
                                "OUTFLOW" -> FintechRed
                                else -> FintechBlue
                            }
                        )
                    )
                }
            }
        }

        // 4. Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search merchant, category, amount...", fontSize = 13.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextBlueGrey) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextBlueGrey)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FintechBlue,
                    unfocusedBorderColor = BorderLight,
                    focusedTextColor = TextNavy,
                    unfocusedTextColor = TextNavy,
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 5. Activity Graph Section
        item {
            WealthWiseChartCard(
                title = activityAnalytics.title,
                subtitle = activityAnalytics.subtitle,
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = activityAnalytics.averageLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBlueGrey
                        )
                        Text(
                            text = formatInr(activityAnalytics.averageValue),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (selectedFilter) {
                                "INFLOW" -> FintechGreen
                                "OUTFLOW" -> FintechRed
                                else -> FintechBlue
                            }
                        )
                    }
                }
            ) {
                WealthWiseTimeRangeSelector(
                    selectedRange = spendingPeriod,
                    ranges = listOf(
                        "1 DAY" to "1 DAY",
                        "WEEK" to "WEEK",
                        "MONTH" to "MONTH",
                        "YEAR" to "YEAR"
                    ),
                    onSelectRange = { spendingPeriod = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedFilter == "ALL") {
                    WealthWiseIncomeExpenseLineChart(
                        points = activityAnalytics.dualPoints,
                        emptyMessage = activityAnalytics.emptyMessage,
                        height = 150.dp
                    )
                } else {
                    WealthWiseLineChart(
                        points = activityAnalytics.chartPoints,
                        lineColor = if (selectedFilter == "INFLOW") FintechGreen else FintechRed,
                        emptyMessage = activityAnalytics.emptyMessage,
                        height = 150.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedFilter == "ALL") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Inflow: ${formatInr(activityAnalytics.totalInflow)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FintechGreen
                            )
                            Text(
                                text = "Outflow: ${formatInr(activityAnalytics.totalOutflow)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = FintechRed
                            )
                        }
                    } else {
                        Text(
                            text = activityAnalytics.totalLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextNavy
                        )
                    }
                    Text(
                        text = "${activityAnalytics.unitCount} ${activityAnalytics.unitName}",
                        fontSize = 10.sp,
                        color = TextBlueGrey
                    )
                }
            }
        }

        // 6. Transactions Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSACTIONS (${filteredTransactions.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextBlueGrey,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Read-Only Ledger",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
            }
        }

        // 7. Transactions List
        if (filteredTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No matching transactions found",
                            color = TextBlueGrey,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(filteredTransactions, key = { it.id }) { txn ->
                TransactionItemRow(
                    txn = txn,
                    onDelete = null
                )
            }
        }
    }
}

/**
 * Authoritative, reusable Transaction Row for Activity Ledger and Passbook views.
 */
@Composable
fun TransactionItemRow(
    txn: TransactionEntity,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val isGoalReserve = txn.isGoalTransfer ||
            txn.type.equals("GOAL_RESERVE", ignoreCase = true) ||
            txn.type.equals("GOAL_ALLOCATION", ignoreCase = true) ||
            txn.type.equals("GOAL_TRANSFER", ignoreCase = true) ||
            txn.eventType.equals("GOAL_TRANSFER", ignoreCase = true)
    val isIncome = (txn.isInflow || txn.type.equals("INCOME", ignoreCase = true) || txn.type.equals("credit", ignoreCase = true)) && !isGoalReserve

    val icon: ImageVector = when {
        isGoalReserve -> Icons.Default.Savings
        txn.category.lowercase().contains("food") || txn.category.lowercase().contains("dining") -> Icons.Default.Restaurant
        txn.category.lowercase().contains("shop") -> Icons.Default.ShoppingBag
        txn.category.lowercase().contains("transport") || txn.category.lowercase().contains("travel") -> Icons.Default.DirectionsCar
        txn.category.lowercase().contains("bill") || txn.category.lowercase().contains("recharge") -> Icons.AutoMirrored.Filled.ReceiptLong
        txn.category.lowercase().contains("health") || txn.category.lowercase().contains("medical") -> Icons.Default.MedicalServices
        txn.category.lowercase().contains("entertainment") || txn.category.lowercase().contains("movie") -> Icons.Default.Movie
        else -> if (isIncome) Icons.Default.ArrowUpward else Icons.Default.Payment
    }

    val badgeBg = when {
        isIncome -> FintechGreenBg
        isGoalReserve -> FintechTealBg
        else -> CardWhite
    }

    val badgeTint = when {
        isIncome -> FintechGreen
        isGoalReserve -> FintechTeal
        else -> TextNavy
    }

    val amountColor = when {
        isIncome -> FintechGreen
        isGoalReserve -> FintechTeal
        else -> FintechRed
    }

    val amountPrefix = when {
        isIncome -> "+"
        isGoalReserve -> "→ "
        else -> "-"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LightBg)
            .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg)
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = txn.merchant.ifEmpty { txn.title },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )
                Text(
                    text = if (isGoalReserve) "Goal Savings • ${txn.date}" else "${txn.category} • ${txn.paymentMethod.ifBlank { "UPI" }} • ${txn.date}",
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix${formatInr(kotlin.math.abs(txn.amount))}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (txn.status.isNotBlank()) txn.status else "COMPLETED",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = FintechRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}