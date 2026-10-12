package com.remoteappblocker.target.data.repository

import com.remoteappblocker.target.data.local.AppDatabase
import com.remoteappblocker.target.data.local.DevicePreferences
import com.remoteappblocker.target.data.local.entity.BlockEventEntity
import com.remoteappblocker.target.data.local.entity.RuleEntity
import com.remoteappblocker.target.domain.model.BlockEvent
import com.remoteappblocker.target.domain.model.Rule
import com.remoteappblocker.target.domain.repository.RuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RuleRepositoryImpl(
    private val database: AppDatabase,
    private val preferences: DevicePreferences
) : RuleRepository {

    override fun observeRules(): Flow<List<Rule>> {
        return database.ruleDao().observeAllRules().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllRules(): List<Rule> = withContext(Dispatchers.IO) {
        database.ruleDao().getAllRules().map { it.toDomain() }
    }

    override suspend fun getActiveRuleForPackage(packageName: String): Rule? = withContext(Dispatchers.IO) {
        database.ruleDao().getActiveRuleForPackage(packageName)?.toDomain()
    }

    override suspend fun saveRules(rules: List<Rule>) = withContext(Dispatchers.IO) {
        val entities = rules.map { RuleEntity.fromDomain(it) }
        database.ruleDao().insertOrUpdateRules(entities)
    }

    override suspend fun saveRule(rule: Rule) = withContext(Dispatchers.IO) {
        database.ruleDao().insertOrUpdateRule(RuleEntity.fromDomain(rule))
    }

    override suspend fun deleteRule(id: String) = withContext(Dispatchers.IO) {
        database.ruleDao().deleteRuleById(id)
    }

    override suspend fun getLatestVersion(): Long = withContext(Dispatchers.IO) {
        database.ruleDao().getLatestVersion() ?: 0L
    }

    override suspend fun logBlockEvent(packageName: String): Unit = withContext(Dispatchers.IO) {
        val entity = BlockEventEntity(
            deviceId = preferences.deviceId,
            packageName = packageName,
            occurredAt = System.currentTimeMillis(),
            isSynced = false
        )
        database.blockEventDao().insertEvent(entity)
        Unit
    }

    override suspend fun getUnsyncedEvents(limit: Int): List<BlockEvent> = withContext(Dispatchers.IO) {
        database.blockEventDao().getUnsyncedEvents(limit).map { it.toDomain() }
    }

    override suspend fun markEventsSynced(ids: List<Long>): Unit = withContext(Dispatchers.IO) {
        database.blockEventDao().markEventsSynced(ids)
    }
}
