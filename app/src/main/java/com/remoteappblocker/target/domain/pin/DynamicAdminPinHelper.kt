package com.remoteappblocker.target.domain.pin

object DynamicAdminPinHelper {
    private const val SALT = ":target_guard_admin_salt"

    /**
     * Generates deterministic 4-digit PIN for specific device and 1-minute time bucket.
     * Matches the JavaScript implementation on the Parent Web Portal.
     */
    fun generatePin(deviceId: String, timeOffsetMinutes: Long = 0L): String {
        if (deviceId.isBlank()) return "1234"
        val timeBucket = (System.currentTimeMillis() / 60000L) + timeOffsetMinutes
        val input = "$deviceId:$timeBucket$SALT"

        var hash = -2128831035
        for (i in 0 until input.length) {
            hash = hash xor input[i].code
            hash *= 16777619
        }

        val pinNum = kotlin.math.abs(hash) % 10000
        return String.format("%04d", pinNum)
    }

    /**
     * Validates entered PIN against:
     * 1. Current minute's rolling PIN
     * 2. Previous minute's PIN (grace window for clock drift or last-second submission)
     * 3. Next minute's PIN (grace window if device clock is slightly fast)
     * 4. Master override PIN fallback
     */
    fun isValidPin(enteredPin: String, deviceId: String, masterPin: String? = "1234"): Boolean {
        if (enteredPin.length != 4) return false

        // Check current 1-minute window
        if (enteredPin == generatePin(deviceId, 0L)) return true

        // Check previous 1-minute window (clock drift tolerance)
        if (enteredPin == generatePin(deviceId, -1L)) return true

        // Check next 1-minute window
        if (enteredPin == generatePin(deviceId, 1L)) return true

        // Master override fallback
        if (!masterPin.isNullOrBlank() && enteredPin == masterPin) return true

        return false
    }
}
