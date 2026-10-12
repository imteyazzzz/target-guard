package com.remoteappblocker.target.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.remoteappblocker.target.data.local.AppDatabase
import com.remoteappblocker.target.data.local.DevicePreferences
import com.remoteappblocker.target.data.remote.SupabaseClientProvider
import com.remoteappblocker.target.domain.model.PairingInfo
import com.remoteappblocker.target.domain.repository.PairingRepository
import com.remoteappblocker.target.util.PermissionUtils
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

@Serializable
data class DeviceInsertDto(
    val id: String,
    val name: String,
    val model: String,
    val android_version: Int,
    val battery_pct: Int,
    val accessibility_enabled: Boolean
)

@Serializable
data class PairingCodeDto(
    val code: String,
    val device_user_id: String,
    val expires_at: String,
    val used: Boolean
)

@Serializable
data class InstalledAppDto(
    val device_id: String,
    val package_name: String,
    val app_name: String
)

class PairingRepositoryImpl(
    private val context: Context,
    private val preferences: DevicePreferences,
    private val database: AppDatabase
) : PairingRepository {

    companion object {
        private const val TAG = "PairingRepository"
        private const val PAIRING_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes
    }

    override fun isDevicePaired(): Boolean = preferences.isPaired

    override suspend fun generateOrGetPairingInfo(): PairingInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var code = preferences.pairingCode
        var expiresAt = preferences.pairingExpiresAt

        if (code == null || expiresAt <= (now + 60_000L)) {
            code = (Random.nextInt(100000, 999999)).toString()
            expiresAt = now + PAIRING_EXPIRY_MS
            preferences.pairingCode = code
            preferences.pairingExpiresAt = expiresAt
        }

        val qrPayload = buildJsonObject {
            put("type", "PAIR_DEVICE")
            put("deviceId", preferences.deviceId)
            put("code", code)
            put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
        }.toString()

        PairingInfo(
            deviceId = preferences.deviceId,
            pairingCode = code,
            expiresAt = expiresAt,
            qrPayload = qrPayload
        )
    }

    override suspend fun registerDeviceInSupabase(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseClientProvider.client
            val batteryPct = getBatteryPercentage()
            val deviceDto = DeviceInsertDto(
                id = preferences.deviceId,
                name = "${Build.MANUFACTURER} ${Build.MODEL}",
                model = Build.MODEL,
                android_version = Build.VERSION.SDK_INT,
                battery_pct = batteryPct,
                accessibility_enabled = PermissionUtils.isAccessibilityEnabled(context)
            )

            // Upsert device record
            client.from("devices").upsert(deviceDto)
            Log.i(TAG, "Device registered in Supabase: ${preferences.deviceId}")

            // Register pairing code
            val code = preferences.pairingCode
            if (code != null) {
                val isoExpires = java.time.Instant.ofEpochMilli(preferences.pairingExpiresAt).toString()
                val codeDto = PairingCodeDto(
                    code = code,
                    device_user_id = preferences.deviceId,
                    expires_at = isoExpires,
                    used = false
                )
                client.from("pairing_codes").upsert(codeDto)
                Log.i(TAG, "Pairing code registered: $code")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase device registration notice (will retry / offline ready): ${e.message}")
            Result.failure(e)
        }
    }

    override fun observePairingStatus(): Flow<Boolean> = flow {
        while (!preferences.isPaired) {
            try {
                val client = SupabaseClientProvider.client
                // Poll check device pairing state
                val response = client.from("devices").select {
                    filter {
                        eq("id", preferences.deviceId)
                    }
                }
                val rawText = response.data
                if (rawText.contains("admin_id") && !rawText.contains("\"admin_id\":null")) {
                    preferences.isPaired = true
                    emit(true)
                    break
                }
            } catch (e: Exception) {
                // Ignore transient network errors
            }
            delay(3000L) // Poll interval
        }
        if (preferences.isPaired) {
            emit(true)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun confirmPaired(adminName: String): Unit = withContext(Dispatchers.IO) {
        preferences.isPaired = true
        preferences.adminName = adminName
        preferences.lastSyncAt = System.currentTimeMillis()
        Log.i(TAG, "Device confirmed paired with $adminName")
        Unit
    }

    override suspend fun uploadInstalledApps(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val apps = database.installedAppDao().getAllApps()
            if (apps.isEmpty()) return@withContext Result.success(0)

            val client = SupabaseClientProvider.client
            val dtoList = apps.map {
                InstalledAppDto(
                    device_id = preferences.deviceId,
                    package_name = it.packageName,
                    app_name = it.appName
                )
            }

            client.from("installed_apps").upsert(dtoList)
            Log.i(TAG, "Uploaded ${dtoList.size} apps to Supabase")
            Result.success(dtoList.size)
        } catch (e: Exception) {
            Log.w(TAG, "App inventory upload notice: ${e.message}")
            Result.failure(e)
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
