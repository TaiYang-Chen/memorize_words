package com.chen.memorizewords.data.sync.remote.appupdate

import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.await
import com.chen.memorizewords.data.sync.remoteapi.api.appupdate.AppUpdateApiService
import com.chen.memorizewords.data.sync.remoteapi.api.appupdate.AppUpdateCheckRequestDto
import com.chen.memorizewords.data.sync.remoteapi.api.appupdate.AppUpdateCheckResponseDto
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCheck
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCheckResult
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateRepository
import com.chen.memorizewords.domain.sync.appupdate.AppVersion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val apiService: AppUpdateApiService,
    private val requestExecutor: NetworkRequestExecutor
) : AppUpdateRepository {
    override suspend fun checkUpdate(request: AppUpdateCheck): Result<AppUpdateCheckResult> {
        val remote = requestExecutor.executePublic {
            apiService.checkUpdate(request.toDto())
                .await<ApiResponse<AppUpdateCheckResponseDto>, AppUpdateCheckResponseDto>()
        }
        return when (remote) {
            is NetworkResult.Success -> Result.success(remote.data.toDomain(request))
            is NetworkResult.Failure -> Result.failure(remote.toException())
        }
    }

    private fun AppUpdateCheck.toDto(): AppUpdateCheckRequestDto {
        return AppUpdateCheckRequestDto(
            platform = platform,
            versionCode = versionCode,
            versionName = versionName,
            channel = channel,
            packageName = packageName,
            installId = installId
        )
    }

    private fun AppUpdateCheckResponseDto.toDomain(request: AppUpdateCheck): AppUpdateCheckResult {
        val available = updateAvailable || hasUpdate
        if (!available) return AppUpdateCheckResult.NoUpdate
        val latestName = latestVersion?.versionName ?: versionName
        val latestCode = latestVersion?.versionCode ?: versionCode
        val notes = releaseNoteItems?.filter { it.isNotBlank() }
            ?: releaseNotes?.lines()?.filter { it.isNotBlank() }
            ?: emptyList()
        if (releaseId == null || latestName.isNullOrBlank() || latestCode == null || downloadUrl.isNullOrBlank()) {
            return AppUpdateCheckResult.NoUpdate
        }
        return AppUpdateCheckResult.UpdateAvailable(
            AppUpdateInfo(
                releaseId = releaseId,
                currentVersion = AppVersion(
                    versionName = currentVersion?.versionName ?: request.versionName,
                    versionCode = currentVersion?.versionCode ?: request.versionCode
                ),
                latestVersion = AppVersion(
                    versionName = latestName,
                    versionCode = latestCode
                ),
                forceUpdate = forceUpdate,
                releaseNotes = notes.take(5),
                downloadUrl = downloadUrl,
                fileSha256 = fileSha256,
                fileSizeBytes = fileSizeBytes
            )
        )
    }

    private fun NetworkResult.Failure.toException(): Throwable {
        return when (this) {
            is NetworkResult.Failure.HttpError -> IllegalStateException(message ?: "HTTP $code")
            is NetworkResult.Failure.Unauthorized -> IllegalStateException(message ?: "Unauthorized")
            is NetworkResult.Failure.NetworkError -> throwable
            is NetworkResult.Failure.GenericError -> IllegalStateException(message)
        }
    }
}
