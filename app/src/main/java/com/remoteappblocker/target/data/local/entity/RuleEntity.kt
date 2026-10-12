package com.remoteappblocker.target.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.remoteappblocker.target.domain.model.Rule
import com.remoteappblocker.target.domain.model.ScheduleConfig

@Entity(
    tableName = "rules",
    indices = [Index(value = ["packageName"])]
)
data class RuleEntity(
    @PrimaryKey
    val id: String,
    val deviceId: String,
    val packageName: String,
    val mode: String, // 'block', 'limit', 'schedule'
    val dailyLimitMin: Int?,
    val scheduleJson: String?,
    val message: String?,
    val enabled: Boolean,
    val version: Long,
    val updatedAt: String?
) {
    fun toDomain(): Rule {
        return Rule(
            id = id,
            deviceId = deviceId,
            packageName = packageName,
            mode = mode,
            dailyLimitMin = dailyLimitMin,
            schedule = ScheduleConfig.fromJson(scheduleJson),
            message = message,
            enabled = enabled,
            version = version,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(rule: Rule): RuleEntity {
            return RuleEntity(
                id = rule.id,
                deviceId = rule.deviceId,
                packageName = rule.packageName,
                mode = rule.mode,
                dailyLimitMin = rule.dailyLimitMin,
                scheduleJson = rule.schedule?.let { ScheduleConfig.toJson(it) },
                message = rule.message,
                enabled = rule.enabled,
                version = rule.version,
                updatedAt = rule.updatedAt
            )
        }
    }
}
