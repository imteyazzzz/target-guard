package com.remoteappblocker.target.domain.pin

object DynamicAdminPinHelper {
    private const val SALT = ":target_guard_admin_salt"

    /**
     * Generates deterministic 4-digit PIN for specific device and 1-minute time bucket.
     * Matches the JavaScript implementation on the Parent Web Portal.
     */
    fun generatePin(deviceId: String, timeOffsetMinutes: Long = 0L): String {
        if (deviceId.isBlank()) return ""
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
     * Validates entered PIN against ONLY the dynamic 1-minute rolling PIN for this device:
     * 1. Current minute's rolling PIN
     * 2. Previous minute's PIN (grace window for clock drift or last-second submission)
     * 3. Next minute's PIN (grace window if device clock is slightly fast)
     *
     * NO fallback or static default PIN (like 1234) is allowed.
     */
    fun isValidPin(enteredPin: String, deviceId: String): Boolean {
        if (enteredPin.length != 4 || deviceId.isBlank()) return false

        // Check current 1-minute window
        val currentPin = generatePin(deviceId, 0L)
        if (currentPin.isNotEmpty() && enteredPin == currentPin) return true

        // Check previous 1-minute window (clock drift tolerance)
        val prevPin = generatePin(deviceId, -1L)
        if (prevPin.isNotEmpty() && enteredPin == prevPin) return true

        // Check next 1-minute window
        val nextPin = generatePin(deviceId, 1L)
        if (nextPin.isNotEmpty() && enteredPin == nextPin) return true

        return false
    }
}
