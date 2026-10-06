package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BillEntity
import com.example.data.local.GoalEntity
import com.example.data.local.LoanEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import com.example.domain.financial.HomeAnalyticsResult
import com.example.domain.financial.IncomeExpensePoint
import com.example.domain.financial.PageAnalyticsEngine
import com.example.ui.components.FloatingNotificationItem
import com.example.ui.components.FloatingStackedNotificationOverlay
import com.example.ui.components.charts.WealthWiseIncomeExpenseLineChart
import com.example.ui.components.charts.WealthWiseTimeRangeSelector
import com.example.ui.theme.*
import com.example.ui.viewmodel.CategorySpending
import com.example.ui.viewmodel.HomeFinancialState
import com.example.ui.viewmodel.WealthWiseUiState
import java.util.Locale

// Utility for Indian Rupee number formatting: e.g. ₹31,148, ₹1,00,000, ₹742
fun formatInr(amount: Double, forceDecimals: Boolean = false): String {
    if (amount.isNaN() || amount.isInfinite()) return "₹0"
    val isNegative = amount < 0
    val absVal = kotlin.math.abs(amount)
    
    // Exact paise calculation to prevent rounding overflows
    val roundedTotalPaise = kotlin.math.round(absVal * 100).toLong()
    val integerPart = roundedTotalPaise / 100
    val decimalPart = (roundedTotalPaise % 100).toInt()

    val str = integerPart.toString()
    val formattedInt = if (str.length <= 3) {
        str
    } else {
        val lastThree = str.substring(str.length - 3)
        val rest = str.substring(0, str.length - 3)
        val sb = StringBuilder()
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            sb.append(rest[i])
            count++
            if (count % 2 == 0 && i != 0) {
                sb.append(',')
            }
        }
        sb.reverse().append(',').append(lastThree).toString()
    }

    val sign = if (isNegative) "-₹" else "₹"
    return if (decimalPart > 0 || forceDecimals) {
        val decStr = String.format(Locale.US, "%02d", decimalPart)
        "$sign$formattedInt.$decStr"
    } else {
        "$sign$formattedInt"
    }
}

private data class CategoryDef(
    val name: String,
    val defaultLimit: Double,
    val icon: ImageVector
)

@Composable
fun HomeDashboardScreen(
    state: WealthWiseUiState,
    homeState: HomeFinancialState? = null,
    onAddIncome: () -> Unit = {},
    onAddExpense: () -> Unit = {},
    onViewReports: () -> Unit = {},
    onAdjustBudget: () -> Unit = {},
    onReviewSubscriptions: () -> Unit = {},
    onCancelSubscription: (SubscriptionEntity) -> Unit = {},
    onPayBill: (BillEntity) -> Unit = {},
    onFundGoal: (GoalEntity) -> Unit = {},
    onAddGoal: () -> Unit = {},
    onViewFullLedger: () -> Unit = {},
    onDismissNotification: (String, Int?) -> Unit = { _, _ -> },
    onOpenDrawer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onOpenUniversalSearch: () -> Unit = {},
    onOpenTotalBalance: () -> Unit = {},
    onOpenIncome: () -> Unit = {},
    onOpenExpenses: () -> Unit = {},
    onOpenSavings: () -> Unit = {},
    onOpenInvestments: () -> Unit = {},
    onOpenLoans: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Authoritative Single Source of Truth from HomeViewModel / Central Financial Calculation Engine
    val actualBalance = homeState?.actualBalance ?: state.overview.currentBalance.toDouble()
    val safeToSpend = homeState?.safeToSpend ?: state.overview.safeToSpend.toDouble()
    val availableBalance = homeState?.availableBalance ?: state.overview.availableBalance.toDouble()
    val totalInflow = homeState?.totalIncome ?: state.overview.totalIncome.toDouble()
    val totalOutflow = homeState?.totalExpense ?: state.overview.totalExpenses.toDouble()
    val totalSavings = state.overview.totalSavings.toDouble()
    val totalMonthlyBudget = homeState?.totalBudget ?: state.overview.totalPlannedBudget.toDouble()
    val investmentsTotal = state.overview.totalInvestments.toDouble()
    val loansRemaining = homeState?.totalRemainingDebt ?: state.loansRemaining
    val activeLoansCount = homeState?.activeLoanCount ?: state.loans.count { !it.isClosed }
    val firstActiveLoan = homeState?.activeLoans?.firstOrNull() ?: state.loans.firstOrNull { !it.isClosed }

    // Notification queue for floating overlay
    val activeNotificationList = remember(state.notifications, state.dismissedSessionNotificationIds) {
        state.notifications
            .filter { notif -> "notif_${notif.id}" !in state.dismissedSessionNotificationIds }
            .map { notif ->
                FloatingNotificationItem(
                    key = "notif_${notif.id}",
                    title = notif.title,
                    message = notif.message,
                    type = notif.type,
                    entityId = notif.id,
                    onAction = when (notif.type) {
                        "ALERT" -> onReviewSubscriptions
                        "BILL" -> {
                            val matchingBill = state.bills.find { it.id == notif.id } ?: state.bills.firstOrNull()
                            if (matchingBill != null) {
                                { onPayBill(matchingBill) }
                            } else null
                        }
                        else -> null
                    }
                )
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 1. TOP HEADER
            // ==========================================
            HomeHeaderSection(
                onOpenDrawer = onOpenDrawer,
                onSearch = onOpenUniversalSearch,
                onOpenSettings = onOpenSettings
            )

            // ==========================================
            // 2. UNIVERSAL SEARCH (NAVIGATION + REAL DATA)
            // ==========================================
            HomeUniversalSearchBar(
                onClick = onOpenUniversalSearch
            )

            // ==========================================
            // 3. HOME BALANCE CARD (AVAILABLE / SAFE TO SPEND / ACTUAL)
            // ==========================================
            HomeBalanceCard(
                availableBalance = availableBalance,
                safeToSpend = safeToSpend,
                actualBalance = actualBalance,
                onClick = onOpenTotalBalance,
                onEditBalance = onAddIncome
            )

            // ==========================================
            // 4. SUMMARY CARDS (2-COLUMN GRID)
            // ==========================================
            SummaryCardsGrid(
                income = totalInflow,
                expenses = totalOutflow,
                savings = totalSavings,
                budget = totalMonthlyBudget,
                investments = investmentsTotal,
                loans = loansRemaining,
                onOpenIncome = onOpenIncome,
                onOpenExpenses = onOpenExpenses,
                onOpenSavings = onOpenSavings,
                onOpenBudget = onOpenBudgets,
                onOpenInvestments = onOpenInvestments,
                onOpenLoans = onOpenLoans
            )

            // ==========================================
            // 5. INCOME VS EXPENSE
            // ==========================================
            IncomeVsExpenseCard(
                income = homeState?.monthlyIncome ?: totalInflow,
                expenses = homeState?.monthlyExpense ?: totalOutflow,
                transactions = homeState?.monthlyTransactions ?: state.transactions,
                chartPoints = homeState?.chartPoints ?: emptyList(),
                onClick = onOpenTotalBalance,
                onOpenIncome = onOpenIncome,
                onOpenExpenses = onOpenExpenses
            )


        }

        // Stacked notifications overlay if active
        if (activeNotificationList.isNotEmpty()) {
            FloatingStackedNotificationOverlay(
                notifications = activeNotificationList,
                onDismiss = { item -> onDismissNotification(item.key, item.entityId) }
            )
        }
    }
}

// =========================================================================
// 1. TOP HEADER COMPONENT
// =========================================================================
@Composable
private fun HomeHeaderSection(
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Hamburger menu icon
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = TextNavy,
                modifier = Modifier.size(24.dp)
            )
        }

        // Center/Left Brand Area: Logo + Name + Subtitle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FintechNavy),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = "WealthWise Logo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "WealthWise",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )
                Text(
                    text = "Personal Finance & Sync",
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            }
        }

        // Right: Settings icon
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextNavy,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// =========================================================================
// 2. UNIVERSAL SEARCH BAR COMPONENT
// =========================================================================
@Composable
private fun HomeUniversalSearchBar(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = FintechNavy,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Search transactions, goals, budgets, loans...",
                    fontSize = 13.sp,
                    color = TextMuted,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(FintechBlueBg)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "SEARCH",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = FintechBlue
                )
            }
        }
    }
}

// =========================================================================
// 3. HOME BALANCE CARD COMPONENT
// =========================================================================
@Composable
private fun HomeBalanceCard(
    availableBalance: Double,
    safeToSpend: Double = 0.0,
    actualBalance: Double = 0.0,
    onClick: () -> Unit = {},
    onEditBalance: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_balance_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FintechNavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Wallet icon + "AVAILABLE BALANCE"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AVAILABLE BALANCE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. PRIMARY VALUE: AVAILABLE BALANCE
            Text(
                text = formatInr(availableBalance),
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.testTag("available_balance_value")
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFF1F2937)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. SAFE TO SPEND
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(FintechGreenLight)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Safe to Spend",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate200
                    )
                }

                Text(
                    text = formatInr(safeToSpend),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FintechGreenLight,
                    modifier = Modifier.testTag("safe_to_spend_value")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. ACTUAL BALANCE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Actual Bank Balance",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )

                Text(
                    text = formatInr(actualBalance),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate200,
                    modifier = Modifier.testTag("actual_balance_value")
                )
            }
        }
    }
}

// =========================================================================
// 4. SUMMARY CARDS GRID COMPONENT (2 COLUMNS)
// =========================================================================
@Composable
private fun SummaryCardsGrid(
    income: Double,
    expenses: Double,
    savings: Double,
    budget: Double,
    investments: Double,
    loans: Double,
    onOpenIncome: () -> Unit = {},
    onOpenExpenses: () -> Unit = {},
    onOpenSavings: () -> Unit = {},
    onOpenBudget: () -> Unit = {},
    onOpenInvestments: () -> Unit = {},
    onOpenLoans: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Income (Green) | Expenses (Red) - Strictly View-Only
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCardItem(
                title = "Income",
                amount = "+${formatInr(income)}",
                accentColor = FintechGreen,
                badgeBg = FintechGreenBg,
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f),
                onClick = null
            )
            SummaryCardItem(
                title = "Expenses",
                amount = formatInr(expenses),
                accentColor = FintechRed,
                badgeBg = FintechRedBg,
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f),
                onClick = null
            )
        }

        // Row 2: Total Savings (Teal) | Monthly Budget (Blue) - Strictly View-Only
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCardItem(
                title = "Total Savings",
                amount = formatInr(savings),
                accentColor = FintechTeal,
                badgeBg = FintechTealBg,
                icon = Icons.Default.Savings,
                modifier = Modifier.weight(1f),
                onClick = null
            )
            SummaryCardItem(
                title = "Monthly Budget",
                amount = formatInr(budget),
                accentColor = FintechBlue,
                badgeBg = FintechBlueBg,
                icon = Icons.Default.AccountBalanceWallet,
                modifier = Modifier.weight(1f),
                onClick = null
            )
        }
    }
}

@Composable
private fun SummaryCardItem(
    title: String,
    amount: String,
    accentColor: Color,
    badgeBg: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) {
            modifier.clickable { onClick() }
        } else {
            modifier
        },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 12.sp,
                color = TextBlueGrey,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = amount,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

// =========================================================================
// 5. INCOME VS EXPENSE COMPONENT
// =========================================================================
@Composable
private fun IncomeVsExpenseCard(
    income: Double,
    expenses: Double,
    transactions: List<TransactionEntity> = emptyList(),
    chartPoints: List<IncomeExpensePoint> = emptyList(),
    onClick: () -> Unit = {},
    onOpenIncome: () -> Unit = {},
    onOpenExpenses: () -> Unit = {}
) {
    var selectedPeriod by remember { mutableStateOf("THIS_MONTH") }
    val analytics = remember(transactions, selectedPeriod, income, expenses, chartPoints) {
        if (selectedPeriod == "THIS_MONTH" && chartPoints.isNotEmpty()) {
            HomeAnalyticsResult(
                selectedPeriod = "THIS_MONTH",
                points = chartPoints,
                totalIncome = income,
                totalExpenses = expenses,
                netCashflow = income - expenses,
                hasData = chartPoints.any { it.income > 0.0 || it.expense > 0.0 }
            )
        } else if (selectedPeriod == "THIS_MONTH") {
            val res = PageAnalyticsEngine.calculateIncomeVsExpenses(transactions, selectedPeriod)
            if (res.points.isNotEmpty() && res.points.any { it.income > 0.0 || it.expense > 0.0 }) {
                res
            } else {
                HomeAnalyticsResult(
                    selectedPeriod = "THIS_MONTH",
                    points = emptyList(),
                    totalIncome = income,
                    totalExpenses = expenses,
                    netCashflow = income - expenses,
                    hasData = false
                )
            }
        } else {
            PageAnalyticsEngine.calculateIncomeVsExpenses(transactions, selectedPeriod)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Income vs Expenses",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "Inflows vs outflows from real transactions",
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                }

                Text(
                    text = "Net: ${if (analytics.netCashflow >= 0) "+" else ""}${formatInr(analytics.netCashflow)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (analytics.netCashflow >= 0) FintechGreen else FintechRed
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Period selector: [This Month] [Last Month] [3M] [6M] [This Year]
            WealthWiseTimeRangeSelector(
                selectedRange = selectedPeriod,
                ranges = listOf(
                    "THIS_MONTH" to "This Month",
                    "LAST_MONTH" to "Last Month",
                    "LAST_3_MONTHS" to "3M",
                    "LAST_6_MONTHS" to "6M",
                    "THIS_YEAR" to "This Year"
                ),
                onSelectRange = { selectedPeriod = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-row: Period Income vs Period Expense values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenIncome() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(FintechGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Income: ${formatInr(analytics.totalIncome)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FintechGreen
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenExpenses() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(FintechRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Expenses: ${formatInr(analytics.totalExpenses)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FintechRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Line Chart
            WealthWiseIncomeExpenseLineChart(
                points = analytics.points,
                emptyMessage = "No income or expense data yet."
            )
        }
    }
}

// =========================================================================
// 6. MONTHLY BUDGET STATUS COMPONENT
// =========================================================================
@Composable
private fun MonthlyBudgetStatusCard(
    totalBudget: Double,
    totalSpent: Double,
    onClick: () -> Unit = {}
) {
    val isExceeded = totalSpent > totalBudget && totalBudget > 0
    val percentUsed = if (totalBudget > 0) ((totalSpent / totalBudget) * 100).toInt() else 0
    val remaining = (totalBudget - totalSpent).coerceAtLeast(0.0)
    val overrun = (totalSpent - totalBudget).coerceAtLeast(0.0)
    val progressRatio = if (totalBudget > 0) (totalSpent / totalBudget).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight)
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
                Text(
                    text = "Monthly Budget Status",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Text(
                    text = if (isExceeded) "Exceeded ($percentUsed%)" else "$percentUsed% Used",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isExceeded) FintechRed else FintechBlue
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linear progress bar
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isExceeded) FintechRed else FintechBlue,
                trackColor = BorderLight
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Spent vs Remaining row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spent: ${formatInr(totalSpent)}",
                    fontSize = 12.sp,
                    color = TextBlueGrey
                )

                if (isExceeded) {
                    Text(
                        text = "Over: ${formatInr(overrun)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = FintechRed
                    )
                } else {
                    Text(
                        text = "Remaining: ${formatInr(remaining)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FintechGreen
                    )
                }
            }
        }
    }
}

// =========================================================================
// 7. SAVINGS GOAL + LOAN
// =========================================================================
@Composable
private fun SavingsGoalAndLoanRow(
    activeGoal: GoalEntity?,
    activeLoan: LoanEntity?,
    activeLoanCount: Int = 0,
    loansRemaining: Double,
    onFundGoal: (GoalEntity) -> Unit,
    onAddGoal: () -> Unit,
    onOpenGoals: () -> Unit = {},
    onOpenLoans: () -> Unit = {}
) {
    val hasActiveLoans = (activeLoan != null || activeLoanCount > 0) && loansRemaining > 0.0

    if (hasActiveLoans) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SavingsGoalCard(
                activeGoal = activeGoal,
                onAddGoal = onAddGoal,
                onOpenGoals = onOpenGoals,
                modifier = Modifier.weight(1f)
            )

            LoanSummaryCard(
                activeLoan = activeLoan,
                activeLoanCount = activeLoanCount,
                loansRemaining = loansRemaining,
                onOpenLoans = onOpenLoans,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        SavingsGoalCard(
            activeGoal = activeGoal,
            onAddGoal = onAddGoal,
            onOpenGoals = onOpenGoals,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SavingsGoalCard(
    activeGoal: GoalEntity?,
    onAddGoal: () -> Unit,
    onOpenGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onOpenGoals() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "Savings Goal",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (activeGoal != null) {
                val goalPercent = if (activeGoal.targetAmount > 0) {
                    ((activeGoal.savedAmount / activeGoal.targetAmount) * 100).toInt().coerceIn(0, 100)
                } else 0

                Text(
                    text = activeGoal.title,
                    fontSize = 12.sp,
                    color = TextBlueGrey
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$goalPercent% achieved",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FintechTeal
                )

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { (goalPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = FintechTeal,
                    trackColor = BorderLight
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${formatInr(activeGoal.savedAmount)} / ${formatInr(activeGoal.targetAmount)}",
                    fontSize = 11.sp,
                    color = TextBlueGrey
                )
            } else {
                Text(
                    text = "No active goals",
                    fontSize = 12.sp,
                    color = TextBlueGrey
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "+ Create Goal",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FintechBlue,
                    modifier = Modifier.clickable { onAddGoal() }
                )
            }
        }
    }
}

@Composable
private fun LoanSummaryCard(
    activeLoan: LoanEntity?,
    activeLoanCount: Int,
    loansRemaining: Double,
    onOpenLoans: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onOpenLoans() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Loans & Debt",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Loans",
                    tint = TextBlueGrey,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Loans Remaining",
                fontSize = 11.sp,
                color = TextBlueGrey
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formatInr(loansRemaining),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = FintechOrange
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$activeLoanCount active loan${if (activeLoanCount > 1) "s" else ""}",
                fontSize = 11.sp,
                color = TextBlueGrey
            )
        }
    }
}

// =========================================================================
// 8. SMART BUDGET INSIGHTS COMPONENT
// =========================================================================
@Composable
private fun SmartBudgetInsightsSection(
    topCategories: List<CategorySpending>,
    smartSuggestion: String?,
    onManageBudgets: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Smart Budget Insights",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    Text(
                        text = "Top active spending this month",
                        fontSize = 11.sp,
                        color = TextBlueGrey
                    )
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

            if (topCategories.isEmpty()) {
                Text(
                    text = "No spending recorded this month yet.",
                    fontSize = 12.sp,
                    color = TextBlueGrey,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                topCategories.forEach { item ->
                    val progressRatio = if (item.limit > 0) {
                        (item.spent / item.limit).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextNavy
                            )

                            Text(
                                text = if (item.limit > 0) {
                                    "${formatInr(item.spent)} / ${formatInr(item.limit)}"
                                } else {
                                    formatInr(item.spent)
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.isExceeded) FintechRed else TextNavy
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (item.isExceeded) FintechRed else FintechBlue,
                            trackColor = BorderLight
                        )

                        if (item.isExceeded) {
                            Text(
                                text = "Exceeded by ${formatInr(item.overrun)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = FintechRed
                            )
                        } else if (item.limit > 0) {
                            Text(
                                text = "Remaining: ${formatInr(item.remaining)} · ${item.percentUsed}% used",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Dynamic Smart Suggestion Chip (only if meaningful spending exists)
            if (!smartSuggestion.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(FintechTealBg)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = smartSuggestion,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextNavy
                    )
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

            // Direct Navigation to Budgets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onManageBudgets() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Manage All Budgets & Categories →",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FintechBlue
                )
            }
        }
    }
}

// =========================================================================
// 9. RECENT TRANSACTIONS COMPONENT
// =========================================================================
@Composable
private fun HomeRecentTransactionsList(
    transactions: List<TransactionEntity>,
    onViewAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: "Recent Transactions" | "View All →"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Transactions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onViewAll() }
                ) {
                    Text(
                        text = "View All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FintechBlue
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View All Transactions",
                        tint = FintechBlue,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)

            val recentList = transactions.take(5)

            if (recentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No transactions yet",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBlueGrey
                        )
                        Text(
                            text = "Transactions parsed from notifications will appear here",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            } else {
                recentList.forEach { txn ->
                    val isGoalReserve = txn.isGoalTransfer ||
                                        txn.type.equals("GOAL_RESERVE", ignoreCase = true) || 
                                        txn.type.equals("GOAL_ALLOCATION", ignoreCase = true) ||
                                        txn.type.equals("GOAL_TRANSFER", ignoreCase = true) ||
                                        txn.eventType.equals("GOAL_TRANSFER", ignoreCase = true)
                    val isIncome = (txn.isInflow || txn.type.equals("INCOME", ignoreCase = true)) && !isGoalReserve

                    val icon = when {
                        isGoalReserve -> Icons.Default.Savings
                        txn.category.lowercase() == "food" -> Icons.Default.Restaurant
                        txn.category.lowercase() == "shopping" -> Icons.Default.ShoppingBag
                        txn.category.lowercase() == "transport" -> Icons.Default.DirectionsCar
                        txn.category.lowercase() == "bills" -> Icons.AutoMirrored.Filled.ReceiptLong
                        txn.category.lowercase() == "health" -> Icons.Default.MedicalServices
                        txn.category.lowercase() == "entertainment" -> Icons.Default.Movie
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LightBg)
                            .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Icon + Merchant & Category details
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
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
                                    text = if (isGoalReserve) "Savings Goal Transfer" else "${txn.category} → ${txn.paymentMethod} · App Notification",
                                    fontSize = 11.sp,
                                    color = TextBlueGrey
                                )
                            }
                        }

                        // Right: Amount and status
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$amountPrefix${formatInr(kotlin.math.abs(txn.amount))}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = amountColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Completed",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}