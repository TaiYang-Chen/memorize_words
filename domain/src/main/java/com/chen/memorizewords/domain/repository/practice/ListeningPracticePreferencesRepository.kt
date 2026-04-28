package com.chen.memorizewords.domain.repository.practice

interface ListeningPracticePreferencesRepository {
    suspend fun getLastListeningPracticeModeName(): String?
    suspend fun saveLastListeningPracticeModeName(modeName: String)
    suspend fun hasShownModeSwitchHint(): Boolean
    suspend fun markModeSwitchHintShown()
}
