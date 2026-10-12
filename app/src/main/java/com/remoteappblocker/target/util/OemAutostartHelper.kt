package com.remoteappblocker.target.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object OemAutostartHelper {

    private const val TAG = "OemAutostartHelper"

    fun getDeviceManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }

    fun isOemSpecificDevice(): Boolean {
        val oem = getDeviceManufacturer()
        return oem.contains("xiaomi") || oem.contains("redmi") ||
                oem.contains("oppo") || oem.contains("realme") ||
                oem.contains("vivo") || oem.contains("samsung") ||
                oem.contains("huawei") || oem.contains("honor") ||
                oem.contains("oneplus")
    }

    /**
     * Attempts to open OEM-specific Autostart or Background Management screen.
     * Falls back to Android Application Details Settings.
     */
    fun openAutostartSettings(context: Context): Boolean {
        val oem = getDeviceManufacturer()
        val intents = mutableListOf<Intent>()

        when {
            oem.contains("xiaomi") || oem.contains("redmi") || oem.contains("poco") -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
            }

            oem.contains("oppo") || oem.contains("realme") || oem.contains("oneplus") -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.oppo.safe",
                            "com.oppo.safe.permission.startup.StartupAppListActivity"
                        )
                    )
                )
            }

            oem.contains("vivo") || oem.contains("iqoo") -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    )
                )
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                        )
                    )
                )
            }

            oem.contains("samsung") -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    )
                )
            }

            oem.contains("huawei") || oem.contains("honor") -> {
                intents.add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    )
                )
            }
        }

        // Try OEM specific intents first
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.i(TAG, "Launched OEM autostart activity: ${intent.component}")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed launching OEM autostart intent", e)
            }
        }

        // Standard Fallback: App Details Settings
        return try {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening application details settings fallback", e)
            false
        }
    }
}
