package com.remoteappblocker.target.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class DevicePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "target_guard_device_prefs"
        private const val KEY_DEVICE_ID = "key_device_id"
        private const val KEY_IS_PAIRED = "key_is_paired"
        private const val KEY_ADMIN_NAME = "key_admin_name"
        private const val KEY_ADMIN_PIN = "key_admin_pin"
        private const val KEY_LAST_SYNC_AT = "key_last_sync_at"
        private const val KEY_PAIRING_CODE = "key_pairing_code"
        private const val KEY_PAIRING_EXPIRES_AT = "key_pairing_expires_at"

        @Volatile
        private var INSTANCE: DevicePreferences? = null

        fun getInstance(context: Context): DevicePreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = DevicePreferences(context)
                INSTANCE = instance
                instance
            }
        }
    }

    var deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var isPaired: Boolean
        get() = prefs.getBoolean(KEY_IS_PAIRED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PAIRED, value).apply()

    var adminName: String
        get() = prefs.getString(KEY_ADMIN_NAME, "Administrator") ?: "Administrator"
        set(value) = prefs.edit().putString(KEY_ADMIN_NAME, value).apply()

    var adminPin: String?
        get() = prefs.getString(KEY_ADMIN_PIN, null)
        set(value) = prefs.edit().putString(KEY_ADMIN_PIN, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    var pairingCode: String?
        get() = prefs.getString(KEY_PAIRING_CODE, null)
        set(value) = prefs.edit().putString(KEY_PAIRING_CODE, value).apply()

    var pairingExpiresAt: Long
        get() = prefs.getLong(KEY_PAIRING_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_PAIRING_EXPIRES_AT, value).apply()
}
