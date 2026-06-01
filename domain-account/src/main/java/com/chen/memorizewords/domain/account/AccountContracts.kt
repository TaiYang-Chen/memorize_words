package com.chen.memorizewords.domain.account
import kotlinx.coroutines.flow.Flow

data class AccountSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long
)

data class AccountProfile(
    val userId: Long,
    val nickname: String?,
    val avatarUrl: String?,
    val phone: String?
)

interface AccountRepository {
    fun observeSession(): Flow<AccountSession?>
    suspend fun currentSession(): AccountSession?
    suspend fun saveSession(session: AccountSession)
    suspend fun clearSession()
    fun observeProfile(): Flow<AccountProfile?>
    suspend fun refreshProfile(): Result<AccountProfile>
}
