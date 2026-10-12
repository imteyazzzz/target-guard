package com.remoteappblocker.target.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.remoteappblocker.target.data.local.AppDatabase
import com.remoteappblocker.target.data.local.DevicePreferences
import com.remoteappblocker.target.data.repository.RuleRepositoryImpl
import com.remoteappblocker.target.domain.engine.RuleEngine
import com.remoteappblocker.target.domain.model.Rule
import com.remoteappblocker.target.domain.repository.RuleRepository
import com.remoteappblocker.target.domain.tracker.UsageTracker
import com.remoteappblocker.target.presentation.block.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class BlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BlockerAccessibility"
        var isServiceRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ruleEngine = RuleEngine()
    private lateinit var ruleRepository: RuleRepository
    private lateinit var usageTracker: UsageTracker

    // In-memory cache for ultra-fast <1ms rule lookups
    private val activeRulesMap = ConcurrentHashMap<String, Rule>()

    // Debounce to prevent intent spamming
    private var lastBlockedPackage: String? = null
    private var lastBlockedTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        val prefs = DevicePreferences.getInstance(this)
        ruleRepository = RuleRepositoryImpl(db, prefs)
        usageTracker = com.remoteappblocker.target.domain.tracker.UsageTracker(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "BlockerAccessibilityService connected and ready")

        // Keep in-memory cache synchronized with Room DB
        serviceScope.launch {
            ruleRepository.observeRules().collectLatest { rules ->
                activeRulesMap.clear()
                rules.filter { it.enabled }.forEach { rule ->
                    activeRulesMap[rule.packageName] = rule
                }
                Log.d(TAG, "Active cached rules count: ${activeRulesMap.size}")
            }
        }
    }

    private var lastSelfProtectionTimestamp: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // 1. SELF-PROTECTION GUARDIAN: Detect any attempt to uninstall, force stop, or disable Target Guard
        if (isSelfProtectionTriggered(event, packageName)) {
            handleSelfProtectionBlock()
            return
        }

        // Only process rule blocks on window state changes
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        // 2. Skip Target Guard itself
        if (packageName == applicationContext.packageName) return

        // 3. Skip System UI and Launcher
        if (packageName == "com.android.systemui" || isLauncherPackage(packageName)) return

        // 4. Ultra-fast cache lookup (< 1 ms)
        val rule = activeRulesMap[packageName] ?: return

        // 5. Rule evaluation (Pure Kotlin)
        val usedMinutes = if (rule.mode == "limit") usageTracker.getDailyUsageMinutes(packageName) else 0
        val evaluation = ruleEngine.evaluateNow(rule, dailyUsageMinutes = usedMinutes)
        if (!evaluation.isBlocked) return

        // 6. Debounce check (1.5 seconds)
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (now - lastBlockedTimestamp) < 1500L) {
            return
        }
        lastBlockedPackage = packageName
        lastBlockedTimestamp = now

        Log.w(TAG, "Blocking target package: $packageName. Reason: ${evaluation.reason}")

        // 7. Launch BlockActivity full screen immediately (< 300 ms target)
        val appName = getAppLabel(packageName)
        val blockIntent = BlockActivity.createIntent(
            context = this,
            appName = appName,
            packageName = packageName,
            message = evaluation.message
        )
        startActivity(blockIntent)

        // 8. Async telemetry log to Room
        serviceScope.launch(Dispatchers.IO) {
            ruleRepository.logBlockEvent(packageName)
        }
    }

    private fun handleSelfProtectionBlock() {
        val now = System.currentTimeMillis()
        if ((now - lastSelfProtectionTimestamp) < 1800L) {
            return
        }
        lastSelfProtectionTimestamp = now

        Log.w(TAG, "🛡️ Tamper attempt intercepted! Blocking Target Guard uninstall/settings access.")

        // 1. Kick user back to home screen immediately
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform global action home: ${e.message}")
        }

        // 2. Launch BlockActivity explaining why it's protected
        val blockIntent = BlockActivity.createIntent(
            context = this,
            appName = "Target Guard Protection Shield",
            packageName = applicationContext.packageName,
            message = "Target Guard is protected by parental controls. Deactivating accessibility, clearing data, or uninstalling requires the 1-minute Admin PIN from the Parent Web Portal."
        )
        startActivity(blockIntent)

        // 3. Log security alert telemetry to Room & Supabase
        serviceScope.launch(Dispatchers.IO) {
            ruleRepository.logBlockEvent("com.remoteappblocker.target.tamper_shield")
        }
    }

    private fun isSelfProtectionTriggered(event: AccessibilityEvent, packageName: String): Boolean {
        val myPkg = applicationContext.packageName
        val myLabel = "Target Guard"

        // A. Package Installer (Uninstall Confirmation Dialog for Target Guard)
        if (packageName.contains("packageinstaller", ignoreCase = true)) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                if (rootNode.findAccessibilityNodeInfosByText(myLabel).isNotEmpty()) return true
                if (rootNode.findAccessibilityNodeInfosByText(myPkg).isNotEmpty()) return true
            }
            for (t in event.text) {
                val str = t.toString()
                if (str.contains(myLabel, ignoreCase = true) || str.contains(myPkg, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        // B. Settings, Permission Controllers, and Security Centers
        val isSettings = packageName.contains("settings", ignoreCase = true) ||
                packageName.contains("permissioncontroller", ignoreCase = true) ||
                packageName.contains("securitycenter", ignoreCase = true) ||
                packageName.contains("secure", ignoreCase = true)

        if (!isSettings) return false

        val rootNode = rootInActiveWindow ?: return false

        // Check if "Target Guard" or package name is actively targeted on this screen
        val hasTargetGuard = rootNode.findAccessibilityNodeInfosByText(myLabel).isNotEmpty() ||
                rootNode.findAccessibilityNodeInfosByText(myPkg).isNotEmpty()

        if (!hasTargetGuard) return false

        // Critical danger action triggers (Actions that attempt to disable, uninstall, force-stop, clear data, or remove admin)
        val dangerKeywords = listOf(
            "uninstall", "force stop", "deactivate", "turn off",
            "clear storage", "clear data", "disable", "remove admin",
            "device admin", "stop service"
        )

        for (keyword in dangerKeywords) {
            if (rootNode.findAccessibilityNodeInfosByText(keyword).isNotEmpty()) {
                return true
            }
        }

        return false
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        Log.i(TAG, "BlockerAccessibilityService destroyed")
    }
}
