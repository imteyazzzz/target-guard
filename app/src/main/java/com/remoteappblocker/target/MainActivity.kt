package com.remoteappblocker.target

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteappblocker.target.data.local.AppDatabase
import com.remoteappblocker.target.data.local.DevicePreferences
import com.remoteappblocker.target.data.repository.PairingRepositoryImpl
import com.remoteappblocker.target.domain.model.PairingInfo
import com.remoteappblocker.target.domain.scanner.InstalledAppsScanner
import com.remoteappblocker.target.presentation.block.BlockActivity
import com.remoteappblocker.target.presentation.pairing.PairingScreen
import com.remoteappblocker.target.presentation.theme.DarkCard
import com.remoteappblocker.target.presentation.theme.DarkSurface
import com.remoteappblocker.target.presentation.theme.PrimaryBlue
import com.remoteappblocker.target.presentation.theme.RemoteAppBlockerTheme
import com.remoteappblocker.target.presentation.theme.Slate400
import com.remoteappblocker.target.presentation.theme.SuccessGreen
import com.remoteappblocker.target.presentation.wizard.SetupWizardScreen
import com.remoteappblocker.target.presentation.wizard.WizardPermissionState
import com.remoteappblocker.target.service.SyncForegroundService
import com.remoteappblocker.target.util.PermissionUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class ScreenState {
    PAIRING,
    WIZARD,
    DASHBOARD
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: DevicePreferences
    private lateinit var pairingRepo: PairingRepositoryImpl
    private val permissionState = mutableStateOf(WizardPermissionState())
    private val currentScreen = mutableStateOf(ScreenState.PAIRING)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionState.value = getPermissionSnapshot()
        val db = AppDatabase.getInstance(this)
        prefs = DevicePreferences.getInstance(this)
        pairingRepo = PairingRepositoryImpl(this, prefs, db)

        // Decide starting screen
        currentScreen.value = when {
            !prefs.isPaired -> ScreenState.PAIRING
            !permissionState.value.isEssentialGranted -> ScreenState.WIZARD
            else -> ScreenState.DASHBOARD
        }

        setContent {
            RemoteAppBlockerTheme {
                val currentPerms = permissionState.value
                val screen = currentScreen.value
                val scope = rememberCoroutineScope()
                var pairingInfo by remember {
                    mutableStateOf(
                        PairingInfo(
                            deviceId = prefs.deviceId,
                            pairingCode = prefs.pairingCode ?: "123456",
                            expiresAt = System.currentTimeMillis() + 600000L,
                            qrPayload = ""
                        )
                    )
                }
                var isDevicePaired by remember { mutableStateOf(prefs.isPaired) }

                // Load and register pairing info & scan installed apps
                LaunchedEffect(Unit) {
                    val info = pairingRepo.generateOrGetPairingInfo()
                    pairingInfo = info
                    pairingRepo.registerDeviceInSupabase()
                    try {
                        InstalledAppsScanner(this@MainActivity).scanAndCacheApps()
                        pairingRepo.uploadInstalledApps()
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Auto app sync notice: ${e.message}")
                    }
                }

                // Listen for pairing status in real-time
                LaunchedEffect(Unit) {
                    pairingRepo.observePairingStatus().collectLatest { paired ->
                        if (paired) {
                            isDevicePaired = true
                            prefs.isPaired = true
                        }
                    }
                }

                var showUnpairPinDialog by remember { mutableStateOf(false) }

                if (showUnpairPinDialog) {
                    com.remoteappblocker.target.presentation.pin.AdminPinDialog(
                        deviceId = prefs.deviceId,
                        masterPin = prefs.adminPin ?: "1234",
                        title = "Unpair Device",
                        subtitle = "Enter 1-minute Admin PIN shown on Parent Web Portal",
                        onDismissRequest = { showUnpairPinDialog = false },
                        onPinVerified = {
                            showUnpairPinDialog = false
                            scope.launch {
                                prefs.isPaired = false
                                prefs.pairingCode = null
                                db.ruleDao().clearAllRules()
                                SyncForegroundService.stopService(this@MainActivity)
                                isDevicePaired = false
                                currentScreen.value = ScreenState.PAIRING
                            }
                        }
                    )
                }

                val installedApps by db.installedAppDao().observeAllApps().collectAsState(initial = emptyList())
                val rules by db.ruleDao().observeAllRules().collectAsState(initial = emptyList())

                when (screen) {
                    ScreenState.PAIRING -> {
                        PairingScreen(
                            pairingInfo = pairingInfo,
                            isPaired = isDevicePaired,
                            onRefreshCode = {
                                scope.launch {
                                    val info = pairingRepo.generateOrGetPairingInfo()
                                    pairingInfo = info
                                    pairingRepo.registerDeviceInSupabase()
                                }
                            },
                            onSimulatePairSuccess = {
                                scope.launch {
                                    pairingRepo.confirmPaired("Admin (Parent)")
                                    isDevicePaired = true
                                    currentScreen.value = ScreenState.WIZARD
                                }
                            },
                            onContinueToApp = {
                                currentScreen.value = if (currentPerms.isEssentialGranted) {
                                    ScreenState.DASHBOARD
                                } else {
                                    ScreenState.WIZARD
                                }
                            }
                        )
                    }

                    ScreenState.WIZARD -> {
                        SetupWizardScreen(
                            state = currentPerms,
                            onGrantAccessibility = { startActivity(PermissionUtils.getAccessibilityIntent(this)) },
                            onGrantOverlay = { startActivity(PermissionUtils.getOverlayIntent(this)) },
                            onGrantUsageStats = { startActivity(PermissionUtils.getUsageStatsIntent()) },
                            onGrantNotification = { startActivity(PermissionUtils.getNotificationIntent(this)) },
                            onIgnoreBattery = { startActivity(PermissionUtils.getBatteryOptimizationIntent(this)) },
                            onActivateAdmin = { startActivity(PermissionUtils.getDeviceAdminIntent(this)) },
                            onOpenOemAutostart = { com.remoteappblocker.target.util.OemAutostartHelper.openAutostartSettings(this) },
                            onFinishSetup = {
                                scope.launch {
                                    // Scan installed apps and start sync service upon finishing wizard
                                    InstalledAppsScanner(this@MainActivity).scanAndCacheApps()
                                    pairingRepo.uploadInstalledApps()
                                    SyncForegroundService.startService(this@MainActivity)
                                    currentScreen.value = ScreenState.DASHBOARD
                                }
                            }
                        )
                    }

                    ScreenState.DASHBOARD -> {
                        MainDashboardScreen(
                            adminName = prefs.adminName,
                            permissionState = currentPerms,
                            installedAppsCount = installedApps.size,
                            activeRulesCount = rules.filter { it.enabled }.size,
                            onOpenPairing = { currentScreen.value = ScreenState.PAIRING },
                            onOpenWizard = { currentScreen.value = ScreenState.WIZARD },
                            onRescanApps = {
                                scope.launch {
                                    InstalledAppsScanner(this@MainActivity).scanAndCacheApps()
                                    pairingRepo.uploadInstalledApps()
                                }
                            },
                            onStartSync = {
                                SyncForegroundService.startService(this)
                            },
                            onTestBlockClicked = {
                                startActivity(
                                    BlockActivity.createIntent(
                                        context = this,
                                        appName = "Instagram (Test)",
                                        packageName = "com.instagram.android",
                                        message = "Test Rule: Blocked during study hours by Admin."
                                    )
                                )
                            },
                            onUnpairClicked = {
                                showUnpairPinDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionState.value = getPermissionSnapshot()
    }

    private fun getPermissionSnapshot(): WizardPermissionState {
        return WizardPermissionState(
            isAccessibilityGranted = PermissionUtils.isAccessibilityEnabled(this),
            isOverlayGranted = PermissionUtils.isOverlayGranted(this),
            isUsageStatsGranted = PermissionUtils.isUsageStatsGranted(this),
            isNotificationGranted = PermissionUtils.isNotificationGranted(this),
            isBatteryOptimizationIgnored = PermissionUtils.isBatteryOptimizationIgnored(this),
            isDeviceAdminActive = PermissionUtils.isDeviceAdminActive(this)
        )
    }
}

@Composable
fun MainDashboardScreen(
    adminName: String,
    permissionState: WizardPermissionState,
    installedAppsCount: Int,
    activeRulesCount: Int,
    onOpenPairing: () -> Unit,
    onOpenWizard: () -> Unit,
    onRescanApps: () -> Unit,
    onStartSync: () -> Unit,
    onTestBlockClicked: () -> Unit,
    onUnpairClicked: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // App Shield Logo
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(PrimaryBlue.copy(alpha = 0.2f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Target Guard",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Managed by $adminName",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Stats Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Rules", color = Slate400, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$activeRulesCount",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Apps Scanned", color = Slate400, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (installedAppsCount > 0) "$installedAppsCount" else "Pending",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (installedAppsCount > 0) SuccessGreen else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // System Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "System Status",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    StatusItem(
                        title = "Accessibility Detection",
                        subtitle = if (permissionState.isAccessibilityGranted) "Active & Monitoring" else "Disabled in settings",
                        isSuccess = permissionState.isAccessibilityGranted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    StatusItem(
                        title = "Overlay Protection",
                        subtitle = if (permissionState.isOverlayGranted) "Active" else "Disabled",
                        isSuccess = permissionState.isOverlayGranted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    StatusItem(
                        title = "Permissions",
                        subtitle = "${permissionState.grantedCount} of ${permissionState.totalCount} granted",
                        isSuccess = permissionState.grantedCount >= 4
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Actions
            Button(
                onClick = onStartSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Sync Service", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onRescanApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Apps, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Installed Apps", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOpenWizard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Permission Wizard", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOpenPairing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Device Pairing / QR", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onTestBlockClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Test Block Screen UI", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onUnpairClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = com.remoteappblocker.target.presentation.theme.AlertRed
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = com.remoteappblocker.target.presentation.theme.AlertRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unpair Device (Admin PIN)",
                    style = MaterialTheme.typography.titleLarge,
                    color = com.remoteappblocker.target.presentation.theme.AlertRed
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatusItem(title: String, subtitle: String, isSuccess: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isSuccess) SuccessGreen else Color(0xFFF59E0B),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400
            )
        }
    }
}
