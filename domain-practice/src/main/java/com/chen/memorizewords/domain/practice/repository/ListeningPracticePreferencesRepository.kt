package com.chen.memorizewords.domain.practice.repository

import com.chen.memorizewords.domain.practice.ListeningAnswerAreaPosition
import com.chen.memorizewords.domain.practice.ListeningPronunciationPreference

interface ListeningPracticePreferencesRepository {
    suspend fun getLastListeningPracticeModeName(): String?
    suspend fun saveLastListeningPracticeModeName(modeName: String)
    suspend fun getAnswerAreaPosition(): ListeningAnswerAreaPosition
    suspend fun saveAnswerAreaPosition(position: ListeningAnswerAreaPosition)
    suspend fun getPronunciationPreference(): ListeningPronunciationPreference
    suspend fun savePronunciationPreference(preference: ListeningPronunciationPreference)
    suspend fun hasShownModeSwitchHint(): Boolean
    suspend fun markModeSwitchHintShown()
}
