package com.chen.memorizewords.domain.practice

interface PracticeSettingsLocalStatePort {
    fun overwriteFromRemote(settings: PracticeSettings)
    fun clearLocalState()
}
