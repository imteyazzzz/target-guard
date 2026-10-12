package com.remoteappblocker.target.data.sync

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.remoteappblocker.target.data.local.AppDatabase
import com.remoteappblocker.target.data.local.DevicePreferences
import com.remoteappblocker.target.data.remote.SupabaseClientProvider
import com.remoteappblocker.target.data.remote.dto.RemoteBlockEventDto
import com.remoteappblocker.target.data.remote.dto.RemoteRuleDto
import com.remoteappblocker.target.data.repository.RuleRepositoryImpl
import com.remoteappblocker.target.domain.repository.RuleRepository
import com.remoteappblocker.target.util.PermissionUtils
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class RemoteSyncEngine(
    private val context: Context,
    private val preferences: DevicePreferences,
    private val ruleRepository: RuleRepository
) {

    companion object {
        private const val TAG = "RemoteSyncEngine"
    }

    private val client = SupabaseClientProvider.client

    /**
     * Fetches rules from Supabase and caches them in Room DB
     */
    suspend fun syncRulesFromSupabase(): Result<Int> = withContext(Dispatchers.IO) {
        val deviceId = preferences.deviceId
        try {
            Log.d(TAG, "Fetching rules for device $deviceId from Supabase...")
            val response = client.from("rules").select {
                filter {
                    eq("device_id", deviceId)
                }
            }

            val remoteRules = response.decodeList<RemoteRuleDto>()
            val domainRules = remoteRules.map { it.toDomain() }

            ruleRepository.saveRules(domainRules)
            preferences.lastSyncAt = System.currentTimeMillis()

            // Find highest version to send ACK
            val maxVersion = domainRules.maxOfOrNull { it.version } ?: 0L
            sendAck(deviceId, maxVersion)

            Log.i(TAG, "Successfully synced ${domainRules.size} rules (Latest version: $maxVersion)")
            Result.success(domainRules.size)
        } catch (e: Exception) {
            Log.w(TAG, "Rules sync offline or error (using local Room cache): ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Reports device status, battery and accessibility state (Heartbeat every 15 min)
     */
    suspend fun sendHeartbeat(): Result<Unit> = withContext(Dispatchers.IO) {
        val deviceId = preferences.deviceId
        try {
            val batteryPct = getBatteryPercentage()
            val isAccessibilityOn = PermissionUtils.isAccessibilityEnabled(context)
            val nowIso = Instant.now().toString()

            val updatePayload = buildJsonObject {
                put("last_seen_at", nowIso)
                put("battery_pct", batteryPct)
                put("accessibility_enabled", isAccessibilityOn)
            }

            client.from("devices").update(updatePayload) {
                filter {
                    eq("id", deviceId)
                }
            }

            Log.d(TAG, "Heartbeat sent: battery=$batteryPct%, accessibility=$isAccessibilityOn")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Heartbeat report failed (transient): ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Uploads pending block events from Room DB to Supabase
     */
    suspend fun uploadPendingBlockEvents(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val unsyncedEvents = ruleRepository.getUnsyncedEvents(50)
            if (unsyncedEvents.isEmpty()) return@withContext Result.success(0)

            val dtoList = unsyncedEvents.map {
                RemoteBlockEventDto(
                    device_id = preferences.deviceId,
                    package_name = it.packageName,
                    occurred_at = Instant.ofEpochMilli(it.occurredAt).toString()
                )
            }

            client.from("block_events").insert(dtoList)

            // Mark synced locally
            val eventIds = unsyncedEvents.map { it.id }
            ruleRepository.markEventsSynced(eventIds)

            Log.i(TAG, "Uploaded ${unsyncedEvents.size} block event logs to Supabase")
            Result.success(unsyncedEvents.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed uploading block events to Supabase: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Listens to real-time changes via Supabase Realtime channel
     */
    fun startRealtimeSubscription(scope: CoroutineScope) {
        val deviceId = preferences.deviceId

        // 1. WebSocket Realtime Channel
        scope.launch(Dispatchers.IO) {
            try {
                val channel = client.channel("rules_device_$deviceId")
                val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "rules"
                }

                channel.subscribe()
                Log.i(TAG, "Subscribed to Supabase Realtime for rules table")

                changes.collectLatest { action ->
                    Log.i(TAG, "Realtime rule update received: $action. Triggering sync...")
                    syncRulesFromSupabase()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Realtime channel connection notice: ${e.message}")
            }
        }

        // 2. Periodic background fallback sync (every 60 seconds)
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000L)
                syncRulesFromSupabase()
            }
        }
    }

    private suspend fun sendAck(deviceId: String, version: Long) {
        try {
            val payload = buildJsonObject {
                put("last_seen_at", Instant.now().toString())
            }
            client.from("devices").update(payload) {
                filter {
                    eq("id", deviceId)
                }
            }
        } catch (e: Exception) {
            // Non-critical ACK logging
        }
    }

    private fun getBatteryPercentage(): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            100
        }
    }
}
