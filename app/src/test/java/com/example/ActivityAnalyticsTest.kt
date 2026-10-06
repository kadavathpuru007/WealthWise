package com.example

import com.example.data.local.TransactionEntity
import com.example.domain.financial.PageAnalyticsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ActivityAnalyticsTest {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private lateinit var refCal: Calendar

    @Before
    fun setUp() {
        // Set fixed reference calendar: Monday, September 28, 2026, 14:30:00 (2:30 PM)
        refCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 28) // Monday
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun makeTxn(
        id: Int,
        title: String,
        amount: Double,
        type: String,
        cal: Calendar
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            title = title,
            amount = amount,
            type = type,
            category = if (type == "INCOME") "Salary" else "Food",
            paymentMethod = "UPI",
            date = isoFormat.format(cal.time),
            timestamp = cal.timeInMillis,
            status = "COMPLETED"
        )
    }

    @Test
    fun test_TC01_oneDay_selectsTodayTransactionsOnly() {
        // Given: Transaction today (28 Sep, 10 AM) and yesterday (27 Sep, 10 AM)
        val todayCal = (refCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 10) }
        val yesterdayCal = (refCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 10)
        }

        val txns = listOf(
            makeTxn(1, "Lunch", 450.0, "EXPENSE", todayCal),
            makeTxn(2, "Dinner Yesterday", 800.0, "EXPENSE", yesterdayCal)
        )

        // When
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = txns,
            period = "1 DAY",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        // Then
        assertEquals(450.0, result.totalOutflow, 0.001)
        assertEquals(1, result.chartPoints.count { it.value > 0.0 })
        assertEquals(450.0, result.chartPoints[10].value, 0.001)
    }

    @Test
    fun test_TC02_week_selectsCurrentWeekTransactions() {
        // Given: Transaction on Monday (28 Sep) and last week (20 Sep)
        val thisWeekCal = (refCal.clone() as Calendar) // 28 Sep
        val lastWeekCal = (refCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -8) }

        val txns = listOf(
            makeTxn(1, "Groceries", 1200.0, "EXPENSE", thisWeekCal),
            makeTxn(2, "Old expense", 500.0, "EXPENSE", lastWeekCal)
        )

        // When
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = txns,
            period = "WEEK",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        // Then
        assertEquals(1200.0, result.totalOutflow, 0.001)
        assertEquals(7, result.chartPoints.size)
        // Monday is day index 0
        assertEquals(1200.0, result.chartPoints[0].value, 0.001)
    }

    @Test
    fun test_TC03_month_selectsCurrentMonthTransactions() {
        // Given: Transaction on 15 Sep (this month) and 15 Aug (last month)
        val thisMonthCal = (refCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 15) }
        val lastMonthCal = (refCal.clone() as Calendar).apply {
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 15)
        }

        val txns = listOf(
            makeTxn(1, "Electricity", 1500.0, "EXPENSE", thisMonthCal),
            makeTxn(2, "August Bill", 2000.0, "EXPENSE", lastMonthCal)
        )

        // When
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = txns,
            period = "MONTH",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        // Then
        assertEquals(1500.0, result.totalOutflow, 0.001)
        assertEquals(30, result.chartPoints.size) // September has 30 days
        assertEquals(1500.0, result.chartPoints[14].value, 0.001) // 15th Sep is index 14
    }

    @Test
    fun test_TC04_year_selectsCurrentYearTransactions() {
        // Given: Transaction in March 2026 and March 2025
        val thisYearCal = (refCal.clone() as Calendar).apply { set(Calendar.MONTH, Calendar.MARCH) }
        val lastYearCal = (refCal.clone() as Calendar).apply {
            set(Calendar.YEAR, 2025)
            set(Calendar.MONTH, Calendar.MARCH)
        }

        val txns = listOf(
            makeTxn(1, "Laptop", 65000.0, "EXPENSE", thisYearCal),
            makeTxn(2, "Old Phone", 25000.0, "EXPENSE", lastYearCal)
        )

        // When
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = txns,
            period = "YEAR",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        // Then
        assertEquals(65000.0, result.totalOutflow, 0.001)
        assertEquals(12, result.chartPoints.size)
        // March is index 2
        assertEquals(65000.0, result.chartPoints[2].value, 0.001)
    }

    @Test
    fun test_TC05_oneDay_usesHourlyAggregation() {
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = emptyList(),
            period = "1 DAY",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        assertEquals(24, result.chartPoints.size)
        assertEquals("12 AM", result.chartPoints[0].label)
        assertEquals("12 PM", result.chartPoints[12].label)
        assertEquals("11 PM", result.chartPoints[23].label)
    }

    @Test
    fun test_TC06_week_usesDailyAggregation() {
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = emptyList(),
            period = "WEEK",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        assertEquals(7, result.chartPoints.size)
        assertEquals("Mon", result.chartPoints[0].label)
        assertEquals("Sun", result.chartPoints[6].label)
    }

    @Test
    fun test_TC07_month_usesDailyAggregation() {
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = emptyList(),
            period = "MONTH",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        assertEquals(30, result.chartPoints.size) // September has 30 days
        assertEquals("1 Sep", result.chartPoints[0].label)
        assertEquals("30 Sep", result.chartPoints[29].label)
    }

    @Test
    fun test_TC08_year_usesMonthlyAggregation() {
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = emptyList(),
            period = "YEAR",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        assertEquals(12, result.chartPoints.size)
        assertEquals("Jan", result.chartPoints[0].label)
        assertEquals("Dec", result.chartPoints[11].label)
    }

    @Test
    fun test_TC09_outflowWeek_showsOnlyOutflows() {
        val txnIncome = makeTxn(1, "Salary", 50000.0, "INCOME", refCal)
        val txnExpense = makeTxn(2, "Groceries", 2500.0, "EXPENSE", refCal)

        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = listOf(txnIncome, txnExpense),
            period = "WEEK",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        assertEquals(2500.0, result.totalValue, 0.001)
        assertEquals("Weekly Spending", result.title)
        assertEquals("Weekly spending", result.subtitle)
        assertEquals(2500.0, result.chartPoints[0].value, 0.001) // Monday
    }

    @Test
    fun test_TC10_inflowWeek_showsOnlyInflows() {
        val txnIncome = makeTxn(1, "Salary", 50000.0, "INCOME", refCal)
        val txnExpense = makeTxn(2, "Groceries", 2500.0, "EXPENSE", refCal)

        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = listOf(txnIncome, txnExpense),
            period = "WEEK",
            filter = "INFLOW",
            referenceCalendar = refCal
        )

        assertEquals(50000.0, result.totalValue, 0.001)
        assertEquals("Weekly Income", result.title)
        assertEquals("Weekly income", result.subtitle)
        assertEquals(50000.0, result.chartPoints[0].value, 0.001) // Monday
    }

    @Test
    fun test_TC11_allWeek_showsBothInflowAndOutflow() {
        val txnIncome = makeTxn(1, "Bonus", 10000.0, "INCOME", refCal)
        val txnExpense = makeTxn(2, "Shopping", 3000.0, "EXPENSE", refCal)

        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = listOf(txnIncome, txnExpense),
            period = "WEEK",
            filter = "ALL",
            referenceCalendar = refCal
        )

        assertEquals("Weekly Cash Flow", result.title)
        assertEquals("Cash Flow", result.subtitle)
        assertEquals(10000.0, result.totalInflow, 0.001)
        assertEquals(3000.0, result.totalOutflow, 0.001)
        assertEquals(10000.0, result.dualPoints[0].income, 0.001)
        assertEquals(3000.0, result.dualPoints[0].expense, 0.001)
    }

    @Test
    fun test_TC12_outflowYear_aggregatesByMonth() {
        val febCal = (refCal.clone() as Calendar).apply { set(Calendar.MONTH, Calendar.FEBRUARY) }
        val sepCal = (refCal.clone() as Calendar).apply { set(Calendar.MONTH, Calendar.SEPTEMBER) }

        val txns = listOf(
            makeTxn(1, "Tickets", 4000.0, "EXPENSE", febCal),
            makeTxn(2, "Shopping", 6000.0, "EXPENSE", sepCal)
        )

        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = txns,
            period = "YEAR",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        assertEquals(12, result.chartPoints.size)
        assertEquals(10000.0, result.totalOutflow, 0.001)
        assertEquals("Yearly Spending", result.title)
        assertEquals(4000.0, result.chartPoints[1].value, 0.001) // Feb
        assertEquals(6000.0, result.chartPoints[8].value, 0.001) // Sep
    }

    @Test
    fun test_TC13_inflowYear_aggregatesByMonth() {
        val janCal = (refCal.clone() as Calendar).apply { set(Calendar.MONTH, Calendar.JANUARY) }
        val julCal = (refCal.clone() as Calendar).apply { set(Calendar.MONTH, Calendar.JULY) }

        val txns = listOf(
            makeTxn(1, "Bonus", 20000.0, "INCOME", janCal),
            makeTxn(2, "Dividend", 5000.0, "INCOME", julCal)
        )

        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = txns,
            period = "YEAR",
            filter = "INFLOW",
            referenceCalendar = refCal
        )

        assertEquals(12, result.chartPoints.size)
        assertEquals(25000.0, result.totalInflow, 0.001)
        assertEquals("Yearly Income", result.title)
        assertEquals(20000.0, result.chartPoints[0].value, 0.001) // Jan
        assertEquals(5000.0, result.chartPoints[6].value, 0.001) // Jul
    }

    @Test
    fun test_TC14_noFutureTransactionsInvented() {
        // Today is 28 Sep (Monday of the week, September in the year).
        // Tomorrow (29 Sep, Tuesday) should not have fake transactions.
        val result = PageAnalyticsEngine.calculateActivityAnalytics(
            transactions = emptyList(),
            period = "WEEK",
            filter = "OUTFLOW",
            referenceCalendar = refCal
        )

        // Points for future days in the week must be 0.0
        for (i in 1..6) {
            assertEquals(0.0, result.chartPoints[i].value, 0.0)
        }
    }

    @Test
    fun test_TC15_emptyPeriods_showProperEmptyState() {
        val r1 = PageAnalyticsEngine.calculateActivityAnalytics(emptyList(), "1 DAY", "OUTFLOW", refCal)
        assertFalse(r1.hasData)
        assertEquals("No transactions today", r1.emptyMessage)

        val r2 = PageAnalyticsEngine.calculateActivityAnalytics(emptyList(), "WEEK", "OUTFLOW", refCal)
        assertFalse(r2.hasData)
        assertEquals("No transactions this week", r2.emptyMessage)

        val r3 = PageAnalyticsEngine.calculateActivityAnalytics(emptyList(), "MONTH", "OUTFLOW", refCal)
        assertFalse(r3.hasData)
        assertEquals("No transactions this month", r3.emptyMessage)

        val r4 = PageAnalyticsEngine.calculateActivityAnalytics(emptyList(), "YEAR", "OUTFLOW", refCal)
        assertFalse(r4.hasData)
        assertEquals("No transactions this year", r4.emptyMessage)
    }

    @Test
    fun test_TC16_switchingPeriod_immediatelyRecalculatesGraphAndSummary() {
        val todayCal = (refCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }
        val txn = makeTxn(1, "Lunch", 350.0, "EXPENSE", todayCal)

        val rDay = PageAnalyticsEngine.calculateActivityAnalytics(listOf(txn), "1 DAY", "OUTFLOW", refCal)
        val rWeek = PageAnalyticsEngine.calculateActivityAnalytics(listOf(txn), "WEEK", "OUTFLOW", refCal)
        val rMonth = PageAnalyticsEngine.calculateActivityAnalytics(listOf(txn), "MONTH", "OUTFLOW", refCal)
        val rYear = PageAnalyticsEngine.calculateActivityAnalytics(listOf(txn), "YEAR", "OUTFLOW", refCal)

        assertEquals("Daily Spending", rDay.title)
        assertEquals(24, rDay.chartPoints.size)

        assertEquals("Weekly Spending", rWeek.title)
        assertEquals(7, rWeek.chartPoints.size)

        assertEquals("Monthly Spending", rMonth.title)
        assertEquals(30, rMonth.chartPoints.size)

        assertEquals("Yearly Spending", rYear.title)
        assertEquals(12, rYear.chartPoints.size)
    }

    @Test
    fun test_TC17_switchingFilters_immediatelyRecalculatesGraph() {
        val txn1 = makeTxn(1, "Salary", 40000.0, "INCOME", refCal)
        val txn2 = makeTxn(2, "Rent", 15000.0, "EXPENSE", refCal)
        val txns = listOf(txn1, txn2)

        val rAll = PageAnalyticsEngine.calculateActivityAnalytics(txns, "WEEK", "ALL", refCal)
        val rIn = PageAnalyticsEngine.calculateActivityAnalytics(txns, "WEEK", "INFLOW", refCal)
        val rOut = PageAnalyticsEngine.calculateActivityAnalytics(txns, "WEEK", "OUTFLOW", refCal)

        assertEquals("Weekly Cash Flow", rAll.title)
        assertEquals("Cash Flow", rAll.subtitle)
        assertEquals(40000.0, rAll.totalInflow, 0.001)
        assertEquals(15000.0, rAll.totalOutflow, 0.001)

        assertEquals("Weekly Income", rIn.title)
        assertEquals(40000.0, rIn.totalValue, 0.001)

        assertEquals("Weekly Spending", rOut.title)
        assertEquals(15000.0, rOut.totalValue, 0.001)
    }

    @Test
    fun test_TC18_averageCalculation_matchesSelectedPeriodAndAggregation() {
        val txn = makeTxn(1, "Dinner", 2100.0, "EXPENSE", refCal)
        val txns = listOf(txn)

        // 1 DAY: hourly aggregation -> average per hour (2100 / 24)
        val rDay = PageAnalyticsEngine.calculateActivityAnalytics(txns, "1 DAY", "OUTFLOW", refCal)
        assertEquals("Average today", rDay.averageLabel)
        assertEquals(2100.0 / 24.0, rDay.averageValue, 0.001)

        // WEEK: daily aggregation -> average per day (2100 / 7)
        val rWeek = PageAnalyticsEngine.calculateActivityAnalytics(txns, "WEEK", "OUTFLOW", refCal)
        assertEquals("Average daily spending", rWeek.averageLabel)
        assertEquals(2100.0 / 7.0, rWeek.averageValue, 0.001)

        // MONTH: daily aggregation -> average per day (2100 / 30)
        val rMonth = PageAnalyticsEngine.calculateActivityAnalytics(txns, "MONTH", "OUTFLOW", refCal)
        assertEquals("Average daily spending", rMonth.averageLabel)
        assertEquals(2100.0 / 30.0, rMonth.averageValue, 0.001)

        // YEAR: monthly aggregation -> average per month (2100 / 12)
        val rYear = PageAnalyticsEngine.calculateActivityAnalytics(txns, "YEAR", "OUTFLOW", refCal)
        assertEquals("Average monthly spending", rYear.averageLabel)
        assertEquals(2100.0 / 12.0, rYear.averageValue, 0.001)
    }
}
