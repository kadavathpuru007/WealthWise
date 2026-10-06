package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SubscriptionDao
import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.WealthWiseDatabase
import com.example.data.repository.SubscriptionRepository
import com.example.domain.subscription.SubscriptionCalculations
import com.example.domain.subscription.SubscriptionDetector
import com.example.util.UsageHelper
import com.example.util.UsageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionEngineTest {

    private lateinit var database: WealthWiseDatabase
    private lateinit var subscriptionDao: SubscriptionDao
    private lateinit var repository: SubscriptionRepository
    private lateinit var context: Context

    private val testUserId = "test_user_sub_123"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        subscriptionDao = database.subscriptionDao()
        repository = SubscriptionRepository(
            subscriptionDao = subscriptionDao,
            transactionDao = database.transactionDao()
        )
    }

    @After
    fun tearDown() {
        UsageHelper.permissionOverrideForTesting = null
        UsageHelper.usageMinutesOverrideForTesting = null
        database.close()
    }

    // =========================================================================
    // 1. DICTIONARY & PACKAGE ALIAS TESTS (Section 10)
    // =========================================================================

    @Test
    fun `test known subscription dictionary mappings`() {
        val dict = SubscriptionDetector.KNOWN_SUBSCRIPTION_DICTIONARY

        assertEquals("com.netflix.mediaclient", dict["NETFLIX"])
        assertEquals("com.spotify.music", dict["SPOTIFY"])
        assertEquals("com.amazon.avod.thirdpartyclient", dict["PRIME"])
        assertEquals("com.amazon.avod.thirdpartyclient", dict["AMAZON PRIME"])
        assertEquals("in.startv.hotstar", dict["HOTSTAR"])
        assertEquals("in.startv.hotstar", dict["DISNEY"])
        assertEquals("com.google.android.youtube", dict["YOUTUBE"])
        assertEquals("com.openai.chatgpt", dict["CHATGPT"])
        assertEquals("com.openai.chatgpt", dict["OPENAI"])
        assertEquals("com.graymatrix.did", dict["ZEE5"])
        assertEquals("com.sonyliv", dict["SONYLIV"])
        assertEquals("in.swiggy.android", dict["SWIGGY"])
        assertEquals("com.application.zomato", dict["ZOMATO"])
    }

    // =========================================================================
    // 2. SUBSCRIPTION DETECTION RULES (Section 11 & 12)
    // =========================================================================

    @Test
    fun `test Rule 1 - detect candidate by subscription category or recurring keywords`() {
        val subCategoryTxn = TransactionEntity(
            title = "Cloud Service",
            amount = 499.0,
            type = "EXPENSE",
            category = "Subscription",
            paymentMethod = "UPI",
            date = "2026-09-01"
        )
        val mandateTxn = TransactionEntity(
            title = "Fitness Club Mandate",
            amount = 1500.0,
            type = "EXPENSE",
            category = "Other",
            paymentMethod = "Bank Transfer",
            date = "2026-09-02",
            note = "e-mandate autopay debited"
        )

        assertTrue(SubscriptionDetector.hasSubscriptionEvidence(subCategoryTxn))
        assertTrue(SubscriptionDetector.hasSubscriptionEvidence(mandateTxn))
    }

    @Test
    fun `test Rule 1 - standing instruction SI ambiguity avoids false positives`() {
        // Words containing "si" must NOT be detected as subscription evidence
        val visitTxn = TransactionEntity(
            title = "Doctor Visit",
            amount = 800.0,
            type = "EXPENSE",
            category = "Health",
            paymentMethod = "UPI",
            date = "2026-09-01"
        )
        val depositTxn = TransactionEntity(
            title = "Security Deposit",
            amount = 10000.0,
            type = "EXPENSE",
            category = "Housing",
            paymentMethod = "UPI",
            date = "2026-09-01"
        )
        val silksTxn = TransactionEntity(
            title = "Kalyan Silks Shopping",
            amount = 2500.0,
            type = "EXPENSE",
            category = "Shopping",
            paymentMethod = "Credit Card",
            date = "2026-09-01"
        )

        assertFalse(SubscriptionDetector.hasSubscriptionEvidence(visitTxn))
        assertFalse(SubscriptionDetector.hasSubscriptionEvidence(depositTxn))
        assertFalse(SubscriptionDetector.hasSubscriptionEvidence(silksTxn))

        // Legitimate SI token in payment context MUST be detected
        val legitSiTxn = TransactionEntity(
            title = "Bank SI Debit - Mutual Fund",
            amount = 2000.0,
            type = "EXPENSE",
            category = "Investment",
            paymentMethod = "Bank Transfer",
            date = "2026-09-01",
            note = "NACH SI standing instruction debited"
        )
        assertTrue(SubscriptionDetector.hasSubscriptionEvidence(legitSiTxn))
    }

    @Test
    fun `test Rule 2 - known subscription alias and safe merchant context`() {
        val netflixTxn = TransactionEntity(
            title = "NETFLIX PAYMENT",
            amount = 649.0,
            type = "EXPENSE",
            category = "Entertainment",
            paymentMethod = "Credit Card",
            date = "2026-09-01"
        )
        assertTrue(SubscriptionDetector.matchesKnownSubscriptionAlias(netflixTxn))

        // Amazon grocery must NOT become Amazon Prime subscription
        val amazonGroceryTxn = TransactionEntity(
            title = "Amazon Fresh Grocery",
            amount = 1250.0,
            type = "EXPENSE",
            category = "Food",
            paymentMethod = "UPI",
            date = "2026-09-01"
        )
        assertFalse(SubscriptionDetector.matchesKnownSubscriptionAlias(amazonGroceryTxn))

        // Amazon Prime video MUST be detected
        val amazonPrimeTxn = TransactionEntity(
            title = "Amazon Prime Video Subscription",
            amount = 1499.0,
            type = "EXPENSE",
            category = "Entertainment",
            paymentMethod = "Credit Card",
            date = "2026-09-01"
        )
        assertTrue(SubscriptionDetector.matchesKnownSubscriptionAlias(amazonPrimeTxn))
    }

    // =========================================================================
    // 3. RECURRING INTERVAL DETECTION (Section 13)
    // =========================================================================

    @Test
    fun `test recurring monthly interval detection with 27-32 days spacing and 5 percent tolerance`() {
        // 3 payments ~30 days apart with slight amount variation within 5%
        val txns = listOf(
            TransactionEntity(
                title = "Gym Membership",
                merchant = "Cult Fitness",
                amount = 1000.0,
                type = "EXPENSE",
                category = "Health",
                paymentMethod = "UPI",
                date = "2026-07-01",
                timestamp = 1782864000000L
            ),
            TransactionEntity(
                title = "Gym Membership",
                merchant = "Cult Fitness",
                amount = 1030.0, // +3% variation (within ±5% tolerance)
                type = "EXPENSE",
                category = "Health",
                paymentMethod = "UPI",
                date = "2026-07-31", // 30 days later
                timestamp = 1782864000000L + (30L * 24 * 60 * 60 * 1000L)
            )
        )

        val candidates = SubscriptionDetector.detectPotentialSubscriptions(
            transactions = txns,
            existingSubscriptions = emptyList()
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.first()
        assertEquals("MONTHLY", candidate.billingPeriod)
        assertEquals(30, candidate.intervalDays)
        assertTrue(candidate.confidence >= 0.85)
    }

    // =========================================================================
    // 4. ANNUAL SUBSCRIPTIONS (Section 14)
    // =========================================================================

    @Test
    fun `test annual subscription detection and monthly equivalent calculation`() {
        val annualTxn = TransactionEntity(
            title = "Disney+ Hotstar Annual Super Plan",
            amount = 1499.0,
            type = "EXPENSE",
            category = "Entertainment",
            paymentMethod = "UPI",
            date = "2026-08-15",
            note = "12 months annual subscription"
        )

        val candidates = SubscriptionDetector.detectPotentialSubscriptions(
            transactions = listOf(annualTxn),
            existingSubscriptions = emptyList()
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.first()
        assertEquals("ANNUAL", candidate.billingPeriod)
        assertEquals(1499.0, candidate.amount, 0.01)

        // Monthly equivalent for planning: 1499 / 12 = 124.916...
        val monthlyEquivalent = repository.calculateMonthlyEquivalent(candidate.amount, candidate.billingPeriod)
        assertEquals(124.916, monthlyEquivalent, 0.01)

        // Real transaction remains the real annual charge
        assertEquals(1499.0, candidate.amount, 0.001)
    }

    // =========================================================================
    // 5. IDEMPOTENCY (Section 19)
    // =========================================================================

    @Test
    fun `test detector idempotency - does not duplicate already tracked subscriptions`() {
        val existing = listOf(
            SubscriptionEntity(
                id = 1,
                userId = testUserId,
                name = "Netflix",
                amount = 649.0,
                billingPeriod = "MONTHLY"
            )
        )

        val newTxns = listOf(
            TransactionEntity(
                title = "Netflix Payment",
                merchant = "Netflix",
                amount = 649.0,
                type = "EXPENSE",
                category = "Subscription",
                paymentMethod = "Credit Card",
                date = "2026-09-01"
            )
        )

        val candidates = SubscriptionDetector.detectPotentialSubscriptions(
            transactions = newTxns,
            existingSubscriptions = existing
        )

        // Must not duplicate Netflix
        assertTrue(candidates.isEmpty())
    }

    // =========================================================================
    // 6. ROOM FLOW OBSERVATION & ENTITY MAPPING (Section 15, 17, 18)
    // =========================================================================

    @Test
    fun `test subscriptionDao getSubscriptionsFlow emits on insert and update`() = runBlocking {
        val sub1 = SubscriptionEntity(
            userId = testUserId,
            title = "Spotify",
            amount = 119.0,
            billingCycle = "MONTHLY",
            packageName = "com.spotify.music"
        )

        repository.saveOrUpdateSubscription(sub1)

        val list1 = repository.getSubscriptionsFlow(testUserId).first()
        assertEquals(1, list1.size)
        assertEquals("Spotify", list1.first().title)
        assertEquals(119.0, list1.first().amount, 0.001)
        assertEquals("com.spotify.music", list1.first().packageName)
        assertFalse(list1.first().isInactive)

        // Update amount
        val sub1Updated = list1.first().copy(amount = 129.0)
        repository.saveOrUpdateSubscription(sub1Updated)

        val list2 = repository.getSubscriptionsFlow(testUserId).first()
        assertEquals(1, list2.size)
        assertEquals(129.0, list2.first().amount, 0.001)
    }

    // =========================================================================
    // 7. DASHBOARD METRICS CALCULATIONS (Section 26, 27, 28, 29, 30)
    // =========================================================================

    @Test
    fun `test dashboard financial metrics calculation`() {
        val subs = listOf(
            // Active monthly: ₹649/mo
            SubscriptionEntity(id = 1, name = "Netflix", amount = 649.0, billingPeriod = "MONTHLY", status = "ACTIVE"),
            // Active annual: ₹1200/yr -> ₹100/mo
            SubscriptionEntity(id = 2, name = "Disney+", amount = 1200.0, billingPeriod = "ANNUAL", status = "ACTIVE"),
            // Inactive monthly (flagged unused): ₹199/mo -> wasted annual = ₹2388
            SubscriptionEntity(id = 3, name = "Gym App", amount = 199.0, billingPeriod = "MONTHLY", status = "ACTIVE", isUnusedFlagged = true),
            // Cancelled monthly: ₹299/mo -> saved annual = ₹3588
            SubscriptionEntity(id = 4, name = "Magazine", amount = 299.0, billingPeriod = "MONTHLY", status = "CANCELLED")
        )

        // Monthly commitment sums active non-cancelled: 649 + (1200 / 12) + 199 = 948.0
        val monthlyRecurring = repository.calculateMonthlyRecurringCommitment(subs)
        assertEquals(948.0, monthlyRecurring, 0.01)

        // When usage access granted:
        // Active services count non-cancelled and non-inactive: 2 (Netflix, Disney+)
        val activeServicesWithUsage = repository.calculateActiveServices(subs, usageGranted = true)
        assertEquals(2, activeServicesWithUsage)

        // When usage access NOT granted:
        // Do not treat inactive; count all active non-cancelled: 3
        val activeServicesWithoutUsage = repository.calculateActiveServices(subs, usageGranted = false)
        assertEquals(3, activeServicesWithoutUsage)

        // Wasted on inactive apps (only when usage granted): 199 * 12 = 2388.0
        val wastedWithUsage = repository.calculateWastedOnInactiveApps(subs, usageGranted = true)
        assertEquals(2388.0, wastedWithUsage, 0.01)

        // When usage not granted: wasted = 0.0 (never fabricate inactivity)
        val wastedWithoutUsage = repository.calculateWastedOnInactiveApps(subs, usageGranted = false)
        assertEquals(0.0, wastedWithoutUsage, 0.001)

        // Saved by cancelling (only actual recorded cancellations): 299 * 12 = 3588.0
        val savedCancelling = repository.calculateSavedByCancelling(subs)
        assertEquals(3588.0, savedCancelling, 0.01)
    }

    // =========================================================================
    // 8. USAGE STATUS & LACK OF PERMISSION RULES (Section 8, 21, 22)
    // =========================================================================

    @Test
    fun `test lack of usage permission does not mark apps inactive`() = runBlocking {
        UsageHelper.permissionOverrideForTesting = false

        val sub = SubscriptionEntity(
            id = 1,
            userId = testUserId,
            name = "Netflix",
            amount = 649.0,
            billingPeriod = "MONTHLY",
            linkedPackageName = "com.netflix.mediaclient"
        )
        repository.saveOrUpdateSubscription(sub)

        // In test environment without Usage Access granted
        val usageMap = repository.auditSubscriptionUsage(context, listOf(sub))

        // State must be USAGE_PERMISSION_REQUIRED
        assertEquals(UsageStatus.USAGE_PERMISSION_REQUIRED, usageMap[1])

        // Subscription must NOT be marked inactive
        val dbSubs = repository.getSubscriptionsFlow(testUserId).first()
        assertTrue(dbSubs.isNotEmpty())
        assertFalse(dbSubs.first().isInactive)
    }

    @Test
    fun `test usage under 10 minutes marks app inactive only when permission is granted`() = runBlocking {
        UsageHelper.permissionOverrideForTesting = true
        UsageHelper.usageMinutesOverrideForTesting = { 4L } // 4 minutes < 10 threshold

        val sub = SubscriptionEntity(
            id = 2,
            userId = testUserId,
            name = "Spotify",
            amount = 199.0,
            billingPeriod = "MONTHLY",
            linkedPackageName = "com.spotify.music"
        )
        repository.saveOrUpdateSubscription(sub)

        val usageMap = repository.auditSubscriptionUsage(context, listOf(sub))
        assertEquals(UsageStatus.USAGE_AVAILABLE_INACTIVE, usageMap[2])

        val dbSubs = repository.getSubscriptionsFlow(testUserId).first()
        assertTrue(dbSubs.isNotEmpty())
        assertTrue(dbSubs.first().isInactive)
    }

    @Test
    fun `test usage 10 minutes or more marks app active`() = runBlocking {
        UsageHelper.permissionOverrideForTesting = true
        UsageHelper.usageMinutesOverrideForTesting = { 65L } // 65 minutes >= 10 threshold

        val sub = SubscriptionEntity(
            id = 3,
            userId = testUserId,
            name = "Hotstar",
            amount = 299.0,
            billingPeriod = "MONTHLY",
            linkedPackageName = "in.startv.hotstar"
        )
        repository.saveOrUpdateSubscription(sub)

        val usageMap = repository.auditSubscriptionUsage(context, listOf(sub))
        assertEquals(UsageStatus.USAGE_AVAILABLE_ACTIVE, usageMap[3])

        val dbSubs = repository.getSubscriptionsFlow(testUserId).first()
        assertTrue(dbSubs.isNotEmpty())
        assertFalse(dbSubs.first().isInactive)
    }
}
