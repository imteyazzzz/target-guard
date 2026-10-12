package com.remoteappblocker.target.presentation.block

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteappblocker.target.data.local.DevicePreferences
import com.remoteappblocker.target.presentation.theme.AlertRed
import com.remoteappblocker.target.presentation.theme.DarkCard
import com.remoteappblocker.target.presentation.theme.DarkSurface
import com.remoteappblocker.target.presentation.theme.PrimaryBlue
import com.remoteappblocker.target.presentation.theme.RemoteAppBlockerTheme
import com.remoteappblocker.target.presentation.theme.Slate400
import com.remoteappblocker.target.presentation.theme.Slate700

class BlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_REASON = "extra_reason"

        fun createIntent(
            context: Context,
            appName: String,
            packageName: String,
            message: String?,
            reason: String = "ADMIN_RULE"
        ): Intent {
            return Intent(context, BlockActivity::class.java).apply {
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_REASON, reason)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock back press to navigate safely to Home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHomeScreen()
            }
        })

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "This Application"
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: "This app has been restricted by your administrator."
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "ADMIN_RULE"

        val prefs = DevicePreferences.getInstance(this)
        val adminName = prefs.adminName

        setContent {
            RemoteAppBlockerTheme {
                BlockScreenContent(
                    appName = appName,
                    packageName = packageName,
                    message = message,
                    adminName = adminName,
                    reason = reason,
                    onGoHomeClicked = { navigateToHomeScreen() }
                )
            }
        }
    }

    private fun navigateToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
fun BlockScreenContent(
    appName: String,
    packageName: String,
    message: String,
    adminName: String,
    reason: String,
    onGoHomeClicked: () -> Unit
) {
    val context = LocalContext.current
    val appIconBitmap = remember(packageName) {
        getAppIconBitmap(context, packageName)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing Shield & Alert Badge
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(AlertRed.copy(alpha = 0.35f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
                    .border(1.5.dp, AlertRed.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Access Blocked",
                    tint = AlertRed,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Access Blocked",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Admin Transparency Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Slate700.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Managed by $adminName",
                    style = MaterialTheme.typography.labelLarge,
                    color = Slate400,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Target App Card with real icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCard, RoundedCornerShape(16.dp))
                    .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap.asImageBitmap(),
                            contentDescription = appName,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate400,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Return to Home button
            Button(
                onClick = onGoHomeClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Return to Home",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun getAppIconBitmap(context: Context, packageName: String): Bitmap? {
    if (packageName.isBlank()) return null
    return try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawableToBitmap(drawable)
    } catch (e: Exception) {
        null
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
