package com.chen.memorizewords.data.study.repository.local

import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity

data class LocalWriteResult(
    val dailyStudyDuration: DailyStudyDurationEntity? = null,
    val checkInRecord: CheckInRecordEntity? = null
)
