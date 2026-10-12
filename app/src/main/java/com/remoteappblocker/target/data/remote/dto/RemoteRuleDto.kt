package com.remoteappblocker.target.data.remote.dto

import com.remoteappblocker.target.domain.model.Rule
import com.remoteappblocker.target.domain.model.ScheduleConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RemoteRuleDto(
    val id: String,
    val device_id: String,
    val package_name: String,
    val mode: String = "block",
    val daily_limit_min: Int? = null,
    val schedule: JsonElement? = null,
    val message: String? = null,
    val enabled: Boolean = true,
    val version: Long = 1,
    val updated_at: String? = null
) {
    fun toDomain(): Rule {
        val scheduleStr = schedule?.toString()
        return Rule(
            id = id,
            deviceId = device_id,
            packageName = package_name,
            mode = mode,
            dailyLimitMin = daily_limit_min,
            schedule = ScheduleConfig.fromJson(scheduleStr),
            message = message,
            enabled = enabled,
            version = version,
            updatedAt = updated_at
        )
    }
}

@Serializable
data class RemoteBlockEventDto(
    val device_id: String,
    val package_name: String,
    val occurred_at: String
)
