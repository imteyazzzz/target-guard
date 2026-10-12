package com.remoteappblocker.target.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.remoteappblocker.target.MainActivity
import com.remoteappblocker.target.RemoteBlockerApp
import com.remoteappblocker.target.data.local.AppDatabase
import com.remoteappblocker.target.data.local.DevicePreferences
import com.remoteappblocker.target.data.repository.RuleRepositoryImpl
import com.remoteappblocker.target.data.sync.RemoteSyncEngine
import com.remoteappblocker.target.service.work.ReportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncForegroundService : Service() {

    companion object {
        private const val TAG = "SyncForegroundService"
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var syncEngine: RemoteSyncEngine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        val prefs = DevicePreferences.getInstance(this)
        val ruleRepo = RuleRepositoryImpl(db, prefs)
        syncEngine = RemoteSyncEngine(this, prefs, ruleRepo)

        // 1. Initial Sync
        serviceScope.launch {
            syncEngine.syncRulesFromSupabase()
            syncEngine.sendHeartbeat()
        }

        // 2. Realtime WebSocket Subscription & Polling
        syncEngine.startRealtimeSubscription(serviceScope)

        // 3. Periodic 15-Minute Heartbeat Loop
        serviceScope.launch {
            while (isActive) {
                delay(15 * 60 * 1000L) // 15 mins
                syncEngine.sendHeartbeat()
                syncEngine.uploadPendingBlockEvents()
            }
        }

        // 4. Schedule WorkManager for background survivability
        ReportWorker.schedulePeriodic(this)

        Log.i(TAG, "SyncForegroundService created with Supabase Realtime & Heartbeat")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, RemoteBlockerApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Device managed by admin")
            .setContentText("Target Guard is active and enforcing rules")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "SyncForegroundService destroyed")
    }
}
