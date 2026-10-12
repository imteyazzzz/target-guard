package com.remoteappblocker.target.domain.engine

import com.remoteappblocker.target.domain.model.Rule
import java.util.Calendar

data class RuleEvaluationResult(
    val isBlocked: Boolean,
    val message: String? = null,
    val reason: String = "NO_RULE"
)

class RuleEngine {

    /**
     * Pure Kotlin function to evaluate rule conditions.
     *
     * @param rule Rule to evaluate
     * @param currentDayOfWeek Calendar.DAY_OF_WEEK (1=Sunday, 2=Monday, ..., 7=Saturday)
     * @param currentMinuteOfDay Minute from 0 to 1439 (hour * 60 + minute)
     * @param dailyUsageMinutes Used minutes today (for limit mode)
     */
    fun evaluate(
        rule: Rule?,
        currentDayOfWeek: Int,
        currentMinuteOfDay: Int,
        dailyUsageMinutes: Int = 0
    ): RuleEvaluationResult {
        if (rule == null || !rule.enabled) {
            return RuleEvaluationResult(isBlocked = false, reason = "NO_RULE_OR_DISABLED")
        }

        return when (rule.mode.lowercase()) {
            "block" -> {
                RuleEvaluationResult(
                    isBlocked = true,
                    message = rule.message ?: "This app has been restricted by your administrator.",
                    reason = "ALWAYS_BLOCKED"
                )
            }
            "schedule" -> {
                val schedule = rule.schedule
                    ?: return RuleEvaluationResult(isBlocked = false, reason = "INVALID_SCHEDULE")

                // Check active day
                if (!schedule.days.contains(currentDayOfWeek)) {
                    return RuleEvaluationResult(isBlocked = false, reason = "OUTSIDE_SCHEDULE_DAYS")
                }

                // Check time window, correctly handling midnight wrap-around (e.g., 22:00 to 06:00)
                val isInsideWindow = if (schedule.startMinute <= schedule.endMinute) {
                    currentMinuteOfDay in schedule.startMinute..schedule.endMinute
                } else {
                    currentMinuteOfDay >= schedule.startMinute || currentMinuteOfDay <= schedule.endMinute
                }

                if (isInsideWindow) {
                    RuleEvaluationResult(
                        isBlocked = true,
                        message = rule.message ?: "This app is restricted during this scheduled time.",
                        reason = "INSIDE_SCHEDULE_WINDOW"
                    )
                } else {
                    RuleEvaluationResult(isBlocked = false, reason = "OUTSIDE_SCHEDULE_WINDOW")
                }
            }
            "limit" -> {
                val limit = rule.dailyLimitMin
                    ?: return RuleEvaluationResult(isBlocked = false, reason = "INVALID_LIMIT")

                if (dailyUsageMinutes >= limit) {
                    RuleEvaluationResult(
                        isBlocked = true,
                        message = rule.message ?: "Daily limit of $limit minutes exceeded.",
                        reason = "DAILY_LIMIT_EXCEEDED"
                    )
                } else {
                    RuleEvaluationResult(isBlocked = false, reason = "WITHIN_LIMIT")
                }
            }
            else -> RuleEvaluationResult(isBlocked = false, reason = "UNKNOWN_MODE")
        }
    }

    /**
     * Evaluates current system time against the rule
     */
    fun evaluateNow(
        rule: Rule?,
        dailyUsageMinutes: Int = 0
    ): RuleEvaluationResult {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return evaluate(rule, dayOfWeek, minuteOfDay, dailyUsageMinutes)
    }
}
