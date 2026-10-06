package com.example.util

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * Three distinct legacy states for subscription app usage tracking.
 * Maintained for backward compatibility.
 */
enum class UsageStatus(val label: String) {
    USAGE_AVAILABLE_ACTIVE("Active engagement"),
    USAGE_AVAILABLE_INACTIVE("Potentially inactive"),
    USAGE_PERMISSION_REQUIRED("Usage data unavailable")
}

/**
 * Explicit result type representing app usage query outcome.
 * Adheres strictly to the rule: Permission denied != unused, App not installed != unused.
 */
sealed interface UsageResult {
    data class Available(val minutes: Long) : UsageResult
    data object PermissionRequired : UsageResult
    data object NoUsage : UsageResult
    data object AppNotInstalled : UsageResult
    data class Error(val message: String) : UsageResult
}

/**
 * Lightweight data holder for testing and aggregating foreground usage time.
 */
data class UsageStatsRecord(
    val packageName: String,
    val totalTimeInForeground: Long
)

/**
 * Android UsageStatsManager and AppOpsManager helper for privacy-safe app usage tracking.
 */
object UsageHelper {

    private const val TAG = "UsageHelper"
    private const val INACTIVE_THRESHOLD_MINUTES = 10L

    @set:VisibleForTesting
    var permissionOverrideForTesting: Boolean? = null

    @set:VisibleForTesting
    var usageMinutesOverrideForTesting: ((String) -> Long)? = null

    @set:VisibleForTesting
    var installedPackagesOverrideForTesting: Set<String>? = null

    /**
     * Checks whether Usage Access has been granted to WealthWise by the user in system settings.
     * Uses AppOpsManager.OPSTR_GET_USAGE_STATS and safely handles all API versions and exceptions.
     */
    fun hasUsagePermission(context: Context): Boolean {
        permissionOverrideForTesting?.let { return it }
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false

            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
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
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage permission", e)
            false
        }
    }

    /**
     * Safely opens Android's Usage Access (Special Access) settings screen.
     * Handles ActivityNotFoundException without crashing.
     */
    fun openUsageSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "ACTION_USAGE_ACCESS_SETTINGS not found on device", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch usage access settings", e)
        }
    }

    /**
     * Backward-compatible alias for [openUsageSettings].
     */
    fun openUsageAccessSettings(context: Context) {
        openUsageSettings(context)
    }

    /**
     * Verifies if an application package is installed on the local device.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        installedPackagesOverrideForTesting?.let {
            return it.contains(packageName)
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Pure function to aggregate foreground time from usage records for a specific package.
     * Safely clamps invalid/negative values and returns total minutes.
     */
    fun aggregateUsageStats(records: List<UsageStatsRecord>, targetPackage: String): Long {
        if (targetPackage.isBlank() || records.isEmpty()) return 0L
        var totalForegroundMs = 0L
        for (record in records) {
            if (record.packageName == targetPackage) {
                val ms = record.totalTimeInForeground
                if (ms > 0L) {
                    totalForegroundMs += ms
                }
            }
        }
        return (totalForegroundMs / 60_000L).coerceAtLeast(0L)
    }

    /**
     * Queries total foreground minutes for [packageName] over the rolling 30 days.
     *
     * Window: rolling 30 days (now - 30L * 24L * 60L * 60L * 1000L).
     * Returns TOTAL MINUTES.
     * Returns 0L if no permission or no usage data recorded.
     * Never returns negative numbers.
     */
    fun getMonthlyAppUsageMinutes(context: Context, packageName: String): Long {
        if (packageName.isBlank()) return 0L
        usageMinutesOverrideForTesting?.let { return it.invoke(packageName).coerceAtLeast(0L) }
        if (!hasUsagePermission(context)) return 0L

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return 0L

            val endTime = System.currentTimeMillis()
            val startTime = endTime - (30L * 24L * 60L * 60L * 1000L)

            val statsList: List<UsageStats>? = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_MONTHLY,
                startTime,
                endTime
            )

            if (statsList.isNullOrEmpty()) return 0L

            val records = statsList.map {
                UsageStatsRecord(
                    packageName = it.packageName ?: "",
                    totalTimeInForeground = it.totalTimeInForeground
                )
            }
            aggregateUsageStats(records, packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying usage duration for $packageName", e)
            0L
        }
    }

    /**
     * Backward-compatible alias for [getMonthlyAppUsageMinutes].
     */
    fun getAppUsageDurationLast30Days(context: Context, packageName: String): Long {
        return getMonthlyAppUsageMinutes(context, packageName)
    }

    /**
     * Queries structured [UsageResult] for an application package.
     */
    fun queryUsageResult(context: Context, packageName: String?): UsageResult {
        if (packageName.isNullOrBlank()) {
            return UsageResult.Error("Package name is empty")
        }
        if (!hasUsagePermission(context)) {
            return UsageResult.PermissionRequired
        }
        if (!isAppInstalled(context, packageName)) {
            return UsageResult.AppNotInstalled
        }
        val minutes = getMonthlyAppUsageMinutes(context, packageName)
        return if (minutes > 0L) {
            UsageResult.Available(minutes)
        } else {
            UsageResult.NoUsage
        }
    }

    /**
     * Evaluates the 3-state legacy usage status.
     */
    fun getUsageStatus(context: Context, packageName: String?): UsageStatus {
        if (packageName.isNullOrBlank() || !hasUsagePermission(context)) {
            return UsageStatus.USAGE_PERMISSION_REQUIRED
        }
        val durationMinutes = getMonthlyAppUsageMinutes(context, packageName)
        return if (durationMinutes < INACTIVE_THRESHOLD_MINUTES) {
            UsageStatus.USAGE_AVAILABLE_INACTIVE
        } else {
            UsageStatus.USAGE_AVAILABLE_ACTIVE
        }
    }
}
