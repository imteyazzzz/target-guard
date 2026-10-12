package com.remoteappblocker.target.presentation.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteappblocker.target.presentation.theme.DarkCard
import com.remoteappblocker.target.presentation.theme.DarkSurface
import com.remoteappblocker.target.presentation.theme.PrimaryBlue
import com.remoteappblocker.target.presentation.theme.Slate400
import com.remoteappblocker.target.presentation.theme.Slate700
import com.remoteappblocker.target.presentation.theme.SuccessGreen
import com.remoteappblocker.target.presentation.theme.WarningAmber

data class WizardPermissionState(
    val isAccessibilityGranted: Boolean = false,
    val isOverlayGranted: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isDeviceAdminActive: Boolean = false
) {
    val totalCount: Int = 6
    val grantedCount: Int
        get() = listOf(
            isAccessibilityGranted,
            isOverlayGranted,
            isUsageStatsGranted,
            isNotificationGranted,
            isBatteryOptimizationIgnored,
            isDeviceAdminActive
        ).count { it }

    val isEssentialGranted: Boolean
        get() = isAccessibilityGranted && isOverlayGranted
}

@Composable
fun SetupWizardScreen(
    state: WizardPermissionState,
    onGrantAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantUsageStats: () -> Unit,
    onGrantNotification: () -> Unit,
    onIgnoreBattery: () -> Unit,
    onActivateAdmin: () -> Unit,
    onOpenOemAutostart: (() -> Unit)? = null,
    onFinishSetup: () -> Unit
) {
    val progress = state.grantedCount.toFloat() / state.totalCount.toFloat()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Icon & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(PrimaryBlue.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Permission Setup",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Step-by-step security configuration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Setup Progress",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${state.grantedCount} of ${state.totalCount} active",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.grantedCount == state.totalCount) SuccessGreen else WarningAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (state.grantedCount == state.totalCount) SuccessGreen else PrimaryBlue,
                        trackColor = Slate700
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 1: Accessibility Service (P0)
            WizardPermissionItem(
                stepNumber = "1",
                title = "Accessibility Service",
                description = "Required to detect and block configured apps in real time.",
                isGranted = state.isAccessibilityGranted,
                buttonText = "Enable Accessibility",
                onAction = onGrantAccessibility
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Step 2: Overlay / Alert Window (P0)
            WizardPermissionItem(
                stepNumber = "2",
                title = "Display Over Other Apps",
                description = "Required to immediately show the blocking screen over apps.",
                isGranted = state.isOverlayGranted,
                buttonText = "Grant Overlay",
                onAction = onGrantOverlay
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Step 3: Usage Access (P1)
            WizardPermissionItem(
                stepNumber = "3",
                title = "Usage Access",
                description = "Required to calculate daily app screen time limits.",
                isGranted = state.isUsageStatsGranted,
                buttonText = "Grant Usage Access",
                onAction = onGrantUsageStats
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Step 4: Notifications (P0)
            WizardPermissionItem(
                stepNumber = "4",
                title = "System Notifications",
                description = "Shows the ongoing 'Device managed' status notification.",
                isGranted = state.isNotificationGranted,
                buttonText = "Allow Notifications",
                onAction = onGrantNotification
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Step 5: Battery Optimization (P0)
            WizardPermissionItem(
                stepNumber = "5",
                title = "Unrestricted Battery",
                description = "Prevents OEM battery killers from stopping protection.",
                isGranted = state.isBatteryOptimizationIgnored,
                buttonText = "Disable Restrictions",
                onAction = onIgnoreBattery
            )

            if (onOpenOemAutostart != null && com.remoteappblocker.target.util.OemAutostartHelper.isOemSpecificDevice()) {
                Spacer(modifier = Modifier.height(12.dp))
                WizardPermissionItem(
                    stepNumber = "5+",
                    title = "OEM Autostart Protection",
                    description = "Detected ${com.remoteappblocker.target.util.OemAutostartHelper.getDeviceManufacturer().replaceFirstChar { it.uppercase() }}. Enable Autostart to prevent background kills.",
                    isGranted = false,
                    buttonText = "Open Autostart Settings",
                    onAction = onOpenOemAutostart
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step 6: Device Admin (P1)
            WizardPermissionItem(
                stepNumber = "6",
                title = "Device Admin (Anti-Uninstall)",
                description = "Blocks unauthorized app uninstallation without Admin PIN.",
                isGranted = state.isDeviceAdminActive,
                buttonText = "Activate Admin",
                onAction = onActivateAdmin
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Complete Setup Button
            Button(
                onClick = onFinishSetup,
                enabled = state.isEssentialGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    disabledContainerColor = Slate700
                )
            ) {
                Text(
                    text = if (state.isEssentialGranted) "Complete Setup & Continue" else "Grant Accessibility & Overlay to Continue",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                if (state.isEssentialGranted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun WizardPermissionItem(
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isGranted) SuccessGreen.copy(alpha = 0.4f) else Slate700,
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step badge or check icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (isGranted) SuccessGreen.copy(alpha = 0.2f) else Slate700,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGranted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Granted",
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = stepNumber,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                        fontSize = 13.sp
                    )
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
