package com.remoteappblocker.target.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.remoteappblocker.target.data.local.entity.BlockEventEntity

@Dao
interface BlockEventDao {
    @Insert
    fun insertEvent(event: BlockEventEntity): Long

    @Query("SELECT * FROM block_events WHERE isSynced = 0 ORDER BY occurredAt ASC LIMIT :limit")
    fun getUnsyncedEvents(limit: Int = 100): List<BlockEventEntity>

    @Query("UPDATE block_events SET isSynced = 1 WHERE id IN (:ids)")
    fun markEventsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM block_events")
    fun getTotalBlockEventsCount(): Long

    @Query("DELETE FROM block_events WHERE isSynced = 1 AND occurredAt < :timestamp")
    fun purgeOldSyncedEvents(timestamp: Long)
}
