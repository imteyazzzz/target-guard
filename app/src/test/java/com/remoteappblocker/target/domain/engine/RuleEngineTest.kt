package com.remoteappblocker.target.domain.engine

import com.remoteappblocker.target.domain.model.Rule
import com.remoteappblocker.target.domain.model.ScheduleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class RuleEngineTest {

    private lateinit var engine: RuleEngine

    @Before
    fun setup() {
        engine = RuleEngine()
    }

    @Test
    fun testDirectBlockModeReturnsBlocked() {
        val rule = Rule(
            id = "r1",
            deviceId = "d1",
            packageName = "com.instagram.android",
            mode = "block",
            enabled = true,
            message = "Instagram is blocked"
        )

        val result = engine.evaluate(rule, currentDayOfWeek = Calendar.MONDAY, currentMinuteOfDay = 600)
        assertTrue(result.isBlocked)
        assertEquals("Instagram is blocked", result.message)
        assertEquals("ALWAYS_BLOCKED", result.reason)
    }

    @Test
    fun testDisabledRuleIsNotBlocked() {
        val rule = Rule(
            id = "r2",
            deviceId = "d1",
            packageName = "com.instagram.android",
            mode = "block",
            enabled = false
        )

        val result = engine.evaluate(rule, currentDayOfWeek = Calendar.MONDAY, currentMinuteOfDay = 600)
        assertFalse(result.isBlocked)
        assertEquals("NO_RULE_OR_DISABLED", result.reason)
    }

    @Test
    fun testScheduleNormalWindowInsideAndOutside() {
        // Window from 09:00 (540 min) to 17:00 (1020 min) on weekdays
        val rule = Rule(
            id = "r3",
            deviceId = "d1",
            packageName = "com.youtube.android",
            mode = "schedule",
            schedule = ScheduleConfig(
                days = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
                startMinute = 540,
                endMinute = 1020
            ),
            enabled = true
        )

        // Inside window at 11:30 (690 min) on Wednesday
        val inside = engine.evaluate(rule, currentDayOfWeek = Calendar.WEDNESDAY, currentMinuteOfDay = 690)
        assertTrue(inside.isBlocked)
        assertEquals("INSIDE_SCHEDULE_WINDOW", inside.reason)

        // Outside window at 18:30 (1110 min) on Wednesday
        val outside = engine.evaluate(rule, currentDayOfWeek = Calendar.WEDNESDAY, currentMinuteOfDay = 1110)
        assertFalse(outside.isBlocked)
        assertEquals("OUTSIDE_SCHEDULE_WINDOW", outside.reason)

        // Outside active days: Sunday at 11:30
        val outsideDay = engine.evaluate(rule, currentDayOfWeek = Calendar.SUNDAY, currentMinuteOfDay = 690)
        assertFalse(outsideDay.isBlocked)
        assertEquals("OUTSIDE_SCHEDULE_DAYS", outsideDay.reason)
    }

    @Test
    fun testScheduleMidnightCrossingWindow() {
        // Window from 22:00 (1320 min) to 06:00 (360 min)
        val rule = Rule(
            id = "r4",
            deviceId = "d1",
            packageName = "com.snapchat.android",
            mode = "schedule",
            schedule = ScheduleConfig(
                days = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY),
                startMinute = 1320,
                endMinute = 360
            ),
            enabled = true
        )

        // At 23:30 (1410 min) -> Should be blocked
        val nightBeforeMidnight = engine.evaluate(rule, currentDayOfWeek = Calendar.MONDAY, currentMinuteOfDay = 1410)
        assertTrue(nightBeforeMidnight.isBlocked)

        // At 02:15 (135 min) -> Should be blocked
        val morningEarly = engine.evaluate(rule, currentDayOfWeek = Calendar.MONDAY, currentMinuteOfDay = 135)
        assertTrue("02:15 should be inside 22:00-06:00 window", morningEarly.isBlocked)

        // At 14:00 (840 min) -> Should NOT be blocked
        val daytime = engine.evaluate(rule, currentDayOfWeek = Calendar.MONDAY, currentMinuteOfDay = 840)
        assertFalse(daytime.isBlocked)
    }

    @Test
    fun testDailyLimitExceeded() {
        val rule = Rule(
            id = "r5",
            deviceId = "d1",
            packageName = "com.facebook.katana",
            mode = "limit",
            dailyLimitMin = 45,
            enabled = true
        )

        // Used 30 minutes (< 45) -> Not blocked
        val underLimit = engine.evaluate(rule, currentDayOfWeek = Calendar.MONDAY, currentMinuteOfDay = 600, dailyUsageMinutes = 30)
        assertFalse(underLimit.isBlocked)

        // Used 45 minutes (== 45) -> Blocked
        val exactLimit = engine.evaluate(rule, currentDayOfWeek = Calendar.MONDAY, currentMinuteOfDay = 600, dailyUsageMinutes = 45)
        assertTrue(exactLimit.isBlocked)
        assertEquals("DAILY_LIMIT_EXCEEDED", exactLimit.reason)

        // Used 60 minutes (> 45) -> Blocked
        val overLimit = engine.evaluate(rule, currentDayOfWeek = Calendar.MONDAY, currentMinuteOfDay = 600, dailyUsageMinutes = 60)
        assertTrue(overLimit.isBlocked)
    }
}
