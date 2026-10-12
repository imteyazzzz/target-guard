package com.remoteappblocker.target.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.remoteappblocker.target.data.local.dao.BlockEventDao
import com.remoteappblocker.target.data.local.dao.InstalledAppDao
import com.remoteappblocker.target.data.local.dao.RuleDao
import com.remoteappblocker.target.data.local.entity.BlockEventEntity
import com.remoteappblocker.target.data.local.entity.InstalledAppEntity
import com.remoteappblocker.target.data.local.entity.RuleEntity

@Database(
    entities = [
        RuleEntity::class,
        BlockEventEntity::class,
        InstalledAppEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun installedAppDao(): InstalledAppDao

    companion object {
        private const val DATABASE_NAME = "remote_blocker.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
