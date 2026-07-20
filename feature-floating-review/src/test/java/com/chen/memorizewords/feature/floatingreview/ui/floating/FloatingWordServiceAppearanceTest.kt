package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.sprite.SpritePackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingWordServiceAppearanceTest {

    @Test
    fun `ball size scale follows supported percentage range`() {
        assertEquals(0.01f, resolveBallSizeScale(1))
        assertEquals(0.6f, resolveBallSizeScale(60))
        assertEquals(2f, resolveBallSizeScale(200))
    }

    @Test
    fun `ball size scale clamps unsupported percentage`() {
        assertEquals(0.01f, resolveBallSizeScale(0))
        assertEquals(2f, resolveBallSizeScale(201))
    }

    @Test
    fun `stopped service rejects stale asynchronous operations`() {
        assertTrue(
            isFloatingServiceOperationActive(
                stopping = false,
                currentGeneration = 4L,
                operationGeneration = 4L
            )
        )
        assertFalse(
            isFloatingServiceOperationActive(
                stopping = true,
                currentGeneration = 5L,
                operationGeneration = 4L
            )
        )
        assertFalse(
            isFloatingServiceOperationActive(
                stopping = false,
                currentGeneration = 5L,
                operationGeneration = 4L
            )
        )
    }

    @Test
    fun `enabled service remains running only while every runtime requirement is valid`() {
        val healthy = FloatingServiceHealthSnapshot(
            settingsEnabled = true,
            overlayPermissionGranted = true,
            membershipAllowed = true,
            characterPackUsable = true
        )

        assertTrue(
            shouldKeepFloatingServiceRunning(
                snapshot = healthy,
                runMode = FloatingServiceRunMode.ENABLED
            )
        )
        assertFalse(
            shouldKeepFloatingServiceRunning(
                snapshot = healthy.copy(settingsEnabled = false),
                runMode = FloatingServiceRunMode.ENABLED
            )
        )
        assertFalse(
            shouldKeepFloatingServiceRunning(
                snapshot = healthy.copy(overlayPermissionGranted = false),
                runMode = FloatingServiceRunMode.ENABLED
            )
        )
        assertFalse(
            shouldKeepFloatingServiceRunning(
                snapshot = healthy.copy(membershipAllowed = false),
                runMode = FloatingServiceRunMode.ENABLED
            )
        )
        assertFalse(
            shouldKeepFloatingServiceRunning(
                snapshot = healthy.copy(characterPackUsable = false),
                runMode = FloatingServiceRunMode.ENABLED
            )
        )
    }

    @Test
    fun `temporary preview may ignore disabled setting but not runtime requirements`() {
        val preview = FloatingServiceHealthSnapshot(
            settingsEnabled = false,
            overlayPermissionGranted = true,
            membershipAllowed = true,
            characterPackUsable = true
        )

        assertTrue(
            shouldKeepFloatingServiceRunning(
                snapshot = preview,
                runMode = FloatingServiceRunMode.TEMPORARY_PREVIEW
            )
        )
        assertFalse(
            shouldKeepFloatingServiceRunning(
                snapshot = preview.copy(overlayPermissionGranted = false),
                runMode = FloatingServiceRunMode.TEMPORARY_PREVIEW
            )
        )
        assertFalse(
            shouldKeepFloatingServiceRunning(
                snapshot = preview.copy(membershipAllowed = false),
                runMode = FloatingServiceRunMode.TEMPORARY_PREVIEW
            )
        )
        assertFalse(
            shouldKeepFloatingServiceRunning(
                snapshot = preview.copy(characterPackUsable = false),
                runMode = FloatingServiceRunMode.TEMPORARY_PREVIEW
            )
        )
    }

    @Test
    fun `floating started is reported only for a fully attached enabled session`() {
        assertTrue(
            shouldReportFloatingStarted(
                alreadyReported = false,
                reportInProgress = false,
                runMode = FloatingServiceRunMode.ENABLED,
                ballViewAttached = true,
                cardViewAttached = true
            )
        )
        assertFalse(
            shouldReportFloatingStarted(
                alreadyReported = false,
                reportInProgress = false,
                runMode = FloatingServiceRunMode.TEMPORARY_PREVIEW,
                ballViewAttached = true,
                cardViewAttached = true
            )
        )
        assertFalse(
            shouldReportFloatingStarted(
                alreadyReported = true,
                reportInProgress = false,
                runMode = FloatingServiceRunMode.ENABLED,
                ballViewAttached = true,
                cardViewAttached = true
            )
        )
        assertFalse(
            shouldReportFloatingStarted(
                alreadyReported = false,
                reportInProgress = true,
                runMode = FloatingServiceRunMode.ENABLED,
                ballViewAttached = true,
                cardViewAttached = true
            )
        )
        assertFalse(
            shouldReportFloatingStarted(
                alreadyReported = false,
                reportInProgress = false,
                runMode = FloatingServiceRunMode.ENABLED,
                ballViewAttached = true,
                cardViewAttached = false
            )
        )
    }

    @Test
    fun `new correlated activation replaces an older floating started report`() {
        assertTrue(
            shouldReplaceFloatingStartedReport(
                reportInProgress = true,
                activeRequestId = null,
                incomingRequestId = "request-new"
            )
        )
        assertTrue(
            shouldReplaceFloatingStartedReport(
                reportInProgress = true,
                activeRequestId = "request-old",
                incomingRequestId = "request-new"
            )
        )
        assertFalse(
            shouldReplaceFloatingStartedReport(
                reportInProgress = true,
                activeRequestId = "request-new",
                incomingRequestId = "request-new"
            )
        )
        assertFalse(
            shouldReplaceFloatingStartedReport(
                reportInProgress = true,
                activeRequestId = "request-active",
                incomingRequestId = null
            )
        )
    }

    @Test
    fun `appearance-only request stops a cold service without changing an active start`() {
        assertTrue(
            shouldStopColdAppearanceRequest(
                ballViewAttached = false,
                lifecycleOperationInProgress = false
            )
        )
        assertFalse(
            shouldStopColdAppearanceRequest(
                ballViewAttached = true,
                lifecycleOperationInProgress = false
            )
        )
        assertFalse(
            shouldStopColdAppearanceRequest(
                ballViewAttached = false,
                lifecycleOperationInProgress = true
            )
        )
    }

    @Test
    fun `character pack apply is appearance-only and cannot cold-start floating`() {
        assertTrue(
            isFloatingAppearanceOnlyAction(FloatingWordActions.ACTION_APPLY_BALL_APPEARANCE)
        )
        assertTrue(
            isFloatingAppearanceOnlyAction(FloatingWordActions.ACTION_APPLY_CHARACTER_PACK)
        )
        assertFalse(isFloatingAppearanceOnlyAction(FloatingWordActions.ACTION_START))
        assertFalse(isFloatingAppearanceOnlyAction(null))
    }

    @Test
    fun `management completion is acknowledged only after the requested pack loaded`() {
        assertTrue(
            shouldAcknowledgeManagementPackReload(
                requestedPackId = "green_pet",
                downloadRequestId = "request-1",
                selectedPackId = "green_pet",
                loadedPackId = SpritePackId("green_pet")
            )
        )
        assertFalse(
            shouldAcknowledgeManagementPackReload(
                requestedPackId = "green_pet",
                downloadRequestId = "request-1",
                selectedPackId = "blue_pet",
                loadedPackId = SpritePackId("green_pet")
            )
        )
        assertFalse(
            shouldAcknowledgeManagementPackReload(
                requestedPackId = "green_pet",
                downloadRequestId = "request-1",
                selectedPackId = "green_pet",
                loadedPackId = null
            )
        )
        assertFalse(
            shouldAcknowledgeManagementPackReload(
                requestedPackId = "green_pet",
                downloadRequestId = null,
                selectedPackId = "green_pet",
                loadedPackId = SpritePackId("green_pet")
            )
        )
    }

    @Test
    fun `surface failure disables activation only when permission is invalid`() {
        assertTrue(
            shouldDisableActivationAfterSurfaceFailure(
                failure = SecurityException("permission revoked"),
                overlayPermissionGranted = true
            )
        )
        assertTrue(
            shouldDisableActivationAfterSurfaceFailure(
                failure = IllegalStateException("bad overlay token"),
                overlayPermissionGranted = false
            )
        )
        assertFalse(
            shouldDisableActivationAfterSurfaceFailure(
                failure = IllegalStateException("transient view failure"),
                overlayPermissionGranted = true
            )
        )
    }
}
