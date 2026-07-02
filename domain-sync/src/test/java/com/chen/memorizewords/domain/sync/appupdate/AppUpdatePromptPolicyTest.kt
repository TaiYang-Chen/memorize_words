package com.chen.memorizewords.domain.sync.appupdate

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdatePromptPolicyTest {
    private val policy = AppUpdatePromptPolicy()

    @Test
    fun `force update ignores dismiss record`() {
        val info = updateInfo(forceUpdate = true)
        val dismissRecord = AppUpdateDismissRecord(releaseId = 10L, dismissedAtMillis = 1_000L)

        assertTrue(policy.shouldPrompt(info, dismissRecord, nowMillis = 2_000L))
    }

    @Test
    fun `optional update is hidden within defer window`() {
        val info = updateInfo(forceUpdate = false)
        val dismissRecord = AppUpdateDismissRecord(releaseId = 10L, dismissedAtMillis = 1_000L)

        assertFalse(
            policy.shouldPrompt(
                info,
                dismissRecord,
                nowMillis = 1_000L + AppUpdatePromptPolicy.DEFER_INTERVAL_MILLIS - 1
            )
        )
    }

    @Test
    fun `optional update is shown after defer window`() {
        val info = updateInfo(forceUpdate = false)
        val dismissRecord = AppUpdateDismissRecord(releaseId = 10L, dismissedAtMillis = 1_000L)

        assertTrue(
            policy.shouldPrompt(
                info,
                dismissRecord,
                nowMillis = 1_000L + AppUpdatePromptPolicy.DEFER_INTERVAL_MILLIS
            )
        )
    }

    @Test
    fun `different release ignores previous dismiss record`() {
        val info = updateInfo(forceUpdate = false)
        val dismissRecord = AppUpdateDismissRecord(releaseId = 9L, dismissedAtMillis = 1_000L)

        assertTrue(policy.shouldPrompt(info, dismissRecord, nowMillis = 2_000L))
    }

    @Test
    fun `ignored optional release is hidden from automatic prompt`() {
        val info = updateInfo(forceUpdate = false)
        val ignoreRecord = AppUpdateIgnoreRecord(releaseId = 10L, versionCode = 2, ignoredAtMillis = 1_000L)

        assertFalse(
            policy.shouldShowUpdate(
                info = info,
                ignoreRecord = ignoreRecord,
                deferredRecord = null,
                nowMillis = 2_000L,
                manual = false
            )
        )
    }

    @Test
    fun `manual check shows ignored optional release`() {
        val info = updateInfo(forceUpdate = false)
        val ignoreRecord = AppUpdateIgnoreRecord(releaseId = 10L, versionCode = 2, ignoredAtMillis = 1_000L)

        assertTrue(
            policy.shouldShowUpdate(
                info = info,
                ignoreRecord = ignoreRecord,
                deferredRecord = null,
                nowMillis = 2_000L,
                manual = true
            )
        )
    }

    @Test
    fun `deferred optional release is hidden before deferred time`() {
        val info = updateInfo(forceUpdate = false)
        val deferredRecord = AppUpdateDeferredRecord(releaseId = 10L, deferredUntilMillis = 3_000L)

        assertFalse(
            policy.shouldShowUpdate(
                info = info,
                ignoreRecord = null,
                deferredRecord = deferredRecord,
                nowMillis = 2_000L,
                manual = false
            )
        )
    }

    @Test
    fun `cached force update is reused within ttl`() {
        val info = updateInfo(forceUpdate = true)
        val cached = AppUpdateCachedForceUpdate(info = info, cachedAtMillis = 1_000L)

        assertTrue(
            policy.cachedForceUpdateOrNull(
                cached = cached,
                nowMillis = 1_000L + AppUpdatePromptPolicy.FORCE_CACHE_TTL_MILLIS
            ) === info
        )
    }

    @Test
    fun `cached force update is ignored after ttl`() {
        val info = updateInfo(forceUpdate = true)
        val cached = AppUpdateCachedForceUpdate(info = info, cachedAtMillis = 1_000L)

        assertFalse(
            policy.cachedForceUpdateOrNull(
                cached = cached,
                nowMillis = 1_000L + AppUpdatePromptPolicy.FORCE_CACHE_TTL_MILLIS + 1
            ) === info
        )
    }

    private fun updateInfo(forceUpdate: Boolean): AppUpdateInfo {
        return AppUpdateInfo(
            releaseId = 10L,
            currentVersion = AppVersion("1.0.0", 1),
            latestVersion = AppVersion("1.1.0", 2),
            forceUpdate = forceUpdate,
            releaseNotes = listOf("新增每日学习回顾", "优化学习路径", "修复同步提示"),
            downloadUrl = "/app-releases/test.apk",
            fileSha256 = null,
            fileSizeBytes = null
        )
    }
}
