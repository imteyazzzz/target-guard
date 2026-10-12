package com.remoteappblocker.target.presentation.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.remoteappblocker.target.presentation.theme.AlertRed
import com.remoteappblocker.target.presentation.theme.DarkCard
import com.remoteappblocker.target.presentation.theme.PrimaryBlue
import com.remoteappblocker.target.presentation.theme.Slate400
import com.remoteappblocker.target.presentation.theme.Slate700
import com.remoteappblocker.target.presentation.theme.Slate800

@Composable
fun AdminPinDialog(
    deviceId: String = "",
    masterPin: String = "1234",
    title: String = "Admin Authentication",
    subtitle: String = "Enter 1-minute Admin PIN from Parent Portal",
    onDismissRequest: () -> Unit,
    onPinVerified: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lock Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(PrimaryBlue.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isError -> AlertRed
                                        isFilled -> PrimaryBlue
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    2.dp,
                                    if (isError) AlertRed else if (isFilled) PrimaryBlue else Slate700,
                                    CircleShape
                                )
                        )
                    }
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Incorrect or expired PIN. Check live PIN on Web Portal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlertRed,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Keypad Layout (1..9, Backspace, 0)
                KeypadGrid(
                    onNumberClick = { num ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + num
                            enteredPin = newPin
                            isError = false

                            if (newPin.length == 4) {
                                val isValid = com.remoteappblocker.target.domain.pin.DynamicAdminPinHelper.isValidPin(
                                    enteredPin = newPin,
                                    deviceId = deviceId,
                                    masterPin = masterPin
                                )
                                if (isValid) {
                                    onPinVerified()
                                } else {
                                    isError = true
                                    enteredPin = ""
                                }
                            }
                        }
                    },
                    onBackspaceClick = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            isError = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismissRequest) {
                    Text("Cancel", color = Slate400, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun KeypadGrid(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (key in row) {
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(54.dp))
                    } else if (key == "DEL") {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .clickable { onBackspaceClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = Slate400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Slate800)
                                .clickable { onNumberClick(key) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
