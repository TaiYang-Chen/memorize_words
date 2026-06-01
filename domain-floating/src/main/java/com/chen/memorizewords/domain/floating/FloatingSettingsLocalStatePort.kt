package com.chen.memorizewords.domain.floating

import com.chen.memorizewords.domain.floating.model.FloatingWordSettings

interface FloatingSettingsLocalStatePort {
    fun overwriteFromRemote(settings: FloatingWordSettings)
    fun clearLocalState()
}
