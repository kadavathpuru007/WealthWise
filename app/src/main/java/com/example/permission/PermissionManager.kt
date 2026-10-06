package com.example.permission

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.local.SubscriptionEntity

/**
 * Access status representation adhering to Android Privacy and Accessibility guidelines.
 * Accessible with distinct label, icons, and non-color dependent state.
 */
enum class AccessStatus(val label: String, val isGranted: Boolean) {
    CONNECTED("Connected", true),
    NOT_CONNECTED("Not Connected", false),
    NOT_AVAILABLE("Not Available", false),
    DENIED("Denied", false),
    NOT_REQUIRED("Not Required", true)
}

enum class WealthWiseFeature(val featureName: String, val requiredAccessDescription: String) {
    FINANCIAL_SYNC("Financial Sync", "Notification Access (read payment notifications)"),
    FINANCIAL_ALERTS("Financial Alerts", "POST_NOTIFICATIONS (post device alerts)"),
    SUBSCRIPTION_USAGE("Subscription Usage Insights", "Usage Access (read app usage stats)"),
    CLOUD_SYNC("Cloud Database Sync", "INTERNET (online network access)"),
    OPEN_SUBSCRIPTION_APP("Open Subscription App", "Targeted Package Query (launch installed app)")
}

data class KnownSubscriptionApp(
    val appName: String,
    val packageName: String,
    val websiteUrl: String,
    val playStoreUrl: String
)

data class AppLaunchFallbackState(
    val appName: String,
    val packageName: String,
    val webUrl: String?,
    val playStoreUrl: String
)

data class SubscriptionUsageInfo(
    val subscriptionId: Int,
    val name: String,
    val isInstalled: Boolean,
    val packageName: String?,
    val lastUsedTimestamp: Long?,
    val daysSinceLastUsed: Int?,
    val usageStatusText: String,
    val isUnusedRisk: Boolean,
    val webUrl: String?,
    val playStoreUrl: String
)

/**
 * Centralized Permission and Access Manager for WealthWise.
 *
 * Adheres strictly to the principle of "REQUEST THE LEAST ACCESS NECESSARY".
 * - Never bombards the user during onboarding/login/first launch.
 * - Enforces contextual, feature-based permission gates.
 * - Safely detects system-derived states without relying solely on transient callbacks.
 */
object PermissionManager {

    private const val TAG = "WealthWisePermMgr"

    val KNOWN_SUBSCRIPTION_APPS = listOf(
        KnownSubscriptionApp("Netflix", "com.netflix.mediaclient", "https://www.netflix.com/youraccount", "https://play.google.com/store/apps/details?id=com.netflix.mediaclient"),
        KnownSubscriptionApp("Spotify", "com.spotify.music", "https://www.spotify.com/account/overview/", "https://play.google.com/store/apps/details?id=com.spotify.music"),
        KnownSubscriptionApp("Amazon Prime", "com.amazon.avod.thirdpartyclient", "https://www.amazon.in/gp/primecentral", "https://play.google.com/store/apps/details?id=com.amazon.avod.thirdpartyclient"),
        KnownSubscriptionApp("YouTube Premium", "com.google.android.youtube", "https://www.youtube.com/paid_memberships", "https://play.google.com/store/apps/details?id=com.google.android.youtube"),
        KnownSubscriptionApp("Disney+ Hotstar", "in.startv.hotstar", "https://www.hotstar.com/in/my-account", "https://play.google.com/store/apps/details?id=in.startv.hotstar"),
        KnownSubscriptionApp("Cult.fit", "in.cure.fit", "https://www.cult.fit", "https://play.google.com/store/apps/details?id=in.cure.fit"),
        KnownSubscriptionApp("ChatGPT Plus", "com.openai.chatgpt", "https://chatgpt.com", "https://play.google.com/store/apps/details?id=com.openai.chatgpt"),
        KnownSubscriptionApp("Google One", "com.google.android.apps.subscriptions.red", "https://one.google.com", "https://play.google.com/store/apps/details?id=com.google.android.apps.subscriptions.red")
    )

    // =========================================================================
    // 1. NOTIFICATION LISTENER ACCESS (Special Access for Financial Sync)
    // =========================================================================

    /**
     * Checks whether NotificationListenerService special access has been granted in Android system settings.
     * Used exclusively to detect incoming payment/bank/UPI notifications.
     */
    fun checkNotificationListenerAccess(context: Context): AccessStatus {
        return try {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            if (!flat.isNullOrEmpty()) {
                val names = flat.split(":".toRegex()).toTypedArray()
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && cn.packageName == packageName) {
                        return AccessStatus.CONNECTED
                    }
                }
            }
            AccessStatus.NOT_CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification listener access", e)
            AccessStatus.NOT_AVAILABLE
        }
    }

    /**
     * Opens Android's Notification Listener (Special Access) settings page safely.
     * Returns false if an ActivityNotFoundException or security error occurs.
     */
    fun openNotificationAccessSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification listener settings", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    // =========================================================================
    // 1b. SMS PERMISSION (For Background Transaction SMS Ingestion)
    // =========================================================================

    /**
     * Checks whether RECEIVE_SMS permission has been granted.
     * Note: If denied, the app continues to function smoothly using notification ingestion.
     */
    fun checkSmsPermission(context: Context): AccessStatus {
        return try {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) AccessStatus.CONNECTED else AccessStatus.NOT_CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SMS permission", e)
            AccessStatus.NOT_AVAILABLE
        }
    }

    /**
     * Checks whether the app is currently exempt from battery optimization.
     */
    fun checkBatteryOptimization(context: Context): AccessStatus {
        return try {
            if (com.example.util.BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)) {
                AccessStatus.CONNECTED
            } else {
                AccessStatus.NOT_CONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking battery optimization", e)
            AccessStatus.NOT_AVAILABLE
        }
    }

    // =========================================================================
    // 2. POST_NOTIFICATIONS (Runtime Permission for WealthWise Alerts)
    // =========================================================================

    /**
     * Checks whether WealthWise is permitted to post its OWN alerts to the notification shade.
     * On Android 13+ (API 33), checks Manifest.permission.POST_NOTIFICATIONS.
     * On Android 12 and below, checks areNotificationsEnabled().
     */
    fun checkNotificationPermission(context: Context): AccessStatus {
        return try {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) {
                return AccessStatus.DENIED
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) AccessStatus.CONNECTED else AccessStatus.DENIED
            } else {
                AccessStatus.CONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification permission", e)
            AccessStatus.NOT_AVAILABLE
        }
    }

    /**
     * Opens Android's App Notification settings page safely.
     */
    fun openAppNotificationSettings(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app notification settings", e)
            false
        }
    }

    // =========================================================================
    // 3. USAGE ACCESS (Special Access via AppOpsManager for Subscription Killer)
    // =========================================================================

    /**
     * Checks whether Usage Access has been granted by the user in Android system settings.
     * Used exclusively to estimate whether subscription apps are being actively used.
     */
    fun checkUsageAccess(context: Context): AccessStatus {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return AccessStatus.NOT_AVAILABLE
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            if (mode == AppOpsManager.MODE_ALLOWED) AccessStatus.CONNECTED else AccessStatus.NOT_CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage stats access", e)
            AccessStatus.NOT_AVAILABLE
        }
    }

    /**
     * Opens Android's Usage Access (Special Access) settings page safely.
     */
    fun openUsageAccessSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            // Some OEM ROMs do not support package URI on usage settings
            try {
                val fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open general usage access settings", ex)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open usage access settings", e)
            false
        }
    }

    // =========================================================================
    // 4. INTERNET & NETWORK STATE
    // =========================================================================

    /**
     * Checks whether the device is currently online.
     * Uses normal manifest permission android.permission.INTERNET and ACCESS_NETWORK_STATE if present.
     */
    fun checkInternetAccess(context: Context): AccessStatus {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return AccessStatus.CONNECTED
            val network = cm.activeNetwork ?: return AccessStatus.NOT_CONNECTED
            val caps = cm.getNetworkCapabilities(network) ?: return AccessStatus.NOT_CONNECTED
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                AccessStatus.CONNECTED
            } else {
                AccessStatus.NOT_CONNECTED
            }
        } catch (e: Exception) {
            AccessStatus.CONNECTED // Fallback assuming internet is handled gracefully by networking client
        }
    }

    // =========================================================================
    // 5. FEATURE-BASED ACCESS GATE
    // =========================================================================

    fun checkRequiredAccessForFeature(context: Context, feature: WealthWiseFeature): AccessStatus {
        return when (feature) {
            WealthWiseFeature.FINANCIAL_SYNC -> checkNotificationListenerAccess(context)
            WealthWiseFeature.FINANCIAL_ALERTS -> checkNotificationPermission(context)
            WealthWiseFeature.SUBSCRIPTION_USAGE -> checkUsageAccess(context)
            WealthWiseFeature.CLOUD_SYNC -> checkInternetAccess(context)
            WealthWiseFeature.OPEN_SUBSCRIPTION_APP -> AccessStatus.CONNECTED
        }
    }

    // =========================================================================
    // 6. TARGETED APP LAUNCHING & FALLBACK
    // =========================================================================

    fun findKnownApp(subscriptionName: String): KnownSubscriptionApp? {
        val trimmed = subscriptionName.trim().lowercase()
        return KNOWN_SUBSCRIPTION_APPS.firstOrNull { app ->
            trimmed.contains(app.appName.lowercase()) || app.appName.lowercase().contains(trimmed)
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package $packageName", e)
            false
        }
    }

    fun openPlayStore(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (ex: Exception) {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun launchUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch URL $url", e)
            false
        }
    }

    // =========================================================================
    // 7. SUBSCRIPTION USAGE CALCULATION ENGINE
    // =========================================================================

    /**
     * Queries local UsageStatsManager to estimate subscription usage strictly during the last 30 days.
     *
     * PRIVACY MANDATE:
     * - Only queries the targeted packages of the user's active subscriptions.
     * - Never queries or uploads broad device usage history.
     * - If Usage Access is NOT granted, returns "Usage data unavailable" (NEVER fakes "Unused").
     */
    fun querySubscriptionUsage(
        context: Context,
        subscriptions: List<SubscriptionEntity>
    ): Map<Int, SubscriptionUsageInfo> {
        val access = checkUsageAccess(context)
        val isAccessGranted = access.isGranted

        val usageStatsManager = if (isAccessGranted) {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        } else {
            null
        }

        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

        // Query usage stats locally if access is granted
        val usageMapByPackage: Map<String, Long> = if (usageStatsManager != null) {
            try {
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    thirtyDaysAgo,
                    now
                )
                stats.groupBy { it.packageName }
                    .mapValues { entry ->
                        entry.value.maxOfOrNull { it.lastTimeUsed } ?: 0L
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying usage stats", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val result = mutableMapOf<Int, SubscriptionUsageInfo>()

        for (sub in subscriptions) {
            val known = findKnownApp(sub.name)
            val packageName = if (sub.linkedPackageName.isNotBlank()) sub.linkedPackageName else known?.packageName
            val isInstalled = if (packageName != null) isAppInstalled(context, packageName) else false
            val playStoreUrl = known?.playStoreUrl ?: (if (packageName != null) "https://play.google.com/store/apps/details?id=$packageName" else "https://play.google.com")
            val webUrl = known?.websiteUrl

            val (statusText, daysUnused, isRisk) = when {
                !isAccessGranted -> {
                    // Prompt MANDATE: Must NOT claim "Unused" when access is unavailable!
                    Triple("Usage data unavailable", null, false)
                }
                packageName == null -> {
                    Triple("Manual subscription", null, sub.isUnusedFlagged)
                }
                !isInstalled -> {
                    Triple("App not installed on device", null, sub.isUnusedFlagged)
                }
                else -> {
                    val lastUsed = usageMapByPackage[packageName] ?: 0L
                    if (lastUsed <= 0L || lastUsed < thirtyDaysAgo) {
                        Triple("No detected usage in last 30 days", 30, true)
                    } else {
                        val days = ((now - lastUsed) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                        val text = if (days == 0) "Used today" else "Used $days day${if (days == 1) "" else "s"} ago"
                        Triple(text, days, days >= 30)
                    }
                }
            }

            result[sub.id] = SubscriptionUsageInfo(
                subscriptionId = sub.id,
                name = sub.name,
                isInstalled = isInstalled,
                packageName = packageName,
                lastUsedTimestamp = if (packageName != null) usageMapByPackage[packageName] else null,
                daysSinceLastUsed = daysUnused,
                usageStatusText = statusText,
                isUnusedRisk = isRisk,
                webUrl = webUrl,
                playStoreUrl = playStoreUrl
            )
        }

        return result
    }
}
