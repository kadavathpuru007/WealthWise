package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object WealthWiseNotificationManager {

    private const val TAG = "WealthWiseNotif"

    const val CHANNEL_ID_ALERTS = "wealthwise_financial_alerts"
    const val CHANNEL_NAME_ALERTS = "WealthWise Alerts"
    const val CHANNEL_DESC_ALERTS = "Important financial alerts from WealthWise"

    const val EXTRA_NAV_SCREEN = "EXTRA_NAV_SCREEN"

    // Base Notification IDs
    private const val NOTIF_ID_BUDGET_ALMOST_EXHAUSTED_BASE = 1000
    private const val NOTIF_ID_BUDGET_EXCEEDED_BASE = 1500
    private const val NOTIF_ID_UPCOMING_EMI_BASE = 2000
    private const val NOTIF_ID_UPCOMING_BILL_BASE = 2500
    private const val NOTIF_ID_GOAL_BASE = 3000
    private const val NOTIF_ID_SYNC_ATTENTION = 4001
    private const val NOTIF_ID_TEST = 5001

    /**
     * Initializes the notification channel on Android 8.0 (API 26) and above.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            val channel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                CHANNEL_NAME_ALERTS,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC_ALERTS
                enableLights(true)
                lightColor = Color.parseColor("#1E3A8A") // Fintech Blue
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID_ALERTS")
        }
    }

    private fun createNavigationPendingIntent(
        context: Context,
        screenName: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAV_SCREEN, screenName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    /**
     * 1. Budget almost exhausted:
     * "Food budget is almost exhausted"
     * "You've used ₹4,500 of your ₹5,000 Food budget."
     */
    fun postBudgetAlmostExhausted(
        context: Context,
        category: String,
        spent: Double,
        limit: Double
    ) {
        if (!NotificationAccessUtils.isNotificationPermissionGranted(context)) {
            Log.w(TAG, "Notification permission not granted, skipping budget alert")
            return
        }

        createNotificationChannel(context)

        val pendingIntent = createNavigationPendingIntent(context, "BUDGET", category.hashCode())
        val title = "$category budget is almost exhausted"
        val message = "You've used ₹${spent.toInt()} of your ₹${limit.toInt()} $category budget."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setColor(0xFFF59E0B.toInt()) // Amber warning
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notifId = NOTIF_ID_BUDGET_ALMOST_EXHAUSTED_BASE + (Math.abs(category.hashCode()) % 400)
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            Log.i(TAG, "Posted budget almost exhausted notification: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification: SecurityException", e)
        }
    }

    /**
     * 2. Budget exceeded:
     * "Food budget exceeded"
     * "You've spent ₹6,200 of your ₹5,000 Food budget. Over budget: ₹1,200."
     */
    fun postBudgetExceeded(
        context: Context,
        category: String,
        spent: Double,
        limit: Double
    ) {
        if (!NotificationAccessUtils.isNotificationPermissionGranted(context)) {
            Log.w(TAG, "Notification permission not granted, skipping budget exceeded alert")
            return
        }

        createNotificationChannel(context)

        val pendingIntent = createNavigationPendingIntent(context, "BUDGET", category.hashCode() + 50)
        val overBudget = spent - limit
        val title = "$category budget exceeded"
        val message = "You've spent ₹${spent.toInt()} of your ₹${limit.toInt()} $category budget. Over budget: ₹${overBudget.toInt()}."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setColor(0xFFEF4444.toInt()) // Danger Red
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notifId = NOTIF_ID_BUDGET_EXCEEDED_BASE + (Math.abs(category.hashCode()) % 400)
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            Log.i(TAG, "Posted budget exceeded notification: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification: SecurityException", e)
        }
    }

    /**
     * 3a. Upcoming EMI:
     * "Upcoming EMI"
     * "Your ₹12,000 EMI is due tomorrow."
     */
    fun postUpcomingEmi(
        context: Context,
        loanId: Int,
        loanTitle: String,
        emiAmount: Double
    ) {
        if (!NotificationAccessUtils.isNotificationPermissionGranted(context)) return

        createNotificationChannel(context)

        val pendingIntent = createNavigationPendingIntent(context, "LOANS_DEBTS", 2000 + loanId)
        val title = "Upcoming EMI"
        val message = "Your ₹${emiAmount.toInt()} EMI for $loanTitle is due tomorrow."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF6366F1.toInt()) // Indigo
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_UPCOMING_EMI_BASE + (loanId % 400), notification)
            Log.i(TAG, "Posted upcoming EMI notification: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification: SecurityException", e)
        }
    }

    /**
     * 3b. Upcoming Bill:
     * "Upcoming Bill"
     * "Your ₹1,499 bill for Airtel Broadband is due tomorrow."
     */
    fun postUpcomingBill(
        context: Context,
        billId: Int,
        billTitle: String,
        amount: Double
    ) {
        if (!NotificationAccessUtils.isNotificationPermissionGranted(context)) return

        createNotificationChannel(context)

        val pendingIntent = createNavigationPendingIntent(context, "HOME", 2500 + billId)
        val title = "Upcoming Bill"
        val message = "Your ₹${amount.toInt()} bill for $billTitle is due tomorrow."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF3B82F6.toInt()) // Blue
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_UPCOMING_BILL_BASE + (billId % 400), notification)
            Log.i(TAG, "Posted upcoming bill notification: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification: SecurityException", e)
        }
    }

    /**
     * 4a. Goal falling behind:
     * "Your Laptop goal is falling behind"
     * "You're currently about ₹2,000/month behind the pace needed to reach your target."
     */
    fun postGoalBehind(
        context: Context,
        goalId: Long,
        goalTitle: String,
        shortfallMonthly: Double
    ) {
        if (!NotificationAccessUtils.isNotificationPermissionGranted(context)) return

        createNotificationChannel(context)

        val pendingIntent = createNavigationPendingIntent(context, "GOALS", 3000 + (goalId % 1000).toInt())
        val title = "Your $goalTitle goal is falling behind"
        val message = "You're currently about ₹${shortfallMonthly.toInt()}/month behind the pace needed to reach your target."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setColor(0xFF0D9488.toInt()) // Teal
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_GOAL_BASE + (goalId % 400).toInt(), notification)
            Log.i(TAG, "Posted goal behind notification: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification: SecurityException", e)
        }
    }

    fun postGoalBehind(
        context: Context,
        goalId: Int,
        goalTitle: String,
        shortfallMonthly: Double
    ) = postGoalBehind(context, goalId.toLong(), goalTitle, shortfallMonthly)

    /**
     * 4b. Goal affordability:
     * "Your goal may need attention"
     * "Your current budget leaves about ₹4,000/month, while this goal requires about ₹6,000/month. Shortfall: ₹2,000/month."
     */
    fun postGoalAffordability(
        context: Context,
        goalId: Long,
        goalTitle: String,
        availableMonthly: Double,
        requiredMonthly: Double,
        shortfall: Double
    ) {
        if (!NotificationAccessUtils.isNotificationPermissionGranted(context)) return

        createNotificationChannel(context)

        val pendingIntent = createNavigationPendingIntent(context, "GOALS", 3500 + (goalId % 1000).toInt())
        val title = "Your goal may need attention"
        val message = "Your current budget leaves about ₹${availableMonthly.toInt()}/month, while $goalTitle requires about ₹${requiredMonthly.toInt()}/month. Shortfall: ₹${shortfall.toInt()}/month."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setColor(0xFFF97316.toInt()) // Orange
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_GOAL_BASE + (goalId % 400).toInt(), notification)
            Log.i(TAG, "Posted goal affordability notification: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification: SecurityException", e)
        }
    }

    fun postGoalAffordability(
        context: Context,
        goalId: Int,
        goalTitle: String,
        availableMonthly: Double,
        requiredMonthly: Double,
        shortfall: Double
    ) = postGoalAffordability(context, goalId.toLong(), goalTitle, availableMonthly, requiredMonthly, shortfall)

    /**
     * 5. Sync requires attention:
     * "Sync requires attention"
     * "Notification access is disabled. WealthWise cannot detect new financial notifications."
     * Tapping opens notification access settings.
     */
    fun postSyncRequiresAttention(context: Context) {
        if (!NotificationAccessUtils.isNotificationPermissionGranted(context)) return

        createNotificationChannel(context)

        // Try to open Notification Listener settings; fallback to Settings screen
        val settingsIntent = NotificationAccessUtils.getNotificationListenerSettingsIntent()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, 4001, settingsIntent, flags)

        val title = "Sync requires attention"
        val message = "Notification access is disabled. WealthWise cannot detect new financial notifications."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(0xFFEF4444.toInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_SYNC_ATTENTION, notification)
            Log.i(TAG, "Posted sync requires attention notification")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification: SecurityException", e)
        }
    }

    /**
     * Test notification: to verify Android notification shade delivery.
     */
    fun postTestNotification(context: Context) {
        if (!NotificationAccessUtils.isNotificationPermissionGranted(context)) return

        createNotificationChannel(context)

        val pendingIntent = createNavigationPendingIntent(context, "SETTINGS", 5001)
        val title = "WealthWise Alert Test"
        val message = "Device notifications are successfully working! Real financial alerts will appear here."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(0xFF1E3A8A.toInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_TEST, notification)
            Log.i(TAG, "Posted test notification")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot post notification: SecurityException", e)
        }
    }
}
