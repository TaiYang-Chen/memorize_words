package com.chen.memorizewords.data.study.local.room.model.study.checkin

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chen.memorizewords.domain.study.model.record.CheckInType

@Entity(tableName = "check_in_record")
data class CheckInRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "type")
    val type: CheckInType,

    @ColumnInfo(name = "signed_at_ms")
    val signedAtMs: Long,

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
) {
    init {
        require(signedAtMs >= 0L) { "signedAtMs must be non-negative" }
        require(updatedAtMs >= 0L) { "updatedAtMs must be non-negative" }
    }
}
