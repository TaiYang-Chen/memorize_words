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

    @ColumnInfo(name = "signed_at")
    val signedAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(signedAt >= 0L) { "signedAt must be non-negative" }
        require(updatedAt >= 0L) { "updatedAt must be non-negative" }
    }
}
