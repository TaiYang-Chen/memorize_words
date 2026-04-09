package com.chen.memorizewords.data.local.room.model.study.checkin

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_in_record")
data class CheckInRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "signed_at")
    val signedAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
