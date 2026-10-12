package com.remoteappblocker.target.presentation.pairing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteappblocker.target.domain.model.PairingInfo
import com.remoteappblocker.target.presentation.theme.DarkCard
import com.remoteappblocker.target.presentation.theme.DarkSurface
import com.remoteappblocker.target.presentation.theme.PrimaryBlue
import com.remoteappblocker.target.presentation.theme.Slate400
import com.remoteappblocker.target.presentation.theme.Slate600
import com.remoteappblocker.target.presentation.theme.Slate700
import com.remoteappblocker.target.presentation.theme.SuccessGreen
import com.remoteappblocker.target.presentation.theme.WarningAmber
import com.remoteappblocker.target.util.QrCodeUtils
import kotlinx.coroutines.delay

@Composable
fun PairingScreen(
    pairingInfo: PairingInfo,
    isPaired: Boolean,
    onRefreshCode: () -> Unit,
    onSimulatePairSuccess: () -> Unit,
    onContinueToApp: () -> Unit
) {
    var timeLeftSeconds by remember(pairingInfo.expiresAt) {
        val remaining = ((pairingInfo.expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        mutableLongStateOf(remaining)
    }

    LaunchedEffect(pairingInfo.expiresAt) {
        while (timeLeftSeconds > 0) {
            delay(1000L)
            timeLeftSeconds = ((pairingInfo.expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        }
    }

    val qrBitmap = remember(pairingInfo.qrPayload) {
        QrCodeUtils.generateQrBitmap(pairingInfo.qrPayload, size = 480)
    }

    val minutes = timeLeftSeconds / 60
    val seconds = timeLeftSeconds % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)

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

            // Header Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(PrimaryBlue.copy(alpha = 0.2f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Link Device",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Scan QR or enter 6-digit code in Admin Panel",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 6-Digit Code Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PAIRING CODE",
                        style = MaterialTheme.typography.labelLarge,
                        color = Slate400,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Formatted 6-digit code: e.g. "8 4 9   2 0 1"
                    val formattedCode = pairingInfo.pairingCode.chunked(3).joinToString("  ")
                    Text(
                        text = formattedCode,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Expires in $timerText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarningAmber
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = onRefreshCode,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Code",
                                tint = Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // QR Code Container
            Card(
                modifier = Modifier
                    .size(240.dp)
                    .border(1.dp, Slate700, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        CircularProgressIndicator(color = PrimaryBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Status Indicator or Paired confirmation
            if (isPaired) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Paired Successfully!",
                        style = MaterialTheme.typography.titleLarge,
                        color = SuccessGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onContinueToApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(
                        text = "Continue to Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Waiting for admin scan...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Demo Pair button for offline testing
                OutlinedButton(
                    onClick = onSimulatePairSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Instant Pair (Parental Demo Mode)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
