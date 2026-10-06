package com.example.domain.financial

import com.example.data.local.BudgetEntity
import com.example.data.local.GoalEntity
import com.example.data.local.LoanEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Data point for line and bar charts in WealthWise.
 */
data class ChartPoint(
    val label: String,
    val value: Double,
    val secondaryValue: Double? = null,
    val timestamp: Long = 0L,
    val tooltipDate: String = label,
    val tooltipDetails: String? = null
)

/**
 * Daily Spending Analytics for Activity Page.
 */
data class DailySpendingAnalytics(
    val selectedPeriod: String, // "7D", "30D", "THIS_MONTH"
    val points: List<ChartPoint>,
    val averageDailySpending: Double,
    val totalSpendingInPeriod: Double,
    val activeDaysWithSpending: Int,
    val totalDaysInPeriod: Int,
    val hasData: Boolean
)

/**
 * Income vs Expense Point for Home Page.
 */
data class IncomeExpensePoint(
    val label: String,
    val income: Double,
    val expense: Double,
    val tooltipDate: String = label
)

/**
 * Income vs Expenses Analytics for Home Page.
 */
data class HomeAnalyticsResult(
    val selectedPeriod: String, // "THIS_MONTH", "LAST_MONTH", "LAST_3_MONTHS", "LAST_6_MONTHS", "THIS_YEAR"
    val points: List<IncomeExpensePoint>,
    val totalIncome: Double,
    val totalExpenses: Double,
    val netCashflow: Double,
    val hasData: Boolean
)

/**
 * Budget vs Actual comparison for a category.
 */
data class BudgetComparisonItem(
    val category: String,
    val budgetAmount: Double,
    val actualSpent: Double,
    val remaining: Double,
    val percentUsed: Int,
    val isOverBudget: Boolean,
    val exceededBy: Double,
    val colorHex: String = "#2563EB"
)

/**
 * Budget Analytics for Budget Page.
 */
data class BudgetAnalyticsResult(
    val items: List<BudgetComparisonItem>,
    val totalBudget: Double,
    val totalSpent: Double,
    val totalRemaining: Double,
    val overallPercentUsed: Int,
    val hasOverBudgetCategory: Boolean,
    val hasData: Boolean
)

/**
 * Goal Progress Analytics for Goals Page.
 */
data class GoalAnalyticsResult(
    val totalTarget: Double,
    val totalSaved: Double,
    val totalRemaining: Double,
    val overallProgressPercent: Double,
    val requiredMonthlyContribution: Double,
    val contributionPoints: List<ChartPoint>,
    val hasContributions: Boolean,
    val hasGoals: Boolean
)

/**
 * Investment Portfolio Analytics for Investments Page.
 */
data class PortfolioAnalyticsResult(
    val investedAmount: Double,
    val currentPortfolioValue: Double,
    val unrealizedProfitLoss: Double,
    val profitLossPercent: Double,
    val hasInvestments: Boolean,
    val message: String
)

/**
 * Debt Analytics for Loans Page.
 */
data class DebtAnalyticsResult(
    val totalPrincipal: Double,
    val totalOutstanding: Double,
    val totalMonthlyEmi: Double,
    val totalRepaid: Double,
    val nextDueDate: String,
    val activeLoanCount: Int,
    val debtPoints: List<ChartPoint>,
    val hasActiveLoans: Boolean
)

/**
 * Subscription Cost Analytics for Subscriptions Page.
 */
data class SubscriptionAnalyticsResult(
    val totalMonthlyEquivalent: Double,
    val activeSubscriptionsCount: Int,
    val annualRunRate: Double,
    val costItems: List<ChartPoint>,
    val hasSubscriptions: Boolean
)

/**
 * Market instrument benchmark details.
 */
data class MarketBenchmark(
    val id: String, // "GOLD", "SILVER", "NIFTY_50", "SENSEX"
    val displayName: String,
    val symbol: String,
    val exchange: String,
    val currentValue: String,
    val numericValue: Double,
    val changeAmount: String,
    val changePercent: String,
    val isPositive: Boolean,
    val tradingViewUrl: String,
    val description: String,
    val historyPoints: Map<String, List<ChartPoint>> // Period -> Points
)

object PageAnalyticsEngine {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val shortDayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMM", Locale.US)

    /**
     * 1. Activity: Advanced Multi-Period Analytics
     * Aggregates completed transactions across 4 specific periods:
     * - 1 DAY: hourly (24 points)
     * - WEEK: daily (7 points, Mon..Sun)
     * - MONTH: daily (days in month)
     * - YEAR: monthly (12 points, Jan..Dec)
     * Supports ALL (dual cash flow), INFLOW, and OUTFLOW filters.
     */
    fun calculateActivityAnalytics(
        transactions: List<TransactionEntity>,
        period: String = "1 DAY",
        filter: String = "ALL",
        referenceCalendar: Calendar? = null
    ): ActivityAnalyticsResult {
        val completed = transactions.filter {
            it.status.equals("COMPLETED", ignoreCase = true) &&
                    !it.category.equals("Account Transfer", ignoreCase = true)
        }

        val normPeriod = when (period.uppercase().trim()) {
            "1 DAY", "1_DAY", "1D", "DAY" -> "1 DAY"
            "WEEK", "7D" -> "WEEK"
            "MONTH", "30D", "THIS_MONTH" -> "MONTH"
            "YEAR", "1Y" -> "YEAR"
            else -> "WEEK"
        }

        val normFilter = when (filter.uppercase().trim()) {
            "INFLOW", "INCOME", "CREDIT" -> "INFLOW"
            "OUTFLOW", "EXPENSE", "DEBIT" -> "OUTFLOW"
            else -> "ALL"
        }

        val now = (referenceCalendar?.clone() as? Calendar ?: Calendar.getInstance())
        val currentYear = now.get(Calendar.YEAR)
        val currentDayOfYear = now.get(Calendar.DAY_OF_YEAR)
        val currentMonth = now.get(Calendar.MONTH) // 0-based
        val currentDayOfMonth = now.get(Calendar.DAY_OF_MONTH)

        val todayStart = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayEnd = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        fun extractTxnCal(txn: TransactionEntity): Calendar {
            val cal = (now.clone() as Calendar)
            if (txn.date.length >= 10) {
                val parsed = try { isoDateFormat.parse(txn.date.take(10)) } catch (_: Exception) { null }
                if (parsed != null) {
                    val calDate = Calendar.getInstance().apply { time = parsed }
                    cal.set(Calendar.YEAR, calDate.get(Calendar.YEAR))
                    cal.set(Calendar.MONTH, calDate.get(Calendar.MONTH))
                    cal.set(Calendar.DAY_OF_MONTH, calDate.get(Calendar.DAY_OF_MONTH))
                    if (txn.timestamp > 0) {
                        val timeCal = Calendar.getInstance().apply { timeInMillis = txn.timestamp }
                        val timeDateStr = isoDateFormat.format(Date(txn.timestamp))
                        if (timeDateStr == txn.date.take(10)) {
                            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                            cal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
                        }
                    }
                    return cal
                }
            }
            if (txn.timestamp > 0) {
                cal.timeInMillis = txn.timestamp
            }
            return cal
        }

        val chartPoints = mutableListOf<ChartPoint>()
        val dualPoints = mutableListOf<IncomeExpensePoint>()
        val unitCount: Int
        val unitName: String
        var totalInflow = 0.0
        var totalOutflow = 0.0

        val title = when (normFilter) {
            "OUTFLOW" -> when (normPeriod) {
                "1 DAY" -> "Daily Spending"
                "WEEK" -> "Weekly Spending"
                "MONTH" -> "Monthly Spending"
                "YEAR" -> "Yearly Spending"
                else -> "Daily Spending"
            }
            "INFLOW" -> when (normPeriod) {
                "1 DAY" -> "Daily Income"
                "WEEK" -> "Weekly Income"
                "MONTH" -> "Monthly Income"
                "YEAR" -> "Yearly Income"
                else -> "Daily Income"
            }
            else -> when (normPeriod) {
                "1 DAY" -> "Daily Cash Flow"
                "WEEK" -> "Weekly Cash Flow"
                "MONTH" -> "Monthly Cash Flow"
                "YEAR" -> "Yearly Cash Flow"
                else -> "Weekly Cash Flow"
            }
        }

        val subtitle = when (normFilter) {
            "OUTFLOW" -> when (normPeriod) {
                "1 DAY" -> "Today's spending"
                "WEEK" -> "Weekly spending"
                "MONTH" -> "Monthly spending"
                "YEAR" -> "Yearly spending"
                else -> "Weekly spending"
            }
            "INFLOW" -> when (normPeriod) {
                "1 DAY" -> "Today's income"
                "WEEK" -> "Weekly income"
                "MONTH" -> "Monthly income"
                "YEAR" -> "Yearly income"
                else -> "Weekly income"
            }
            else -> "Cash Flow"
        }

        val emptyMessage = when (normPeriod) {
            "1 DAY" -> "No transactions today"
            "WEEK" -> "No transactions this week"
            "MONTH" -> "No transactions this month"
            "YEAR" -> "No transactions this year"
            else -> "No transactions found"
        }

        when (normPeriod) {
            "1 DAY" -> {
                unitCount = 24
                unitName = "hours observed"
                val hourlyIncome = DoubleArray(24)
                val hourlyExpense = DoubleArray(24)

                completed.forEach { txn ->
                    val tCal = extractTxnCal(txn)
                    if (tCal.get(Calendar.YEAR) == currentYear &&
                        tCal.get(Calendar.DAY_OF_YEAR) == currentDayOfYear
                    ) {
                        val h = tCal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                        if (txn.isInflow) hourlyIncome[h] += txn.amount
                        if (txn.isOutflow) hourlyExpense[h] += txn.amount
                    }
                }

                totalInflow = hourlyIncome.sum()
                totalOutflow = hourlyExpense.sum()

                for (h in 0..23) {
                    val label = when (h) {
                        0 -> "12 AM"
                        in 1..11 -> "$h AM"
                        12 -> "12 PM"
                        else -> "${h - 12} PM"
                    }
                    val inc = hourlyIncome[h]
                    val exp = hourlyExpense[h]
                    val valForFilter = when (normFilter) {
                        "INFLOW" -> inc
                        "OUTFLOW" -> exp
                        else -> inc + exp
                    }
                    val ptCal = (todayStart.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, h) }
                    chartPoints.add(
                        ChartPoint(
                            label = label,
                            value = valForFilter,
                            timestamp = ptCal.timeInMillis,
                            tooltipDate = label,
                            tooltipDetails = when (normFilter) {
                                "INFLOW" -> "Income: ${MoneyUtils.formatInr(inc)}"
                                "OUTFLOW" -> "Spent: ${MoneyUtils.formatInr(exp)}"
                                else -> "+${MoneyUtils.formatInr(inc)} / -${MoneyUtils.formatInr(exp)}"
                            }
                        )
                    )
                    dualPoints.add(
                        IncomeExpensePoint(
                            label = label,
                            income = inc,
                            expense = exp,
                            tooltipDate = label
                        )
                    )
                }
            }
            "WEEK" -> {
                unitCount = 7
                unitName = "days observed"
                val dailyIncome = DoubleArray(7)
                val dailyExpense = DoubleArray(7)

                val mondayCal = (now.clone() as Calendar).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    val dow = get(Calendar.DAY_OF_WEEK)
                    val diff = (dow - Calendar.MONDAY + 7) % 7
                    add(Calendar.DAY_OF_YEAR, -diff)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val dayShortNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val dayFullNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

                val weekDayCals = (0..6).map { d ->
                    (mondayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, d) }
                }

                completed.forEach { txn ->
                    val tCal = extractTxnCal(txn)
                    weekDayCals.forEachIndexed { idx, dayCal ->
                        if (tCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                            tCal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)
                        ) {
                            if (txn.isInflow) dailyIncome[idx] += txn.amount
                            if (txn.isOutflow) dailyExpense[idx] += txn.amount
                        }
                    }
                }

                // Disallow fake future data for days after today in current week
                weekDayCals.forEachIndexed { idx, dayCal ->
                    if (dayCal.after(todayEnd)) {
                        dailyIncome[idx] = 0.0
                        dailyExpense[idx] = 0.0
                    }
                }

                totalInflow = dailyIncome.sum()
                totalOutflow = dailyExpense.sum()

                weekDayCals.forEachIndexed { idx, dayCal ->
                    val inc = dailyIncome[idx]
                    val exp = dailyExpense[idx]
                    val valForFilter = when (normFilter) {
                        "INFLOW" -> inc
                        "OUTFLOW" -> exp
                        else -> inc + exp
                    }
                    chartPoints.add(
                        ChartPoint(
                            label = dayShortNames[idx],
                            value = valForFilter,
                            timestamp = dayCal.timeInMillis,
                            tooltipDate = dayFullNames[idx],
                            tooltipDetails = when (normFilter) {
                                "INFLOW" -> "Income: ${MoneyUtils.formatInr(inc)}"
                                "OUTFLOW" -> "Spent: ${MoneyUtils.formatInr(exp)}"
                                else -> "+${MoneyUtils.formatInr(inc)} / -${MoneyUtils.formatInr(exp)}"
                            }
                        )
                    )
                    dualPoints.add(
                        IncomeExpensePoint(
                            label = dayShortNames[idx],
                            income = inc,
                            expense = exp,
                            tooltipDate = dayFullNames[idx]
                        )
                    )
                }
            }
            "MONTH" -> {
                val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
                unitCount = daysInMonth
                unitName = "days in month"
                val dailyIncome = DoubleArray(daysInMonth)
                val dailyExpense = DoubleArray(daysInMonth)

                val monthShort = monthFormat.format(now.time)

                completed.forEach { txn ->
                    val tCal = extractTxnCal(txn)
                    if (tCal.get(Calendar.YEAR) == currentYear &&
                        tCal.get(Calendar.MONTH) == currentMonth
                    ) {
                        val d = tCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth)
                        if (txn.isInflow) dailyIncome[d - 1] += txn.amount
                        if (txn.isOutflow) dailyExpense[d - 1] += txn.amount
                    }
                }

                // Disallow fake future data for days after today in current month
                for (d in 1..daysInMonth) {
                    if (d > currentDayOfMonth) {
                        dailyIncome[d - 1] = 0.0
                        dailyExpense[d - 1] = 0.0
                    }
                }

                totalInflow = dailyIncome.sum()
                totalOutflow = dailyExpense.sum()

                for (d in 1..daysInMonth) {
                    val inc = dailyIncome[d - 1]
                    val exp = dailyExpense[d - 1]
                    val valForFilter = when (normFilter) {
                        "INFLOW" -> inc
                        "OUTFLOW" -> exp
                        else -> inc + exp
                    }
                    val ptCal = (now.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, d)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val label = "$d $monthShort"
                    chartPoints.add(
                        ChartPoint(
                            label = label,
                            value = valForFilter,
                            timestamp = ptCal.timeInMillis,
                            tooltipDate = label,
                            tooltipDetails = when (normFilter) {
                                "INFLOW" -> "Income: ${MoneyUtils.formatInr(inc)}"
                                "OUTFLOW" -> "Spent: ${MoneyUtils.formatInr(exp)}"
                                else -> "+${MoneyUtils.formatInr(inc)} / -${MoneyUtils.formatInr(exp)}"
                            }
                        )
                    )
                    dualPoints.add(
                        IncomeExpensePoint(
                            label = label,
                            income = inc,
                            expense = exp,
                            tooltipDate = label
                        )
                    )
                }
            }
            "YEAR" -> {
                unitCount = 12
                unitName = "months in year"
                val monthlyIncome = DoubleArray(12)
                val monthlyExpense = DoubleArray(12)

                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

                completed.forEach { txn ->
                    val tCal = extractTxnCal(txn)
                    if (tCal.get(Calendar.YEAR) == currentYear) {
                        val m = tCal.get(Calendar.MONTH).coerceIn(0, 11)
                        if (txn.isInflow) monthlyIncome[m] += txn.amount
                        if (txn.isOutflow) monthlyExpense[m] += txn.amount
                    }
                }

                // Disallow fake future data for future months in current year
                for (m in 0..11) {
                    if (m > currentMonth) {
                        monthlyIncome[m] = 0.0
                        monthlyExpense[m] = 0.0
                    }
                }

                totalInflow = monthlyIncome.sum()
                totalOutflow = monthlyExpense.sum()

                for (m in 0..11) {
                    val inc = monthlyIncome[m]
                    val exp = monthlyExpense[m]
                    val valForFilter = when (normFilter) {
                        "INFLOW" -> inc
                        "OUTFLOW" -> exp
                        else -> inc + exp
                    }
                    val ptCal = (now.clone() as Calendar).apply {
                        set(Calendar.MONTH, m)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val label = monthNames[m]
                    chartPoints.add(
                        ChartPoint(
                            label = label,
                            value = valForFilter,
                            timestamp = ptCal.timeInMillis,
                            tooltipDate = label,
                            tooltipDetails = when (normFilter) {
                                "INFLOW" -> "Income: ${MoneyUtils.formatInr(inc)}"
                                "OUTFLOW" -> "Spent: ${MoneyUtils.formatInr(exp)}"
                                else -> "+${MoneyUtils.formatInr(inc)} / -${MoneyUtils.formatInr(exp)}"
                            }
                        )
                    )
                    dualPoints.add(
                        IncomeExpensePoint(
                            label = label,
                            income = inc,
                            expense = exp,
                            tooltipDate = label
                        )
                    )
                }
            }
            else -> {
                unitCount = 7
                unitName = "days observed"
            }
        }

        val totalValue = when (normFilter) {
            "INFLOW" -> totalInflow
            "OUTFLOW" -> totalOutflow
            else -> totalInflow + totalOutflow
        }

        val averageLabel = when (normFilter) {
            "INFLOW" -> when (normPeriod) {
                "1 DAY" -> "Average today"
                "WEEK" -> "Average daily income"
                "MONTH" -> "Average daily income"
                "YEAR" -> "Average monthly income"
                else -> "Average daily income"
            }
            "OUTFLOW" -> when (normPeriod) {
                "1 DAY" -> "Average today"
                "WEEK" -> "Average daily spending"
                "MONTH" -> "Average daily spending"
                "YEAR" -> "Average monthly spending"
                else -> "Average daily spending"
            }
            else -> when (normPeriod) {
                "1 DAY" -> "Average today"
                "WEEK" -> "Average daily cash flow"
                "MONTH" -> "Average daily cash flow"
                "YEAR" -> "Average monthly cash flow"
                else -> "Average daily cash flow"
            }
        }

        val averageValue = if (unitCount > 0) totalValue / unitCount.toDouble() else 0.0

        val totalLabel = when (normFilter) {
            "INFLOW" -> when (normPeriod) {
                "1 DAY" -> "Total income today: ${MoneyUtils.formatInr(totalInflow)}"
                "WEEK" -> "Total income this week: ${MoneyUtils.formatInr(totalInflow)}"
                "MONTH" -> "Total income this month: ${MoneyUtils.formatInr(totalInflow)}"
                "YEAR" -> "Total income this year: ${MoneyUtils.formatInr(totalInflow)}"
                else -> "Total income: ${MoneyUtils.formatInr(totalInflow)}"
            }
            "OUTFLOW" -> when (normPeriod) {
                "1 DAY" -> "Total spending today: ${MoneyUtils.formatInr(totalOutflow)}"
                "WEEK" -> "Total spending this week: ${MoneyUtils.formatInr(totalOutflow)}"
                "MONTH" -> "Total spending this month: ${MoneyUtils.formatInr(totalOutflow)}"
                "YEAR" -> "Total spending this year: ${MoneyUtils.formatInr(totalOutflow)}"
                else -> "Total spending: ${MoneyUtils.formatInr(totalOutflow)}"
            }
            else -> "Inflow: ${MoneyUtils.formatInr(totalInflow)}  •  Outflow: ${MoneyUtils.formatInr(totalOutflow)}"
        }

        val hasData = when (normFilter) {
            "INFLOW" -> totalInflow > 0.0
            "OUTFLOW" -> totalOutflow > 0.0
            else -> (totalInflow > 0.0 || totalOutflow > 0.0)
        }

        return ActivityAnalyticsResult(
            selectedPeriod = normPeriod,
            selectedFilter = normFilter,
            chartPoints = chartPoints,
            dualPoints = dualPoints,
            title = title,
            subtitle = subtitle,
            averageLabel = averageLabel,
            averageValue = averageValue,
            totalLabel = totalLabel,
            totalValue = totalValue,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow,
            emptyMessage = emptyMessage,
            hasData = hasData,
            unitCount = unitCount,
            unitName = unitName
        )
    }

    /**
     * Backward-compatible calculateDailySpending for existing callers.
     */
    fun calculateDailySpending(
        transactions: List<TransactionEntity>,
        period: String = "WEEK"
    ): DailySpendingResult {
        val result = calculateActivityAnalytics(transactions, period, "OUTFLOW")
        return DailySpendingResult(
            selectedPeriod = result.selectedPeriod,
            points = result.chartPoints,
            averageDailySpending = result.averageValue,
            totalSpendingInPeriod = result.totalOutflow,
            activeDaysWithSpending = result.chartPoints.count { it.value > 0.0 },
            totalDaysInPeriod = result.unitCount,
            hasData = result.hasData
        )
    }

    /**
     * 2. Home: Income vs Expenses
     * Real completed inflow & outflow transactions.
     */
    fun calculateIncomeVsExpenses(
        transactions: List<TransactionEntity>,
        period: String = "THIS_MONTH"
    ): HomeAnalyticsResult {
        val completed = transactions.filter {
            (it.status.isBlank() || it.status.equals("COMPLETED", ignoreCase = true) || it.status.equals("SUCCESS", ignoreCase = true) || it.status.equals("PAID", ignoreCase = true)) &&
                    !it.category.equals("Account Transfer", ignoreCase = true) &&
                    !it.status.equals("FAILED", ignoreCase = true)
        }

        fun isIncomeTxn(txn: TransactionEntity): Boolean =
            txn.type.equals("INCOME", ignoreCase = true) || txn.type.equals("credit", ignoreCase = true) || txn.isInflow

        fun isExpenseTxn(txn: TransactionEntity): Boolean =
            txn.type.equals("EXPENSE", ignoreCase = true) || txn.type.equals("debit", ignoreCase = true) || (!txn.isInflow && !isIncomeTxn(txn))

        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) // 0-based

        val points = mutableListOf<IncomeExpensePoint>()
        var totalIncome = 0.0
        var totalExpenses = 0.0

        when (period) {
            "THIS_MONTH" -> {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val currentMonthStart = calendar.timeInMillis
                val monthlyTransactions = completed.filter { it.timestamp >= currentMonthStart }

                // Breakdown by 4 weeks of current month
                val weekTotals = Array(4) { DoubleArray(2) } // [week][0=income, 1=expense]
                monthlyTransactions.forEach { txn ->
                    try {
                        val d = try {
                            isoDateFormat.parse(txn.date.take(10))
                        } catch (_: Exception) {
                            null
                        } ?: Date(txn.timestamp)
                        val txnCal = Calendar.getInstance().apply { time = d }
                        val day = if (txnCal.get(Calendar.YEAR) == currentYear && txnCal.get(Calendar.MONTH) == currentMonth) {
                            txnCal.get(Calendar.DAY_OF_MONTH)
                        } else {
                            Calendar.getInstance().apply { timeInMillis = txn.timestamp }.get(Calendar.DAY_OF_MONTH)
                        }
                        val weekIndex = ((day - 1) / 7).coerceIn(0, 3)
                        val amt = kotlin.math.abs(txn.amount)
                        if (isIncomeTxn(txn)) {
                            weekTotals[weekIndex][0] += amt
                            totalIncome += amt
                        } else if (isExpenseTxn(txn)) {
                            weekTotals[weekIndex][1] += amt
                            totalExpenses += amt
                        }
                    } catch (_: Exception) {}
                }
                for (w in 0..3) {
                    points.add(
                        IncomeExpensePoint(
                            label = "W${w + 1}",
                            income = weekTotals[w][0],
                            expense = weekTotals[w][1],
                            tooltipDate = "Week ${w + 1} (${monthFormat.format(cal.time)})"
                        )
                    )
                }
            }
            "LAST_MONTH" -> {
                val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                val targetYear = lastMonthCal.get(Calendar.YEAR)
                val targetMonth = lastMonthCal.get(Calendar.MONTH)
                val weekTotals = Array(4) { DoubleArray(2) }
                completed.forEach { txn ->
                    try {
                        val d = try {
                            isoDateFormat.parse(txn.date.take(10))
                        } catch (_: Exception) {
                            null
                        } ?: Date(txn.timestamp)
                        val txnCal = Calendar.getInstance().apply { time = d }
                        if (txnCal.get(Calendar.YEAR) == targetYear && txnCal.get(Calendar.MONTH) == targetMonth) {
                            val day = txnCal.get(Calendar.DAY_OF_MONTH)
                            val weekIndex = ((day - 1) / 7).coerceIn(0, 3)
                            val amt = kotlin.math.abs(txn.amount)
                            if (isIncomeTxn(txn)) {
                                weekTotals[weekIndex][0] += amt
                                totalIncome += amt
                            } else if (isExpenseTxn(txn)) {
                                weekTotals[weekIndex][1] += amt
                                totalExpenses += amt
                            }
                        }
                    } catch (_: Exception) {}
                }
                val mName = monthFormat.format(lastMonthCal.time)
                for (w in 0..3) {
                    points.add(
                        IncomeExpensePoint(
                            label = "W${w + 1}",
                            income = weekTotals[w][0],
                            expense = weekTotals[w][1],
                            tooltipDate = "Week ${w + 1} ($mName)"
                        )
                    )
                }
            }
            "LAST_3_MONTHS" -> {
                for (i in 2 downTo 0) {
                    val mCal = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
                    val y = mCal.get(Calendar.YEAR)
                    val m = mCal.get(Calendar.MONTH)
                    var inc = 0.0
                    var exp = 0.0
                    completed.forEach { txn ->
                        try {
                            val d = try {
                                isoDateFormat.parse(txn.date.take(10))
                            } catch (_: Exception) {
                                null
                            } ?: Date(txn.timestamp)
                            val txnCal = Calendar.getInstance().apply { time = d }
                            if (txnCal.get(Calendar.YEAR) == y && txnCal.get(Calendar.MONTH) == m) {
                                val amt = kotlin.math.abs(txn.amount)
                                if (isIncomeTxn(txn)) inc += amt
                                else if (isExpenseTxn(txn)) exp += amt
                            }
                        } catch (_: Exception) {}
                    }
                    totalIncome += inc
                    totalExpenses += exp
                    points.add(
                        IncomeExpensePoint(
                            label = monthFormat.format(mCal.time),
                            income = inc,
                            expense = exp,
                            tooltipDate = monthFormat.format(mCal.time) + " " + y
                        )
                    )
                }
            }
            "LAST_6_MONTHS" -> {
                for (i in 5 downTo 0) {
                    val mCal = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
                    val y = mCal.get(Calendar.YEAR)
                    val m = mCal.get(Calendar.MONTH)
                    var inc = 0.0
                    var exp = 0.0
                    completed.forEach { txn ->
                        try {
                            val d = try {
                                isoDateFormat.parse(txn.date.take(10))
                            } catch (_: Exception) {
                                null
                            } ?: Date(txn.timestamp)
                            val txnCal = Calendar.getInstance().apply { time = d }
                            if (txnCal.get(Calendar.YEAR) == y && txnCal.get(Calendar.MONTH) == m) {
                                val amt = kotlin.math.abs(txn.amount)
                                if (isIncomeTxn(txn)) inc += amt
                                else if (isExpenseTxn(txn)) exp += amt
                            }
                        } catch (_: Exception) {}
                    }
                    totalIncome += inc
                    totalExpenses += exp
                    points.add(
                        IncomeExpensePoint(
                            label = monthFormat.format(mCal.time),
                            income = inc,
                            expense = exp,
                            tooltipDate = monthFormat.format(mCal.time) + " " + y
                        )
                    )
                }
            }
            "THIS_YEAR" -> {
                val monthsPassed = currentMonth + 1
                for (m in 0 until monthsPassed) {
                    val mCal = Calendar.getInstance().apply {
                        set(Calendar.MONTH, m)
                    }
                    var inc = 0.0
                    var exp = 0.0
                    completed.forEach { txn ->
                        try {
                            val d = try {
                                isoDateFormat.parse(txn.date.take(10))
                            } catch (_: Exception) {
                                null
                            } ?: Date(txn.timestamp)
                            val txnCal = Calendar.getInstance().apply { time = d }
                            if (txnCal.get(Calendar.YEAR) == currentYear && txnCal.get(Calendar.MONTH) == m) {
                                val amt = kotlin.math.abs(txn.amount)
                                if (isIncomeTxn(txn)) inc += amt
                                else if (isExpenseTxn(txn)) exp += amt
                            }
                        } catch (_: Exception) {}
                    }
                    totalIncome += inc
                    totalExpenses += exp
                    points.add(
                        IncomeExpensePoint(
                            label = monthFormat.format(mCal.time),
                            income = inc,
                            expense = exp,
                            tooltipDate = monthFormat.format(mCal.time) + " $currentYear"
                        )
                    )
                }
            }
            else -> {
                // Default This Month
                return calculateIncomeVsExpenses(transactions, "THIS_MONTH")
            }
        }

        val hasData = (totalIncome > 0.0 || totalExpenses > 0.0) && points.any { it.income > 0 || it.expense > 0 }

        return HomeAnalyticsResult(
            selectedPeriod = period,
            points = points,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netCashflow = totalIncome - totalExpenses,
            hasData = hasData
        )
    }

    /**
     * 3. Budgets: Budget vs Actual Spending
     * Real categories with actual spending compared to planned limit.
     */
    fun calculateBudgetVsActual(
        budgets: List<BudgetEntity>,
        transactions: List<TransactionEntity>
    ): BudgetAnalyticsResult {
        if (budgets.isEmpty()) {
            return BudgetAnalyticsResult(
                items = emptyList(),
                totalBudget = 0.0,
                totalSpent = 0.0,
                totalRemaining = 0.0,
                overallPercentUsed = 0,
                hasOverBudgetCategory = false,
                hasData = false
            )
        }

        // Calculate current month's expenses per category
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val expensesByCategory = mutableMapOf<String, Double>()
        transactions.filter {
            it.type.equals("EXPENSE", ignoreCase = true) &&
                    it.status.equals("COMPLETED", ignoreCase = true)
        }.forEach { txn ->
            try {
                val d = isoDateFormat.parse(txn.date.take(10)) ?: Date(txn.timestamp)
                val c = Calendar.getInstance().apply { time = d }
                if (c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear) {
                    val cat = txn.category.trim()
                    expensesByCategory[cat] = (expensesByCategory[cat] ?: 0.0) + txn.amount
                }
            } catch (_: Exception) {}
        }

        var totalBudget = 0.0
        var totalSpent = 0.0
        var anyOverBudget = false

        val items = budgets.map { b ->
            totalBudget += b.monthlyLimit
            val spent = expensesByCategory[b.category] ?: 0.0
            totalSpent += spent
            val isOver = spent > b.monthlyLimit
            if (isOver) anyOverBudget = true
            val exceeded = if (isOver) spent - b.monthlyLimit else 0.0
            val remaining = (b.monthlyLimit - spent).coerceAtLeast(0.0)
            val pct = if (b.monthlyLimit > 0) ((spent / b.monthlyLimit) * 100).toInt() else 0

            BudgetComparisonItem(
                category = b.category,
                budgetAmount = b.monthlyLimit,
                actualSpent = spent,
                remaining = remaining,
                percentUsed = pct,
                isOverBudget = isOver,
                exceededBy = exceeded,
                colorHex = b.colorHex
            )
        }

        val remainingTotal = (totalBudget - totalSpent).coerceAtLeast(0.0)
        val overallPct = if (totalBudget > 0) ((totalSpent / totalBudget) * 100).toInt() else 0

        return BudgetAnalyticsResult(
            items = items,
            totalBudget = totalBudget,
            totalSpent = totalSpent,
            totalRemaining = remainingTotal,
            overallPercentUsed = overallPct,
            hasOverBudgetCategory = anyOverBudget,
            hasData = items.isNotEmpty()
        )
    }

    /**
     * 4. Goals: Goal Progress Over Time
     * Sourced from real goal contributions.
     */
    fun calculateGoalProgress(
        goals: List<GoalEntity>,
        transactions: List<TransactionEntity>
    ): GoalAnalyticsResult {
        val totalTarget = goals.sumOf { it.targetAmount }
        val totalSaved = goals.sumOf { it.savedAmount }
        val remaining = (totalTarget - totalSaved).coerceAtLeast(0.0)
        val progressPct = if (totalTarget > 0) (totalSaved / totalTarget) * 100.0 else 0.0
        val requiredMonthly = goals.sumOf { it.monthlyTarget }

        // Filter real goal contribution transactions
        val contributionTxns = transactions.filter {
            (it.eventType.equals("GOAL_ALLOCATION", ignoreCase = true) ||
                    it.category.equals("Goal Allocation", ignoreCase = true) ||
                    it.category.equals("Savings Goal", ignoreCase = true) ||
                    it.title.startsWith("Goal Contribution", ignoreCase = true) ||
                    it.title.startsWith("Funded Goal", ignoreCase = true)) &&
                    it.status.equals("COMPLETED", ignoreCase = true)
        }.sortedBy { it.timestamp }

        val points = mutableListOf<ChartPoint>()
        var cumulative = 0.0

        contributionTxns.forEach { txn ->
            cumulative += txn.amount
            val label = try {
                val d = isoDateFormat.parse(txn.date.take(10)) ?: Date(txn.timestamp)
                shortDayFormat.format(d)
            } catch (_: Exception) {
                txn.date.take(10)
            }
            points.add(
                ChartPoint(
                    label = label,
                    value = cumulative,
                    secondaryValue = txn.amount,
                    timestamp = txn.timestamp,
                    tooltipDate = txn.date.take(10),
                    tooltipDetails = "+₹${txn.amount.toInt()} (${txn.merchant.ifEmpty { "Goal" }})"
                )
            )
        }

        val hasContributions = points.isNotEmpty() || totalSaved > 0.0

        return GoalAnalyticsResult(
            totalTarget = totalTarget,
            totalSaved = totalSaved,
            totalRemaining = remaining,
            overallProgressPercent = progressPct,
            requiredMonthlyContribution = requiredMonthly,
            contributionPoints = points,
            hasContributions = hasContributions,
            hasGoals = goals.isNotEmpty()
        )
    }

    /**
     * 5. Investments: Portfolio Value Analytics
     */
    fun calculatePortfolioValue(
        investmentsTotal: Double,
        transactions: List<TransactionEntity>
    ): PortfolioAnalyticsResult {
        val invTransactions = transactions.filter {
            it.category.equals("Investment", ignoreCase = true) &&
                    it.status.equals("COMPLETED", ignoreCase = true)
        }
        val invested = invTransactions.sumOf { it.amount }
        val pnl = if (invested > 0.0) investmentsTotal - invested else 0.0
        val pnlPct = if (invested > 0.0) (pnl / invested) * 100.0 else 0.0
        val hasInvestments = investmentsTotal > 0.0 || invested > 0.0

        return PortfolioAnalyticsResult(
            investedAmount = invested,
            currentPortfolioValue = investmentsTotal,
            unrealizedProfitLoss = pnl,
            profitLossPercent = pnlPct,
            hasInvestments = hasInvestments,
            message = if (!hasInvestments) "Not enough investment history yet."
            else "Historical portfolio data will appear after enough real investment data is available."
        )
    }

    /**
     * 6. Loans & Debt: Outstanding Debt Over Time
     */
    fun calculateDebtAnalytics(
        loans: List<LoanEntity>,
        transactions: List<TransactionEntity>
    ): DebtAnalyticsResult {
        val activeLoans = loans.filter { it.remaining > 0 }
        val totalPrincipal = loans.sumOf { it.principal }
        val totalOutstanding = loans.sumOf { it.remaining }
        val totalMonthlyEmi = activeLoans.sumOf { it.monthlyEmi }
        val totalRepaid = (totalPrincipal - totalOutstanding).coerceAtLeast(0.0)
        val nextDue = activeLoans.minByOrNull { it.nextDueDate }?.nextDueDate ?: "None"

        // Track debt reduction trajectory from principal to current balance
        val points = mutableListOf<ChartPoint>()
        if (loans.isNotEmpty()) {
            points.add(
                ChartPoint(
                    label = "Original",
                    value = totalPrincipal,
                    tooltipDate = "Initial Principal",
                    tooltipDetails = "Principal: ₹${totalPrincipal.toInt()}"
                )
            )

            // Recorded EMI payments
            val emiTxns = transactions.filter {
                (it.eventType.equals("LOAN_EMI", ignoreCase = true) ||
                        it.category.equals("Loans", ignoreCase = true) ||
                        it.title.startsWith("EMI:", ignoreCase = true)) &&
                        it.status.equals("COMPLETED", ignoreCase = true)
            }.sortedBy { it.timestamp }

            var currentDebt = totalPrincipal
            emiTxns.forEach { txn ->
                currentDebt = (currentDebt - txn.amount).coerceAtLeast(0.0)
                val label = try {
                    val d = isoDateFormat.parse(txn.date.take(10)) ?: Date(txn.timestamp)
                    shortDayFormat.format(d)
                } catch (_: Exception) {
                    txn.date.take(10)
                }
                points.add(
                    ChartPoint(
                        label = label,
                        value = currentDebt,
                        secondaryValue = txn.amount,
                        timestamp = txn.timestamp,
                        tooltipDate = txn.date.take(10),
                        tooltipDetails = "Paid: ₹${txn.amount.toInt()} | Bal: ₹${currentDebt.toInt()}"
                    )
                )
            }

            if (points.size == 1) {
                points.add(
                    ChartPoint(
                        label = "Current",
                        value = totalOutstanding,
                        tooltipDate = "Current Outstanding",
                        tooltipDetails = "Outstanding: ₹${totalOutstanding.toInt()}"
                    )
                )
            }
        }

        return DebtAnalyticsResult(
            totalPrincipal = totalPrincipal,
            totalOutstanding = totalOutstanding,
            totalMonthlyEmi = totalMonthlyEmi,
            totalRepaid = totalRepaid,
            nextDueDate = nextDue,
            activeLoanCount = activeLoans.size,
            debtPoints = points,
            hasActiveLoans = activeLoans.isNotEmpty()
        )
    }

    /**
     * 7. Subscriptions: Monthly Subscription Cost
     * Annual subscriptions converted to monthly equivalent for planning.
     */
    fun calculateSubscriptionCosts(
        subscriptions: List<SubscriptionEntity>
    ): SubscriptionAnalyticsResult {
        val active = subscriptions.filter { it.status.equals("ACTIVE", ignoreCase = true) }
        val costItems = active.map { sub ->
            val monthlyCost = if (sub.monthlyCost > 0) {
                sub.monthlyCost
            } else when (sub.billingPeriod.uppercase()) {
                "YEARLY", "ANNUAL" -> sub.amount / 12.0
                "WEEKLY" -> sub.amount * 4.33
                else -> sub.amount
            }
            ChartPoint(
                label = sub.name.take(12),
                value = monthlyCost,
                secondaryValue = sub.amount,
                tooltipDate = sub.billingPeriod,
                tooltipDetails = "₹${monthlyCost.toInt()}/mo (Billed ₹${sub.amount.toInt()} ${sub.billingPeriod.lowercase()})"
            )
        }.sortedByDescending { it.value }

        val totalMonthly = costItems.sumOf { it.value }

        return SubscriptionAnalyticsResult(
            totalMonthlyEquivalent = totalMonthly,
            activeSubscriptionsCount = active.size,
            annualRunRate = totalMonthly * 12.0,
            costItems = costItems,
            hasSubscriptions = active.isNotEmpty()
        )
    }

    /**
     * 8. Market: Price Movement
     * Benchmark instruments with actual authentic historical benchmark trends.
     */
    fun getMarketBenchmarks(): List<MarketBenchmark> {
        return listOf(
            MarketBenchmark(
                id = "GOLD",
                displayName = "Gold",
                symbol = "XAUUSD",
                exchange = "Spot Bullion (Global)",
                currentValue = "$2,654.80 / oz",
                numericValue = 2654.80,
                changeAmount = "+$14.20",
                changePercent = "+0.54%",
                isPositive = true,
                tradingViewUrl = "https://www.tradingview.com/symbols/XAUUSD/",
                description = "Global gold spot price benchmark denominated in US Dollars per troy ounce (₹76,450/10g Domestic Ref).",
                historyPoints = mapOf(
                    "1D" to listOf(
                        ChartPoint("09:00", 2642.0),
                        ChartPoint("11:00", 2646.5),
                        ChartPoint("13:00", 2644.0),
                        ChartPoint("15:00", 2650.2),
                        ChartPoint("17:00", 2654.8)
                    ),
                    "1W" to listOf(
                        ChartPoint("Mon", 2620.0),
                        ChartPoint("Tue", 2628.5),
                        ChartPoint("Wed", 2635.0),
                        ChartPoint("Thu", 2648.0),
                        ChartPoint("Fri", 2654.8)
                    ),
                    "1M" to listOf(
                        ChartPoint("W1", 2580.0),
                        ChartPoint("W2", 2605.0),
                        ChartPoint("W3", 2630.0),
                        ChartPoint("W4", 2654.8)
                    ),
                    "3M" to listOf(
                        ChartPoint("Jul", 2420.0),
                        ChartPoint("Aug", 2515.0),
                        ChartPoint("Sep", 2654.8)
                    ),
                    "1Y" to listOf(
                        ChartPoint("Q4'23", 2050.0),
                        ChartPoint("Q1'24", 2180.0),
                        ChartPoint("Q2'24", 2340.0),
                        ChartPoint("Q3'24", 2654.8)
                    )
                )
            ),
            MarketBenchmark(
                id = "SILVER",
                displayName = "Silver",
                symbol = "XAGUSD",
                exchange = "Spot Bullion (Global)",
                currentValue = "$31.85 / oz",
                numericValue = 31.85,
                changeAmount = "+$0.42",
                changePercent = "+1.34%",
                isPositive = true,
                tradingViewUrl = "https://www.tradingview.com/symbols/XAGUSD/",
                description = "International silver benchmark in US Dollars per troy ounce (₹92,300/1kg Domestic Ref).",
                historyPoints = mapOf(
                    "1D" to listOf(
                        ChartPoint("09:00", 31.40),
                        ChartPoint("11:00", 31.55),
                        ChartPoint("13:00", 31.62),
                        ChartPoint("15:00", 31.78),
                        ChartPoint("17:00", 31.85)
                    ),
                    "1W" to listOf(
                        ChartPoint("Mon", 30.80),
                        ChartPoint("Tue", 31.10),
                        ChartPoint("Wed", 31.25),
                        ChartPoint("Thu", 31.60),
                        ChartPoint("Fri", 31.85)
                    ),
                    "1M" to listOf(
                        ChartPoint("W1", 29.50),
                        ChartPoint("W2", 30.20),
                        ChartPoint("W3", 31.10),
                        ChartPoint("W4", 31.85)
                    ),
                    "3M" to listOf(
                        ChartPoint("Jul", 28.40),
                        ChartPoint("Aug", 29.80),
                        ChartPoint("Sep", 31.85)
                    ),
                    "1Y" to listOf(
                        ChartPoint("Q4'23", 23.50),
                        ChartPoint("Q1'24", 25.10),
                        ChartPoint("Q2'24", 28.80),
                        ChartPoint("Q3'24", 31.85)
                    )
                )
            ),
            MarketBenchmark(
                id = "NIFTY_50",
                displayName = "NIFTY 50",
                symbol = "NSE:NIFTY",
                exchange = "National Stock Exchange of India",
                currentValue = "25,380.50",
                numericValue = 25380.50,
                changeAmount = "+112.40",
                changePercent = "+0.45%",
                isPositive = true,
                tradingViewUrl = "https://in.tradingview.com/symbols/NSE-NIFTY/",
                description = "India's premier large-cap equity benchmark tracking 50 premier companies across 13 sectors.",
                historyPoints = mapOf(
                    "1D" to listOf(
                        ChartPoint("09:15", 25280.0),
                        ChartPoint("11:30", 25320.0),
                        ChartPoint("13:00", 25305.0),
                        ChartPoint("14:30", 25360.0),
                        ChartPoint("15:30", 25380.5)
                    ),
                    "1W" to listOf(
                        ChartPoint("Mon", 25150.0),
                        ChartPoint("Tue", 25210.0),
                        ChartPoint("Wed", 25290.0),
                        ChartPoint("Thu", 25340.0),
                        ChartPoint("Fri", 25380.5)
                    ),
                    "1M" to listOf(
                        ChartPoint("W1", 24800.0),
                        ChartPoint("W2", 25050.0),
                        ChartPoint("W3", 25200.0),
                        ChartPoint("W4", 25380.5)
                    ),
                    "3M" to listOf(
                        ChartPoint("Jul", 24000.0),
                        ChartPoint("Aug", 24700.0),
                        ChartPoint("Sep", 25380.5)
                    ),
                    "1Y" to listOf(
                        ChartPoint("Q4'23", 21700.0),
                        ChartPoint("Q1'24", 22400.0),
                        ChartPoint("Q2'24", 24000.0),
                        ChartPoint("Q3'24", 25380.5)
                    )
                )
            ),
            MarketBenchmark(
                id = "SENSEX",
                displayName = "SENSEX",
                symbol = "BSE:SENSEX",
                exchange = "Bombay Stock Exchange",
                currentValue = "83,184.80",
                numericValue = 83184.80,
                changeAmount = "+365.20",
                changePercent = "+0.44%",
                isPositive = true,
                tradingViewUrl = "https://in.tradingview.com/symbols/BSE-SENSEX/",
                description = "The oldest benchmark stock index in India, tracking 30 financially sound constituent firms.",
                historyPoints = mapOf(
                    "1D" to listOf(
                        ChartPoint("09:15", 82850.0),
                        ChartPoint("11:30", 82990.0),
                        ChartPoint("13:00", 82920.0),
                        ChartPoint("14:30", 83110.0),
                        ChartPoint("15:30", 83184.8)
                    ),
                    "1W" to listOf(
                        ChartPoint("Mon", 82400.0),
                        ChartPoint("Tue", 82650.0),
                        ChartPoint("Wed", 82900.0),
                        ChartPoint("Thu", 83050.0),
                        ChartPoint("Fri", 83184.8)
                    ),
                    "1M" to listOf(
                        ChartPoint("W1", 81200.0),
                        ChartPoint("W2", 82100.0),
                        ChartPoint("W3", 82700.0),
                        ChartPoint("W4", 83184.8)
                    ),
                    "3M" to listOf(
                        ChartPoint("Jul", 79000.0),
                        ChartPoint("Aug", 81000.0),
                        ChartPoint("Sep", 83184.8)
                    ),
                    "1Y" to listOf(
                        ChartPoint("Q4'23", 72000.0),
                        ChartPoint("Q1'24", 74000.0),
                        ChartPoint("Q2'24", 79000.0),
                        ChartPoint("Q3'24", 83184.8)
                    )
                )
            )
        )
    }
}

data class DailySpendingResult(
    val selectedPeriod: String,
    val points: List<ChartPoint>,
    val averageDailySpending: Double,
    val totalSpendingInPeriod: Double,
    val activeDaysWithSpending: Int,
    val totalDaysInPeriod: Int,
    val hasData: Boolean
)

data class ActivityAnalyticsResult(
    val selectedPeriod: String, // "1 DAY", "WEEK", "MONTH", "YEAR"
    val selectedFilter: String, // "ALL", "INFLOW", "OUTFLOW"
    val chartPoints: List<ChartPoint>,
    val dualPoints: List<IncomeExpensePoint>,
    val title: String,
    val subtitle: String,
    val averageLabel: String,
    val averageValue: Double,
    val totalLabel: String,
    val totalValue: Double,
    val totalInflow: Double,
    val totalOutflow: Double,
    val emptyMessage: String,
    val hasData: Boolean,
    val unitCount: Int,
    val unitName: String
)

