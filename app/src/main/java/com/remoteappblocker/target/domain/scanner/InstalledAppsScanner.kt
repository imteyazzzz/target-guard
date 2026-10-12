package com.remoteappblocker.target.domain.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.remoteappblocker.target.data.local.AppDatabase
import com.remoteappblocker.target.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsScanner(private val context: Context) {

    companion object {
        private const val TAG = "InstalledAppsScanner"
    }

    private val db = AppDatabase.getInstance(context)
    private val packageManager = context.packageManager

    suspend fun scanAndCacheApps(): List<InstalledAppEntity> = withContext(Dispatchers.IO) {
        val apps = mutableListOf<InstalledAppEntity>()
        val selfPackage = context.packageName

        try {
            // Retrieve all installed applications
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                packageManager.getInstalledApplications(0)
            }

            // Also check launchable intent apps to ensure high-priority user-facing apps are captured
            val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchablePackageNames = packageManager.queryIntentActivities(launchIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()

            for (appInfo in installedPackages) {
                val pkgName = appInfo.packageName
                if (pkgName == selfPackage) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isLaunchable = launchablePackageNames.contains(pkgName)

                // We prioritize launchable apps or non-system apps (user installed apps)
                if (isLaunchable || !isSystem) {
                    val appName = try {
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkgName
                    }

                    apps.add(
                        InstalledAppEntity(
                            packageName = pkgName,
                            appName = appName,
                            isSystemApp = isSystem,
                            isBlocked = false
                        )
                    )
                }
            }

            // Save to local database
            db.installedAppDao().clearAll()
            db.installedAppDao().insertApps(apps)
            Log.i(TAG, "Scanned and cached ${apps.size} user-facing installed apps")

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning installed apps", e)
        }

        apps.sortedBy { it.appName.lowercase() }
    }
}
