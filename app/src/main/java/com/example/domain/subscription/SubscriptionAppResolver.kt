package com.example.domain.subscription

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.local.SubscriptionEntity
import com.example.permission.AppLaunchFallbackState
import com.example.permission.KnownSubscriptionApp

/**
 * Resolves subscription app packages and provides safe, permission-less launching
 * with graceful web and Google Play Store fallbacks.
 */
object SubscriptionAppResolver {

    private const val TAG = "SubAppResolver"

    val KNOWN_CATALOGUE = listOf(
        KnownSubscriptionApp("Netflix", "com.netflix.mediaclient", "https://www.netflix.com/youraccount", "https://play.google.com/store/apps/details?id=com.netflix.mediaclient"),
        KnownSubscriptionApp("Spotify", "com.spotify.music", "https://www.spotify.com/account/overview/", "https://play.google.com/store/apps/details?id=com.spotify.music"),
        KnownSubscriptionApp("Amazon Prime", "com.amazon.avod.thirdpartyclient", "https://www.amazon.in/gp/primecentral", "https://play.google.com/store/apps/details?id=com.amazon.avod.thirdpartyclient"),
        KnownSubscriptionApp("YouTube Premium", "com.google.android.youtube", "https://www.youtube.com/paid_memberships", "https://play.google.com/store/apps/details?id=com.google.android.youtube"),
        KnownSubscriptionApp("Disney+ Hotstar", "in.startv.hotstar", "https://www.hotstar.com/in/my-account", "https://play.google.com/store/apps/details?id=in.startv.hotstar"),
        KnownSubscriptionApp("Cult.fit", "in.cure.fit", "https://www.cult.fit", "https://play.google.com/store/apps/details?id=in.cure.fit"),
        KnownSubscriptionApp("ChatGPT Plus", "com.openai.chatgpt", "https://chatgpt.com", "https://play.google.com/store/apps/details?id=com.openai.chatgpt"),
        KnownSubscriptionApp("Google One", "com.google.android.apps.subscriptions.red", "https://one.google.com", "https://play.google.com/store/apps/details?id=com.google.android.apps.subscriptions.red"),
        KnownSubscriptionApp("Microsoft 365", "com.microsoft.office.officehubrow", "https://account.microsoft.com/services", "https://play.google.com/store/apps/details?id=com.microsoft.office.officehubrow"),
        KnownSubscriptionApp("SonyLIV", "com.jio.media.ondemand", "https://www.sonyliv.com", "https://play.google.com/store/apps/details?id=com.jio.media.ondemand"),
        KnownSubscriptionApp("Zee5", "com.graymatrix.did", "https://www.zee5.com", "https://play.google.com/store/apps/details?id=com.graymatrix.did"),
        KnownSubscriptionApp("Audible", "com.audible.application", "https://www.audible.in/account/overview", "https://play.google.com/store/apps/details?id=com.audible.application"),
        KnownSubscriptionApp("Times Prime", "com.timesprime.tp", "https://www.timesprime.com", "https://play.google.com/store/apps/details?id=com.timesprime.tp"),
        KnownSubscriptionApp("Apple Music", "com.apple.android.music", "https://appleid.apple.com", "https://play.google.com/store/apps/details?id=com.apple.android.music")
    )

    fun resolveApp(subscriptionName: String, customPackage: String? = null): KnownSubscriptionApp? {
        if (!customPackage.isNullOrBlank()) {
            return KnownSubscriptionApp(
                appName = subscriptionName,
                packageName = customPackage,
                websiteUrl = "https://www.google.com/search?q=" + Uri.encode("$subscriptionName subscription manage"),
                playStoreUrl = "https://play.google.com/store/apps/details?id=$customPackage"
            )
        }
        val trimmed = subscriptionName.trim().lowercase()
        return KNOWN_CATALOGUE.firstOrNull { app ->
            val appLower = app.appName.lowercase()
            trimmed.contains(appLower) || appLower.contains(trimmed)
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
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
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package: $packageName", e)
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
            openWeb(context, "https://play.google.com/store/apps/details?id=$packageName")
        } catch (e: Exception) {
            false
        }
    }

    fun openWeb(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open web url: $url", e)
            false
        }
    }

    fun createFallbackState(subscription: SubscriptionEntity): AppLaunchFallbackState {
        val known = resolveApp(subscription.name, subscription.linkedPackageName.ifBlank { null })
        val packageName = known?.packageName ?: subscription.linkedPackageName.ifBlank {
            "com.${subscription.name.lowercase().replace(" ", "")}"
        }
        val webUrl = known?.websiteUrl ?: "https://www.google.com/search?q=" + Uri.encode("${subscription.name} manage subscription")
        val playStoreUrl = known?.playStoreUrl ?: "https://play.google.com/store/apps/details?id=$packageName"

        return AppLaunchFallbackState(
            appName = subscription.name,
            packageName = packageName,
            webUrl = webUrl,
            playStoreUrl = playStoreUrl
        )
    }
}
