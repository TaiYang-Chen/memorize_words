package com.chen.memorizewords.domain.account.time

interface ClockProvider {
    fun nowEpochMillis(): Long
}

