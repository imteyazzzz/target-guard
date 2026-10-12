package com.remoteappblocker.target.domain.repository

import com.remoteappblocker.target.domain.model.BlockEvent
import com.remoteappblocker.target.domain.model.Rule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    fun observeRules(): Flow<List<Rule>>
    suspend fun getAllRules(): List<Rule>
    suspend fun getActiveRuleForPackage(packageName: String): Rule?
    suspend fun saveRules(rules: List<Rule>)
    suspend fun saveRule(rule: Rule)
    suspend fun deleteRule(id: String)
    suspend fun getLatestVersion(): Long
    suspend fun logBlockEvent(packageName: String)
    suspend fun getUnsyncedEvents(limit: Int = 100): List<BlockEvent>
    suspend fun markEventsSynced(ids: List<Long>)
}
