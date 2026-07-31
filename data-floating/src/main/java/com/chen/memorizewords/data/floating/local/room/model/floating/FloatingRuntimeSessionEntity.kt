package com.chen.memorizewords.data.floating.local.room.model.floating

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "floating_runtime_session")
data class FloatingRuntimeSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val revision: Long,
    val phase: String,
    val source: String,
    @ColumnInfo(name = "target_pack_id")
    val targetPackId: String?,
    val progress: Int,
    @ColumnInfo(name = "error_code")
    val errorCode: String?,
    @ColumnInfo(name = "start_deadline_at_ms")
    val startDeadlineAtMs: Long?,
    @ColumnInfo(name = "last_heartbeat_at_ms")
    val lastHeartbeatAtMs: Long?,
    @ColumnInfo(name = "config_version")
    val configVersion: Long,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
