package com.remoteappblocker.target.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isBlocked: Boolean = false
)
