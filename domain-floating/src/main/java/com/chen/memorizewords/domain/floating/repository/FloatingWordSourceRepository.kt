package com.chen.memorizewords.domain.floating.repository

import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceSnapshot

interface FloatingWordSourceRepository {
    suspend fun loadSnapshot(settings: FloatingWordSettings): FloatingWordSourceSnapshot
}
