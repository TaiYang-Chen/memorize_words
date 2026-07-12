package com.chen.memorizewords.startup.appupdate

import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCachedForceUpdate
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCheck
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCheckResult
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateDeferredRecord
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateDismissRecord
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateIgnoreRecord
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateLocalStateRepository
import com.chen.memorizewords.domain.sync.appupdate.AppUpdatePromptPolicy
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateRepository
import com.chen.memorizewords.domain.sync.appupdate.AppVersion
import com.chen.memorizewords.domain.sync.appupdate.CheckAppUpdateUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AppUpdateStartupCoordinatorTest {

    @Test
    fun `offline launch skips remote check and returns cached force update`() = runBlocking {
        val now = 10_000L
        val cachedInfo = updateInfo(forceUpdate = true)
        val local = FakeLocalStateRepository(
            cachedForce = AppUpdateCachedForceUpdate(cachedInfo, now)
        )
        val remote = FakeAppUpdateRepository { error("Remote check must not run") }
        val coordinator = createCoordinator(remote, local)

        val result = coordinator.resolveStartupPrompt(hasNetwork = false, nowMillis = now)

        assertEquals(cachedInfo, result)
        assertEquals(0, remote.callCount)
        assertEquals(0, local.installIdReadCount)
    }

    @Test
    fun `online timeout returns cached force update`() = runBlocking {
        val now = 20_000L
        val cachedInfo = updateInfo(forceUpdate = true)
        val local = FakeLocalStateRepository(
            cachedForce = AppUpdateCachedForceUpdate(cachedInfo, now)
        )
        val remote = FakeAppUpdateRepository {
            delay(5_000)
            Result.success(AppUpdateCheckResult.NoUpdate)
        }
        val coordinator = createCoordinator(remote, local)

        val result = coordinator.resolveStartupPrompt(
            hasNetwork = true,
            nowMillis = now,
            timeoutMillis = 20
        )

        assertEquals(cachedInfo, result)
        assertEquals(1, remote.callCount)
    }

    @Test
    fun `network failure returns cached force update`() = runBlocking {
        val now = 30_000L
        val cachedInfo = updateInfo(forceUpdate = true)
        val local = FakeLocalStateRepository(
            cachedForce = AppUpdateCachedForceUpdate(cachedInfo, now)
        )
        val remote = FakeAppUpdateRepository {
            Result.failure(IllegalStateException("server unavailable"))
        }
        val coordinator = createCoordinator(remote, local)

        val result = coordinator.resolveStartupPrompt(hasNetwork = true, nowMillis = now)

        assertEquals(cachedInfo, result)
    }

    @Test
    fun `successful update check keeps prompt and cache behavior`() = runBlocking {
        val now = 40_000L
        val forceInfo = updateInfo(forceUpdate = true)
        val local = FakeLocalStateRepository()
        val remote = FakeAppUpdateRepository {
            Result.success(AppUpdateCheckResult.UpdateAvailable(forceInfo))
        }
        val coordinator = createCoordinator(remote, local)

        val result = coordinator.resolveStartupPrompt(hasNetwork = true, nowMillis = now)

        assertEquals(forceInfo, result)
        assertEquals(AppUpdateCachedForceUpdate(forceInfo, now), local.cachedForce)
    }

    private fun createCoordinator(
        repository: AppUpdateRepository,
        local: AppUpdateLocalStateRepository
    ): AppUpdateStartupCoordinator {
        return AppUpdateStartupCoordinator(
            checkAppUpdate = CheckAppUpdateUseCase(repository),
            localStateRepository = local,
            promptPolicy = AppUpdatePromptPolicy()
        )
    }

    private fun updateInfo(forceUpdate: Boolean): AppUpdateInfo {
        return AppUpdateInfo(
            releaseId = 7L,
            currentVersion = AppVersion("1.0.0", 1),
            latestVersion = AppVersion("1.1.0", 2),
            forceUpdate = forceUpdate,
            releaseNotes = listOf("Fixes"),
            downloadUrl = "http://localhost/update.apk",
            fileSha256 = null,
            fileSizeBytes = null
        )
    }
}

private class FakeAppUpdateRepository(
    private val result: suspend (AppUpdateCheck) -> Result<AppUpdateCheckResult>
) : AppUpdateRepository {
    var callCount: Int = 0

    override suspend fun checkUpdate(request: AppUpdateCheck): Result<AppUpdateCheckResult> {
        callCount += 1
        return result(request)
    }
}

private class FakeLocalStateRepository(
    var cachedForce: AppUpdateCachedForceUpdate? = null
) : AppUpdateLocalStateRepository {
    var installIdReadCount: Int = 0
    private var dismissRecord: AppUpdateDismissRecord? = null
    private var ignoreRecord: AppUpdateIgnoreRecord? = null
    private var deferredRecord: AppUpdateDeferredRecord? = null
    private var cachedLatestInfo: AppUpdateInfo? = null

    override fun getOrCreateInstallId(): String {
        installIdReadCount += 1
        return "install-id"
    }

    override fun getDismissRecord(): AppUpdateDismissRecord? = dismissRecord

    override fun setDismissed(releaseId: Long, dismissedAtMillis: Long) {
        dismissRecord = AppUpdateDismissRecord(releaseId, dismissedAtMillis)
    }

    override fun getIgnoreRecord(): AppUpdateIgnoreRecord? = ignoreRecord

    override fun setIgnored(releaseId: Long, versionCode: Int, ignoredAtMillis: Long) {
        ignoreRecord = AppUpdateIgnoreRecord(releaseId, versionCode, ignoredAtMillis)
    }

    override fun getDeferredRecord(): AppUpdateDeferredRecord? = deferredRecord

    override fun setDeferred(releaseId: Long, deferredUntilMillis: Long) {
        deferredRecord = AppUpdateDeferredRecord(releaseId, deferredUntilMillis)
    }

    override fun getCachedLatestInfo(): AppUpdateInfo? = cachedLatestInfo

    override fun setCachedLatestInfo(info: AppUpdateInfo?) {
        cachedLatestInfo = info
    }

    override fun getCachedForceUpdate(): AppUpdateCachedForceUpdate? = cachedForce

    override fun setCachedForceUpdate(info: AppUpdateInfo?, cachedAtMillis: Long) {
        cachedForce = info?.let { AppUpdateCachedForceUpdate(it, cachedAtMillis) }
    }
}
