package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SubscriptionDao
import com.example.data.local.SubscriptionEntity
import com.example.data.local.WealthWiseDatabase
import com.example.data.repository.SubscriptionRepository
import com.example.domain.subscription.SubscriptionSummary
import com.example.domain.subscription.SubscriptionUiModel
import com.example.domain.subscription.SubscriptionValueEvaluator
import com.example.domain.subscription.UsageState
import com.example.domain.subscription.ValueRating
import com.example.util.UsageHelper
import com.example.util.UsageStatsRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Production test suite covering all required test cases for the
 * Subscription Value & Usage Insights module (Sections 65 - 71).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionValueAndUsageTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: WealthWiseDatabase
    private lateinit var subscriptionDao: SubscriptionDao
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Executor { it.run() })
            .build()
        subscriptionDao = database.subscriptionDao()
        subscriptionRepository = SubscriptionRepository(subscriptionDao, database.transactionDao())

        // Reset test overrides
        UsageHelper.permissionOverrideForTesting = null
        UsageHelper.usageMinutesOverrideForTesting = null
        UsageHelper.installedPackagesOverrideForTesting = null
    }

    @After
    fun tearDown() {
        UsageHelper.permissionOverrideForTesting = null
        UsageHelper.usageMinutesOverrideForTesting = null
        UsageHelper.installedPackagesOverrideForTesting = null
        database.close()
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. USAGE HELPER TESTS (Section 65)
    // =========================================================================

    @Test
    fun test01_permissionGranted_returnsTrue() {
        UsageHelper.permissionOverrideForTesting = true
        assertTrue(UsageHelper.hasUsagePermission(context))
    }

    @Test
    fun test02_permissionDenied_returnsFalse() {
        UsageHelper.permissionOverrideForTesting = false
        assertFalse(UsageHelper.hasUsagePermission(context))
    }

    @Test
    fun test03_usageAggregation_sumsMultipleEntries() {
        val records = listOf(
            UsageStatsRecord("com.netflix.mediaclient", 600_000L), // 10 mins
            UsageStatsRecord("com.netflix.mediaclient", 1_200_000L), // 20 mins
            UsageStatsRecord("com.netflix.mediaclient", 300_000L) // 5 mins
        )
        val minutes = UsageHelper.aggregateUsageStats(records, "com.netflix.mediaclient")
        assertEquals(35L, minutes)
    }

    @Test
    fun test04_packageMismatch_ignoresOtherPackages() {
        val records = listOf(
            UsageStatsRecord("com.netflix.mediaclient", 600_000L),
            UsageStatsRecord("com.spotify.music", 1_800_000L),
            UsageStatsRecord("com.google.android.youtube", 3_600_000L)
        )
        val minutes = UsageHelper.aggregateUsageStats(records, "com.netflix.mediaclient")
        assertEquals(10L, minutes)
    }

    @Test
    fun test05_zeroUsage_returnsZeroMinutes() {
        val records = listOf(
            UsageStatsRecord("com.netflix.mediaclient", 0L)
        )
        val minutes = UsageHelper.aggregateUsageStats(records, "com.netflix.mediaclient")
        assertEquals(0L, minutes)
    }

    @Test
    fun test06_negativeOrInvalidValues_safelyHandled() {
        val records = listOf(
            UsageStatsRecord("com.netflix.mediaclient", -50_000L),
            UsageStatsRecord("com.netflix.mediaclient", 120_000L)
        )
        val minutes = UsageHelper.aggregateUsageStats(records, "com.netflix.mediaclient")
        assertEquals(2L, minutes)
    }

    // =========================================================================
    // 2. VALUE RATING TESTS (Section 66)
    // =========================================================================

    @Test
    fun test07_valueRating_eightPlusHours_greatValue() {
        val rating = SubscriptionValueEvaluator.evaluateValueRating(
            usageGranted = true,
            packageName = "com.netflix.mediaclient",
            isAppInstalled = true,
            usageMinutes = 480L // 8.0 hours
        )
        assertEquals(ValueRating.GREAT_VALUE, rating)
    }

    @Test
    fun test08_valueRating_oneHour_moderate() {
        val rating = SubscriptionValueEvaluator.evaluateValueRating(
            usageGranted = true,
            packageName = "com.spotify.music",
            isAppInstalled = true,
            usageMinutes = 60L // 1.0 hour
        )
        assertEquals(ValueRating.MODERATE, rating)
    }

    @Test
    fun test09_valueRating_sevenPointNinetyNineHours_moderate() {
        val rating = SubscriptionValueEvaluator.evaluateValueRating(
            usageGranted = true,
            packageName = "com.spotify.music",
            isAppInstalled = true,
            usageMinutes = 479L // 7.983 hours
        )
        assertEquals(ValueRating.MODERATE, rating)
    }

    @Test
    fun test10_valueRating_halfHour_wasted() {
        val rating = SubscriptionValueEvaluator.evaluateValueRating(
            usageGranted = true,
            packageName = "com.netflix.mediaclient",
            isAppInstalled = true,
            usageMinutes = 30L // 0.5 hours
        )
        assertEquals(ValueRating.WASTED, rating)
    }

    @Test
    fun test11_valueRating_zeroMinutesWithUsageAvailable_wasted() {
        val rating = SubscriptionValueEvaluator.evaluateValueRating(
            usageGranted = true,
            packageName = "com.netflix.mediaclient",
            isAppInstalled = true,
            usageMinutes = 0L // 0 minutes
        )
        assertEquals(ValueRating.WASTED, rating)
    }

    @Test
    fun test12_valueRating_permissionUnavailable_unknown() {
        val rating = SubscriptionValueEvaluator.evaluateValueRating(
            usageGranted = false,
            packageName = "com.netflix.mediaclient",
            isAppInstalled = true,
            usageMinutes = null
        )
        assertEquals(ValueRating.UNKNOWN, rating)
    }

    @Test
    fun test13_valueRating_appNotInstalled_unknown() {
        val rating = SubscriptionValueEvaluator.evaluateValueRating(
            usageGranted = true,
            packageName = "com.netflix.mediaclient",
            isAppInstalled = false,
            usageMinutes = 0L
        )
        assertEquals(ValueRating.UNKNOWN, rating)
    }

    // =========================================================================
    // 3. COST PER HOUR TESTS (Section 67)
    // =========================================================================

    @Test
    fun test14_costPerHour_calculatedAccurately() {
        // ₹600/month, 10 hours (600 minutes)
        val costPerHour = SubscriptionValueEvaluator.calculateCostPerHour(600.0, 600L)
        assertNotNull(costPerHour)
        assertEquals(60.0, costPerHour!!, 0.001)
    }

    @Test
    fun test15_costPerHour_zeroMinutes_returnsNullWithoutDivideByZero() {
        val costPerHour = SubscriptionValueEvaluator.calculateCostPerHour(600.0, 0L)
        assertNull(costPerHour)
        val formatted = SubscriptionValueEvaluator.formatCostPerHour(costPerHour)
        assertEquals("No measurable usage", formatted)
    }

    // =========================================================================
    // 4. ANNUAL COST TESTS (Section 68)
    // =========================================================================

    @Test
    fun test16_annualCost_calculatesTwelveTimesMonthly() {
        val annual = SubscriptionValueEvaluator.calculateAnnualCost(500.0)
        assertEquals(6000.0, annual, 0.001)
    }

    @Test
    fun test17_annualCost_zeroPrice_returnsZero() {
        val annual = SubscriptionValueEvaluator.calculateAnnualCost(0.0)
        assertEquals(0.0, annual, 0.001)
    }

    // =========================================================================
    // 5. BILLING FREQUENCY & PLANNING EQUIVALENT (Section 69)
    // =========================================================================

    @Test
    fun test18_monthlyBilling_equalsActualAmount() {
        val monthly = SubscriptionValueEvaluator.calculateMonthlyEquivalent(500.0, "MONTHLY")
        assertEquals(500.0, monthly, 0.001)
    }

    @Test
    fun test19_quarterlyBilling_dividesByThree() {
        val monthly = SubscriptionValueEvaluator.calculateMonthlyEquivalent(1500.0, "QUARTERLY")
        assertEquals(500.0, monthly, 0.001)
    }

    @Test
    fun test20_annualBilling_dividesByTwelve() {
        val monthly = SubscriptionValueEvaluator.calculateMonthlyEquivalent(12000.0, "ANNUAL")
        assertEquals(1000.0, monthly, 0.001)
    }

    // =========================================================================
    // 6. SUMMARY METRICS & ACCOUNTING (Section 70)
    // =========================================================================

    @Test
    fun test21_summaryMetrics_wastedExcludesUnknownAndCancelled() {
        val items = listOf(
            SubscriptionUiModel(
                id = 1,
                serviceName = "Active Great Value",
                monthlyPrice = 500.0,
                packageName = "com.test.one",
                nextDueDate = 0L,
                usageMinutesLast30Days = 600L,
                costPerHour = 50.0,
                valueStatus = ValueRating.GREAT_VALUE,
                usageState = UsageState.ACTIVE_USAGE,
                status = "ACTIVE"
            ),
            SubscriptionUiModel(
                id = 2,
                serviceName = "Active Wasted",
                monthlyPrice = 300.0,
                packageName = "com.test.two",
                nextDueDate = 0L,
                usageMinutesLast30Days = 0L,
                costPerHour = null,
                valueStatus = ValueRating.WASTED,
                usageState = UsageState.NO_USAGE,
                status = "ACTIVE"
            ),
            SubscriptionUiModel(
                id = 3,
                serviceName = "Active Unknown Usage",
                monthlyPrice = 400.0,
                packageName = null,
                nextDueDate = 0L,
                usageMinutesLast30Days = null,
                costPerHour = null,
                valueStatus = ValueRating.UNKNOWN,
                usageState = UsageState.UNAVAILABLE,
                status = "ACTIVE"
            ),
            SubscriptionUiModel(
                id = 4,
                serviceName = "Cancelled Sub",
                monthlyPrice = 200.0,
                packageName = "com.test.three",
                nextDueDate = 0L,
                usageMinutesLast30Days = 0L,
                costPerHour = null,
                valueStatus = ValueRating.WASTED,
                usageState = UsageState.NO_USAGE,
                status = "CANCELLED"
            )
        )

        val summary = SubscriptionValueEvaluator.calculateSummary(items)
        // Active subs: 1 (500), 2 (300), 3 (400) -> monthly = 1200, count = 3
        assertEquals(1200.0, summary.monthlyRecurringTotal, 0.001)
        assertEquals(14400.0, summary.annualRecurringTotal, 0.001)
        assertEquals(3, summary.activeServicesCount)
        // Wasted: ONLY sub 2 (300 * 12 = 3600). Unknown and Cancelled are NOT counted.
        assertEquals(3600.0, summary.wastedAnnualTotal, 0.001)
    }

    // =========================================================================
    // 7. NO FAKE DATA TEST (Section 71)
    // =========================================================================

    @Test
    fun test22_emptySubscriptions_showsZeroMetricsAndNoFakeData() {
        val summary = SubscriptionValueEvaluator.calculateSummary(emptyList())
        assertEquals(0.0, summary.monthlyRecurringTotal, 0.001)
        assertEquals(0.0, summary.annualRecurringTotal, 0.001)
        assertEquals(0.0, summary.wastedAnnualTotal, 0.001)
        assertEquals(0, summary.activeServicesCount)
    }

    // =========================================================================
    // 8. DATABASE USER ISOLATION
    // =========================================================================

    @Test
    fun test23_userIsolation_userCannotSeeOtherUsersSubscriptions() = runTest {
        val userA = "user_A"
        val userB = "user_B"

        subscriptionDao.insertSubscription(
            SubscriptionEntity(
                userId = userA,
                name = "User A Netflix",
                amount = 649.0,
                billingPeriod = "MONTHLY",
                status = "ACTIVE"
            )
        )
        subscriptionDao.insertSubscription(
            SubscriptionEntity(
                userId = userB,
                name = "User B Spotify",
                amount = 119.0,
                billingPeriod = "MONTHLY",
                status = "ACTIVE"
            )
        )

        val userASubs = subscriptionDao.getSubscriptionsFlow(userA).first()
        val userBSubs = subscriptionDao.getSubscriptionsFlow(userB).first()

        assertEquals(1, userASubs.size)
        assertEquals("User A Netflix", userASubs.first().name)

        assertEquals(1, userBSubs.size)
        assertEquals("User B Spotify", userBSubs.first().name)
    }
}
