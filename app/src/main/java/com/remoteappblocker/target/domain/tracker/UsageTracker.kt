package com.remoteappblocker.target.domain.tracker

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.remoteappblocker.target.util.PermissionUtils
import java.util.Calendar

class UsageTracker(private val context: Context) {

    companion object {
        private const val TAG = "UsageTracker"
    }

    private val usageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    } else {
        null
    }

    /**
     * Calculates the total minutes a package was used today (from 00:00 AM to now).
     */
    fun getDailyUsageMinutes(packageName: String): Int {
        if (!PermissionUtils.isUsageStatsGranted(context) || usageStatsManager == null) {
            return 0
        }

        try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            val now = System.currentTimeMillis()

            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            ) ?: return 0

            val matchingStat = statsList.firstOrNull { it.packageName == packageName }
            if (matchingStat != null) {
                val foregroundTimeMs = matchingStat.totalTimeInForeground
                val minutes = (foregroundTimeMs / (1000 * 60)).toInt()
                Log.d(TAG, "Usage for $packageName today: $minutes minutes ($foregroundTimeMs ms)")
                return minutes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed calculating daily usage for $packageName", e)
        }
        return 0
    }
}
