package com.remoteappblocker.target.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScheduleConfig(
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1 = Sunday .. 7 = Saturday
    val startMinute: Int = 0,                         // Minute of day (0 to 1439), e.g. 22:00 = 1320
    val endMinute: Int = 1439                         // Minute of day, e.g. 06:00 = 360
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonStr: String?): ScheduleConfig? {
            if (jsonStr.isNullOrBlank()) return null
            return try {
                json.decodeFromString<ScheduleConfig>(jsonStr)
            } catch (e: Exception) {
                null
            }
        }

        fun toJson(config: ScheduleConfig): String {
            return json.encodeToString(serializer(), config)
        }
    }
}
