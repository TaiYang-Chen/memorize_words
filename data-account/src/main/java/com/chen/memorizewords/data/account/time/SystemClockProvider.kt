package com.chen.memorizewords.data.account.time

import com.chen.memorizewords.domain.account.time.ClockProvider
import javax.inject.Inject

class SystemClockProvider @Inject constructor() : ClockProvider {
    override fun nowEpochMillis(): Long {
        return System.currentTimeMillis()
    }
}

