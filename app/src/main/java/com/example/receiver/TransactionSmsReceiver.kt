/**
 * WealthWise - Smart Personal Finance Application
 *
 * Development Team:
 * - Purushotham
 * - Surya
 * - Srinivas
 * - Karthik
 */

package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.local.WealthWiseDatabase
import com.example.data.parser.ParserConfidence
import com.example.data.parser.SmsTransactionSource
import com.example.data.parser.TransactionParserEngine
import com.example.data.repository.IngestionResult
import com.example.data.repository.WealthWiseRepository
import com.example.notification.WealthWiseNotificationEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TransactionSmsReceiver listens for SMS_RECEIVED broadcasts, combines multipart messages,
 * extracts sender/body/timestamp, passes them to TransactionParserEngine, and persists
 * valid transactions to Room via WealthWiseRepository.
 */
class TransactionSmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                val db = WealthWiseDatabase.getDatabase(context.applicationContext)
                val repository = WealthWiseRepository(db, context.applicationContext)

                // Check user preference
                if (!repository.smsParsingEnabled) {
                    return@launch
                }

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) {
                    return@launch
                }

                // Group by originating address to safely handle multipart SMS
                val messagesBySender = messages.groupBy { it.displayOriginatingAddress.orEmpty() }

                for ((sender, parts) in messagesBySender) {
                    val fullBody = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
                    val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

                    if (fullBody.isBlank()) continue

                    // Ingestion filter: check if sender header or content matches financial patterns
                    if (!isLikelyFinancialSenderOrContent(sender, fullBody)) {
                        continue
                    }

                    val source = SmsTransactionSource(
                        sender = sender,
                        body = fullBody,
                        timestamp = timestamp
                    )

                    val parsed = TransactionParserEngine.parseSms(source)
                    if (parsed.confidence != ParserConfidence.REJECTED && parsed.amount > 0.0) {
                        val result = repository.ingestParsedTransaction(parsed)
                        if (result is IngestionResult.Success) {
                            Log.i(TAG, "Ingested SMS transaction: ID=${result.transactionId}, ₹${parsed.amount}")
                            WealthWiseNotificationEvaluator.evaluateFinancialAlerts(
                                context.applicationContext,
                                db,
                                repository
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error handling incoming SMS: ${t.localizedMessage}", t)
            } finally {
                // Single safe exit point for PendingResult
                pendingResult.finish()
            }
        }
    }

    private fun isLikelyFinancialSenderOrContent(sender: String, body: String): Boolean {
        val s = sender.uppercase()
        val financialKeywords = listOf(
            "BANK", "HDFC", "SBI", "ICICI", "AXIS", "PNB", "CANARA", "BOB",
            "KOTAK", "UNIONB", "INDUS", "PAYTM", "GPAY", "PHONEPE", "CRED",
            "AMEX", "RBL", "IDBI", "YESB", "SCISMS", "FEDBNK", "AUFIN", "JUPITR",
            "FDRL", "IDFCFB", "BHIM", "NPCI", "SLICE", "UNI", "ONEPRK"
        )
        if (financialKeywords.any { s.contains(it) }) return true

        val bodyLower = body.lowercase()
        return (bodyLower.contains("debited") || bodyLower.contains("credited") ||
                bodyLower.contains("spent") || bodyLower.contains("withdrawn") ||
                bodyLower.contains("deposited") || bodyLower.contains("sent to") ||
                bodyLower.contains("received from") || bodyLower.contains("vpa") ||
                bodyLower.contains("upi ref") || bodyLower.contains("a/c ending"))
    }

    companion object {
        private const val TAG = "TransactionSmsReceiver"
    }
}