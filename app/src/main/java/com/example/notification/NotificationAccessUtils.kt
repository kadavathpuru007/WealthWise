package com.example.notification

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.permission.AccessStatus
import com.example.permission.PermissionManager

/**
 * Utility wrapper for notification permission and listener checks.
 * Delegates system queries to the centralized PermissionManager.
 */
object NotificationAccessUtils {

    /**
     * Checks if notification permission is granted.
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return PermissionManager.checkNotificationPermission(context).isGranted
    }

    /**
     * Checks if notifications are globally enabled for the app at the Android system level.
     */
    fun areNotificationsEnabledOnDevice(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return false
        }
        if (!isNotificationPermissionGranted(context)) {
            return false
        }
        // Also check if our channel is blocked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(WealthWiseNotificationManager.CHANNEL_ID_ALERTS)
            if (channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    /**
     * Checks whether NotificationListenerService access has been granted in Android system settings.
     */
    fun isNotificationListenerAccessGranted(context: Context): Boolean {
        return PermissionManager.checkNotificationListenerAccess(context) == AccessStatus.CONNECTED
    }

    /**
     * Returns an intent to open Android's App Notification Settings.
     */
    fun getAppNotificationSettingsIntent(context: Context): Intent {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            intent.putExtra("app_package", context.packageName)
            intent.putExtra("app_uid", context.applicationInfo.uid)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /**
     * Returns an intent to open Android's Notification Listener (Notification Access) Settings.
     */
    fun getNotificationListenerSettingsIntent(): Intent {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
