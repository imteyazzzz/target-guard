package com.remoteappblocker.target.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PairingInfo(
    val deviceId: String,
    val pairingCode: String,
    val expiresAt: Long,
    val qrPayload: String
)
