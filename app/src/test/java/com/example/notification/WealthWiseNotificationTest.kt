package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.repository.WealthWiseRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WealthWiseNotificationTest {

    private lateinit var context: Context
    private lateinit var database: WealthWiseDatabase
    private lateinit var repository: WealthWiseRepository
    private lateinit var deduplicator: WealthWiseNotificationDeduplicator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WealthWiseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WealthWiseRepository(database, context)
        repository.isLoggedIn = true
        repository.hasCompletedSetup = true
        deduplicator = WealthWiseNotificationDeduplicator(context)
        deduplicator.resetAll()
    }

    @Test
    fun testNotificationChannelCreated() {
        WealthWiseNotificationManager.createNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel: NotificationChannel? = nm.getNotificationChannel(WealthWiseNotificationManager.CHANNEL_ID_ALERTS)

        assertNotNull("Alerts channel must not be null", channel)
        assertEquals("WealthWise Alerts", channel?.name?.toString())
        assertEquals("Important financial alerts from WealthWise", channel?.description)
    }

    @Test
    fun testDeduplicatorPreventsDuplicateAlerts() {
        val key = WealthWiseNotificationDeduplicator.budgetAlmostExhaustedKey("Food", "2026-09")
        assertFalse(deduplicator.hasBeenSent(key))

        deduplicator.recordSent(key)
        assertTrue(deduplicator.hasBeenSent(key))

        // Reset
        deduplicator.clearKey(key)
        assertFalse(deduplicator.hasBeenSent(key))
    }

    @Test
    fun testNoNotificationsWhenNoObligations() = runBlocking {
        // Empty loans and empty bills
        repository.pushNotificationsEnabled = true
        repository.billEmiAlertsEnabled = true

        val alerts = WealthWiseNotificationEvaluator.evaluateFinancialAlerts(context, database, repository)
        val billAlerts = alerts.filter { it.contains("Bill") || it.contains("EMI") }
        assertTrue("No EMI or Bill notifications when there are no obligations", billAlerts.isEmpty())
    }

    @Test
    fun testBudgetExceededAlertTriggered() = runBlocking {
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        repository.pushNotificationsEnabled = true
        repository.budgetAlertsEnabled = true

        // Insert Food budget of 5000
        database.budgetDao().insertOrUpdate(BudgetEntity(category = "Food", monthlyLimit = 5000.0))

        // Insert Food expense of 5500
        database.transactionDao().insertTransaction(
            TransactionEntity(
                title = "Dinner Party",
                amount = 5500.0,
                type = "EXPENSE",
                category = "Food",
                paymentMethod = "UPI",
                date = "$currentMonth-10"
            )
        )

        val alerts = WealthWiseNotificationEvaluator.evaluateFinancialAlerts(context, database, repository)
        assertTrue("Budget exceeded alert should be triggered", alerts.any { it.contains("Budget exceeded: Food") })

        // Second run should deduplicate
        val secondRunAlerts = WealthWiseNotificationEvaluator.evaluateFinancialAlerts(context, database, repository)
        assertFalse("Second evaluation should deduplicate", secondRunAlerts.any { it.contains("Budget exceeded: Food") })
    }
}
