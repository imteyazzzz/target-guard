package com.remoteappblocker.target.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.remoteappblocker.target.domain.model.BlockEvent

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val packageName: String,
    val occurredAt: Long,
    val isSynced: Boolean = false
) {
    fun toDomain(): BlockEvent {
        return BlockEvent(
            id = id,
            deviceId = deviceId,
            packageName = packageName,
            occurredAt = occurredAt,
            isSynced = isSynced
        )
    }

    companion object {
        fun fromDomain(event: BlockEvent): BlockEventEntity {
            return BlockEventEntity(
                id = event.id,
                deviceId = event.deviceId,
                packageName = event.packageName,
                occurredAt = event.occurredAt,
                isSynced = event.isSynced
            )
        }
    }
}
