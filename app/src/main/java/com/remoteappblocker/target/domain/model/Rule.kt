package com.remoteappblocker.target.domain.model

data class Rule(
    val id: String,
    val deviceId: String,
    val packageName: String,
    val mode: String = "block", // 'block', 'limit', 'schedule'
    val dailyLimitMin: Int? = null,
    val schedule: ScheduleConfig? = null,
    val message: String? = null,
    val enabled: Boolean = true,
    val version: Long = 1,
    val updatedAt: String? = null
)
