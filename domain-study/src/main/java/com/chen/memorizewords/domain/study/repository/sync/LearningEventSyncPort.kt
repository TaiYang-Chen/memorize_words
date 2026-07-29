package com.chen.memorizewords.domain.study.repository.sync

import com.chen.memorizewords.domain.sync.LearningEventSyncPayload

interface LearningEventSyncPort {
    fun schedule(payload: LearningEventSyncPayload)
}
