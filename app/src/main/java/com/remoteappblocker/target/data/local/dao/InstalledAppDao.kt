package com.remoteappblocker.target.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.remoteappblocker.target.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertApps(apps: List<InstalledAppEntity>)

    @Query("SELECT * FROM installed_apps ORDER BY appName ASC")
    fun getAllApps(): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps ORDER BY appName ASC")
    fun observeAllApps(): Flow<List<InstalledAppEntity>>

    @Query("DELETE FROM installed_apps")
    fun clearAll()
}
