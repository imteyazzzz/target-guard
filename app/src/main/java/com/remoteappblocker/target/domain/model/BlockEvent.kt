package com.remoteappblocker.target.domain.model

data class BlockEvent(
    val id: Long = 0,
    val deviceId: String,
    val packageName: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
