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
    fun `non-starting request stops a cold service without changing an active start`() {
        assertTrue(
            shouldStopColdNonStartingRequest(
                ballViewAttached = false,
                lifecycleOperationInProgress = false
            )
        )
        assertFalse(
            shouldStopColdNonStartingRequest(
                ballViewAttached = true,
                lifecycleOperationInProgress = false
            )
        )
        assertFalse(
            shouldStopColdNonStartingRequest(
                ballViewAttached = false,
                lifecycleOperationInProgress = true
            )
        )
    }

    @Test
    fun `character pack apply is non-starting and cannot cold-start floating`() {
        assertTrue(
            isFloatingNonStartingAction(FloatingWordActions.ACTION_APPLY_CHARACTER_PACK)
        )
        assertFalse(isFloatingNonStartingAction(FloatingWordActions.ACTION_START))
        assertFalse(isFloatingNonStartingAction(null))
    }

    @Test
    fun `stale word sequence is not reused when opening a card`() {
        assertTrue(
            canReuseCurrentFloatingWord(
                hasCurrentWord = true,
                wordSequenceRefreshPending = false,
                loadedSequenceMatches = true
            )
        )
        assertFalse(
            canReuseCurrentFloatingWord(
                hasCurrentWord = true,
                wordSequenceRefreshPending = true,
                loadedSequenceMatches = true
            )
        )
        assertFalse(
            canReuseCurrentFloatingWord(
                hasCurrentWord = true,
                wordSequenceRefreshPending = false,
                loadedSequenceMatches = false
            )
        )
    }

    @Test
    fun `card settings changes defer work while the card is hidden`() {
        assertEquals(
            FloatingCardSettingsAction.NONE,
            resolveFloatingCardSettingsAction(
                cardVisible = false,
                hasCurrentWord = true,
                wordSequenceChanged = true,
                fieldConfigsChanged = true
            )
        )
    }

    @Test
    fun `visible card keeps its word for a sequence change and redraws for field changes`() {
        assertEquals(
            FloatingCardSettingsAction.NONE,
            resolveFloatingCardSettingsAction(
                cardVisible = true,
                hasCurrentWord = true,
                wordSequenceChanged = true,
                fieldConfigsChanged = false
            )
        )
        assertEquals(
            FloatingCardSettingsAction.RENDER_CURRENT,
            resolveFloatingCardSettingsAction(
                cardVisible = true,
                hasCurrentWord = true,
                wordSequenceChanged = false,
                fieldConfigsChanged = true
            )
        )
        assertEquals(
            FloatingCardSettingsAction.LOAD_NEXT,
            resolveFloatingCardSettingsAction(
                cardVisible = true,
                hasCurrentWord = false,
                wordSequenceChanged = true,
                fieldConfigsChanged = false
            )
        )
    }

    @Test
    fun `loaded character pack is reused only when its revision and renderer are ready`() {
        assertFalse(
            shouldReloadFloatingCharacterPack(
                revisionMatches = true,
                packReady = true
            )
        )
        assertTrue(
            shouldReloadFloatingCharacterPack(
                revisionMatches = false,
                packReady = true
            )
        )
        assertTrue(
            shouldReloadFloatingCharacterPack(
                revisionMatches = true,
                packReady = false
            )
        )
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
