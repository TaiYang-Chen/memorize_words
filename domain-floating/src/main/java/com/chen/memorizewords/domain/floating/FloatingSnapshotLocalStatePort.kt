package com.chen.memorizewords.domain.floating

import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord

interface FloatingSnapshotLocalStatePort {
    suspend fun overwriteDisplayRecordsFromRemote(records: List<FloatingWordDisplayRecord>)
}
