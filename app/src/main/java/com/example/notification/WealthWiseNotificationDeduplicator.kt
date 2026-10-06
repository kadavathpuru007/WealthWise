package com.example.notification

import android.content.Context
import android.content.SharedPreferences

class WealthWiseNotificationDeduplicator(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wealthwise_notification_dedup", Context.MODE_PRIVATE)

    /**
     * Checks if this notification event has already been sent.
     */
    fun hasBeenSent(key: String): Boolean {
        return prefs.getBoolean(key, false)
    }

    /**
     * Records that a notification for this key has been delivered.
     */
    fun recordSent(key: String) {
        prefs.edit().putBoolean(key, true).putLong("${key}_timestamp", System.currentTimeMillis()).apply()
    }

    /**
     * Clears or resets a specific key (e.g. if sync attention is resolved).
     */
    fun clearKey(key: String) {
        prefs.edit().remove(key).remove("${key}_timestamp").apply()
    }

    /**
     * Resets deduplication records for budget testing or resets.
     */
    fun resetAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        fun budgetAlmostExhaustedKey(category: String, period: String): String =
            "BUDGET_ALMOST_EXHAUSTED_${category.trim().uppercase()}_$period"

        fun budgetExceededKey(category: String, period: String): String =
            "BUDGET_EXCEEDED_${category.trim().uppercase()}_$period"

        fun upcomingEmiKey(loanId: Int, dueDate: String): String =
            "UPCOMING_EMI_${loanId}_$dueDate"

        fun upcomingBillKey(billId: Int, dueDate: String): String =
            "UPCOMING_BILL_${billId}_$dueDate"

        fun goalBehindKey(goalId: Long, period: String): String =
            "GOAL_BEHIND_${goalId}_$period"

        fun goalBehindKey(goalId: Int, period: String): String = goalBehindKey(goalId.toLong(), period)

        fun goalAffordabilityKey(goalId: Long, period: String): String =
            "GOAL_AFFORDABILITY_${goalId}_$period"

        fun goalAffordabilityKey(goalId: Int, period: String): String = goalAffordabilityKey(goalId.toLong(), period)

        fun syncAttentionKey(): String =
            "SYNC_ATTENTION_DISABLED"
    }
}
