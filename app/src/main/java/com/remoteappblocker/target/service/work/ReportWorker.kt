package com.remoteappblocker.target.service.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.remoteappblocker.target.data.local.AppDatabase
import com.remoteappblocker.target.data.local.DevicePreferences
import com.remoteappblocker.target.data.repository.RuleRepositoryImpl
import com.remoteappblocker.target.data.sync.RemoteSyncEngine
import java.util.concurrent.TimeUnit

class ReportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ReportWorker"
        private const val WORK_NAME = "target_guard_report_worker"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ReportWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Scheduled 15-minute periodic ReportWorker")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "ReportWorker executing background sync...")
        val db = AppDatabase.getInstance(applicationContext)
        val prefs = DevicePreferences.getInstance(applicationContext)
        val repository = RuleRepositoryImpl(db, prefs)
        val engine = RemoteSyncEngine(applicationContext, prefs, repository)

        return try {
            // 1. Sync rules
            engine.syncRulesFromSupabase()
            // 2. Upload pending block events
            engine.uploadPendingBlockEvents()
            // 3. Send heartbeat status
            engine.sendHeartbeat()

            Log.i(TAG, "ReportWorker successfully completed sync cycles")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "ReportWorker encountered retryable error", e)
            Result.retry()
        }
    }
}
