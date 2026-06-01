package com.chen.memorizewords.data.study.local

import androidx.room.TypeConverter
import com.chen.memorizewords.domain.study.model.record.CheckInType

class StudyRoomConverters {
    @TypeConverter
    fun fromCheckInType(value: CheckInType?): String? = value?.name

    @TypeConverter
    fun toCheckInType(value: String?): CheckInType? = value?.let(CheckInType::valueOf)
}
