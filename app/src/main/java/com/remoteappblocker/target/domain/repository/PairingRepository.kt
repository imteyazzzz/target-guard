package com.remoteappblocker.target.domain.repository

import com.remoteappblocker.target.domain.model.PairingInfo
import kotlinx.coroutines.flow.Flow

interface PairingRepository {
    fun isDevicePaired(): Boolean
    suspend fun generateOrGetPairingInfo(): PairingInfo
    suspend fun registerDeviceInSupabase(): Result<Unit>
    fun observePairingStatus(): Flow<Boolean>
    suspend fun confirmPaired(adminName: String)
    suspend fun uploadInstalledApps(): Result<Int>
}
