package com.example.notification

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.WealthWiseDatabase
import com.example.data.parser.NotificationTransactionSource
import com.example.data.parser.ParserConfidence
import com.example.data.parser.TransactionParserEngine
import com.example.data.repository.IngestionResult
import com.example.data.repository.WealthWiseRepository
import com.example.domain.financial.FinancialStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

typealias NotificationListener = WealthWiseNotificationListenerService

/**
 * Service dedicated to capturing incoming financial notification alerts from bank & UPI apps
 * (e.g. GPay, PhonePe, Paytm, CRED, SMS) when notification access is granted by the user.
 *
 * Ingestion source only: normalizes StatusBarNotification into NotificationTransactionSource,
 * delegates parsing to TransactionParserEngine, and persists valid transactions through WealthWiseRepository.
 */
class WealthWiseNotificationListenerService : NotificationListenerService() {

    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: WealthWiseDatabase? = null
    private var repository: WealthWiseRepository? = null

    override fun onCreate() {
        super.onCreate()
        initDependencies()
        Log.i(TAG, "WealthWiseNotificationListenerService created")
    }

    private fun initDependencies() {
        if (database == null) {
            val db = WealthWiseDatabase.getDatabase(applicationContext)
            database = db
            repository = WealthWiseRepository(db, applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        // Ignore WealthWise's own outgoing alerts
        if (packageName == applicationContext.packageName) return

        val notification = sbn.notification ?: return

        serviceScope.launch {
            try {
                initDependencies()
                val repo = repository ?: return@launch
                val db = database ?: return@launch

                // Determine if this is an SMS app or a direct UPI/Banking notification
                val isSmsApp = packageName.contains("messaging", ignoreCase = true) ||
                        packageName.contains("mms", ignoreCase = true) ||
                        packageName.contains("sms", ignoreCase = true)

                // Verify user preferences for the specific source
                if (isSmsApp && !repo.smsParsingEnabled) return@launch
                if (!isSmsApp && !repo.accountAggregatorSyncEnabled) return@launch

                // Extract notification text safely
                val extras = notification.extras ?: return@launch
                val title = try {
                    extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                } catch (_: Exception) { "" }

                val text = try {
                    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                } catch (_: Exception) { "" }

                val bigText = try {
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
                } catch (_: Exception) { "" }

                val subText = try {
                    extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
                } catch (_: Exception) { "" }

                val body = if (bigText.isNotBlank()) bigText else text
                if (title.isBlank() && body.isBlank()) return@launch

                val source = NotificationTransactionSource(
                    packageName = packageName,
                    title = title,
                    body = body,
                    subText = subText,
                    timestamp = sbn.postTime
                )

                val parsed = TransactionParserEngine.parseNotification(source)

                // Allow all non-rejected parsed transactions to ensure 100% visibility
                if (parsed.confidence == ParserConfidence.REJECTED || parsed.amount <= 0.0) {
                    Log.i(TAG, "Notification skipped: confidence=${parsed.confidence}, reason=${parsed.rejectionReason}")
                    return@launch
                }

                if (parsed.amount <= 0.0) return@launch

                val result = repo.ingestParsedTransaction(parsed)
                when (result) {
                    is IngestionResult.Success -> {
                        Log.i(TAG, "Successfully ingested transaction: ID=${result.transactionId}, ₹${parsed.amount}")
                        WealthWiseNotificationEvaluator.evaluateFinancialAlerts(
                            applicationContext,
                            db,
                            repo
                        )
                    }
                    is IngestionResult.Duplicate -> {
                        Log.i(TAG, "Duplicate suppressed: ${result.reason}")
                    }
                    is IngestionResult.Rejected -> {
                        Log.i(TAG, "Rejected: ${result.message}")
                    }
                    is IngestionResult.Error -> {
                        Log.e(TAG, "Ingestion error: ${result.throwable.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming notification from $packageName", e)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        initDependencies()
        Log.i(TAG, "WealthWiseNotificationListenerService connected and listening")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "WealthWiseNotificationListenerService destroyed and coroutines canceled")
    }

    companion object {
        private const val TAG = "WealthWiseNotifService"
    }
}