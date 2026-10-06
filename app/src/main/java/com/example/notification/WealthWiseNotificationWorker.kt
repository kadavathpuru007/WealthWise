package com.example.notification

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.local.WealthWiseDatabase
import com.example.data.repository.WealthWiseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WealthWiseNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing WealthWise periodic financial notification check...")
            val database = WealthWiseDatabase.getDatabase(applicationContext)
            val repository = WealthWiseRepository(database, applicationContext)

            val alerts = WealthWiseNotificationEvaluator.evaluateFinancialAlerts(
                applicationContext,
                database,
                repository
            )
            Log.d(TAG, "WealthWise notification worker finished. Fired ${alerts.size} alerts.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in WealthWiseNotificationWorker", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WealthWiseWorker"
        private const val WORK_NAME_PERIODIC = "wealthwise_periodic_notification_check"
        private const val WORK_NAME_IMMEDIATE = "wealthwise_immediate_notification_check"

        /**
         * Schedules battery-friendly periodic background checking every 6 hours.
         */
        fun schedulePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<WealthWiseNotificationWorker>(
                6, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.d(TAG, "Scheduled periodic WorkManager task for WealthWise notifications (every 6 hours).")
        }

        /**
         * Dispatches a background one-time evaluation.
         */
        fun runImmediateCheck(context: Context) {
            val oneTimeRequest = OneTimeWorkRequestBuilder<WealthWiseNotificationWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
