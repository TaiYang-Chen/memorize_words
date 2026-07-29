package com.chen.memorizewords.data.study.repository.record

import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import javax.inject.Inject

class BusinessDateProviderImpl @Inject constructor(
    private val calendar: CheckInBusinessCalendar
) : BusinessDateProvider {
    override fun currentBusinessDate(): String = calendar.currentBusinessDate()
}
