package com.remoteappblocker.target.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.remoteappblocker.target.data.local.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules WHERE enabled = 1")
    fun getAllEnabledRules(): List<RuleEntity>

    @Query("SELECT * FROM rules")
    fun getAllRules(): List<RuleEntity>

    @Query("SELECT * FROM rules")
    fun observeAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE packageName = :packageName AND enabled = 1 LIMIT 1")
    fun getActiveRuleForPackage(packageName: String): RuleEntity?

    @Query("SELECT MAX(version) FROM rules")
    fun getLatestVersion(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateRules(rules: List<RuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    fun deleteRuleById(id: String)

    @Query("DELETE FROM rules WHERE packageName = :packageName")
    fun deleteRuleByPackage(packageName: String)

    @Query("DELETE FROM rules")
    fun clearAllRules()
}
