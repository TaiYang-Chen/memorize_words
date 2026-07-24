package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.CharacterPackResolution
import com.chen.memorizewords.domain.floating.model.FloatingActivationContinuation
import com.chen.memorizewords.domain.floating.model.FloatingActivationEligibility
import com.chen.memorizewords.domain.floating.model.FloatingActivationPhase
import com.chen.memorizewords.domain.floating.model.FloatingActivationPreparation
import com.chen.memorizewords.domain.floating.model.FloatingActivationSource
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.InstalledCharacterPack
import com.chen.memorizewords.domain.floating.model.PendingFloatingActivation
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.repository.FloatingActivationStateRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FloatingActivationCoordinatorTest {
    @Test
    fun `server resolved character is the only initial download target`() = runBlocking {
        val applied = pack("pack-b")
        val fallback = installed("pack-a")
        val packs = FakeCharacterPackRepository(
            catalog = listOf(pack("pack-a"), applied),
            installed = mapOf(fallback.packId to fallback),
            resolveResult = Result.success(CharacterPackResolution.Resolved(applied))
        )
        val settings = FakeSettingsRepository()
        val pending = FakeActivationStateRepository()
        val coordinator = coordinator(packs, settings, pending)

        assertEquals(
            FloatingActivationPreparation.NEEDS_DOWNLOAD,
            coordinator.prepareActivation(FloatingActivationSource.HOME)
        )

        assertEquals(1, packs.resolveCalls)
        assertEquals(applied.packId, pending.current?.targetPackId)
        assertEquals(applied.packId, settings.current.selectedCharacterPackId)
        assertEquals(0, packs.usabilityChecksFor(fallback.packId))
    }

    @Test
    fun `server resolved installed character continues through permission and activation`() = runBlocking {
        val applied = pack("pack-b")
        val local = installed(applied.packId)
        val packs = FakeCharacterPackRepository(
            catalog = listOf(applied),
            installed = mapOf(local.packId to local),
            resolveResult = Result.success(CharacterPackResolution.Resolved(applied))
        )
        val settings = FakeSettingsRepository()
        val pending = FakeActivationStateRepository()
        val coordinator = coordinator(packs, settings, pending)

        assertEquals(
            FloatingActivationPreparation.READY_FOR_PERMISSION,
            coordinator.prepareActivation(FloatingActivationSource.HOME)
        )
        val requestId = pending.current?.requestId
        assertNotNull(requestId)
        assertEquals(
            FloatingActivationContinuation.REQUIRES_PERMISSION,
            coordinator.continueActivation(canDrawOverlays = false, expectedRequestId = requestId)
        )
        assertFalse(settings.current.enabled)

        assertEquals(
            FloatingActivationContinuation.ACTIVATED,
            coordinator.continueActivation(canDrawOverlays = true, expectedRequestId = requestId)
        )
        assertTrue(settings.current.enabled)
        assertTrue(settings.current.autoStartOnAppLaunch)
        assertEquals(applied.packId, settings.current.selectedCharacterPackId)
        assertNotNull(pending.current?.committedAtMs)

        assertTrue(
            coordinator.completeActivationOnFloatingStarted(
                packId = applied.packId,
                expectedRequestId = requestId
            )
        )
        assertNull(pending.current)
    }

    @Test
    fun `selection required clears activation and never switches to another installed character`() = runBlocking {
        val previous = PendingFloatingActivation(
            requestId = "8bdf5f8b-6f02-4d5a-843a-297df9dbb403",
            targetPackId = "obsolete-pack",
            source = FloatingActivationSource.HOME,
            createdAtMs = 1L
        )
        val fallback = installed("locally-available")
        val packs = FakeCharacterPackRepository(
            installed = mapOf(fallback.packId to fallback),
            resolveResult = Result.success(CharacterPackResolution.SelectionRequired)
        )
        val settings = FakeSettingsRepository(
            FloatingWordSettings(
                enabled = true,
                autoStartOnAppLaunch = true,
                selectedCharacterPackId = "obsolete-pack"
            )
        )
        val pending = FakeActivationStateRepository(previous)
        val reporter = RecordingEventReporter()
        val coordinator = FloatingActivationCoordinator(
            packs,
            settings,
            pending,
            reporter,
            FloatingActivationEligibilityChecker { FloatingActivationEligibility.ELIGIBLE }
        )

        assertEquals(
            FloatingActivationPreparation.SELECTION_REQUIRED,
            coordinator.prepareActivation(FloatingActivationSource.HOME)
        )

        assertNull(pending.current)
        assertFalse(settings.current.enabled)
        assertFalse(settings.current.autoStartOnAppLaunch)
        assertNull(settings.current.selectedCharacterPackId)
        assertEquals(0, packs.usabilityChecksFor(fallback.packId))
        assertTrue(
            reporter.events.any {
                it.first == FloatingActivationEvent.ACTIVATION_CANCELLED &&
                    it.second["reason"] == "SELECTION_REQUIRED"
            }
        )
    }

    @Test
    fun `resolve failure allows only the currently cached applied pack offline`() = runBlocking {
        val applied = installed("pack-a")
        val fallback = installed("pack-b")
        val packs = FakeCharacterPackRepository(
            installed = mapOf(applied.packId to applied, fallback.packId to fallback),
            resolveResult = Result.failure(IllegalStateException("network unavailable"))
        )
        val settings = FakeSettingsRepository(
            FloatingWordSettings(selectedCharacterPackId = applied.packId)
        )
        val pending = FakeActivationStateRepository()
        val coordinator = coordinator(packs, settings, pending)

        assertEquals(
            FloatingActivationPreparation.READY_FOR_PERMISSION,
            coordinator.prepareActivation(FloatingActivationSource.HOME)
        )

        assertEquals(applied.packId, pending.current?.targetPackId)
        assertEquals(1, packs.usabilityChecksFor(applied.packId))
        assertEquals(0, packs.usabilityChecksFor(fallback.packId))
    }

    @Test
    fun `resolve failure does not use another installed character when cached pack is missing`() = runBlocking {
        val fallback = installed("pack-b")
        val packs = FakeCharacterPackRepository(
            installed = mapOf(fallback.packId to fallback),
            resolveResult = Result.failure(IllegalStateException("network unavailable"))
        )
        val settings = FakeSettingsRepository(
            FloatingWordSettings(
                enabled = true,
                autoStartOnAppLaunch = true,
                selectedCharacterPackId = "missing-pack"
            )
        )
        val pending = FakeActivationStateRepository()
        val coordinator = coordinator(packs, settings, pending)

        assertEquals(
            FloatingActivationPreparation.NO_CHARACTER_AVAILABLE,
            coordinator.prepareActivation(FloatingActivationSource.HOME)
        )

        assertNotNull(pending.current)
        assertNull(pending.current?.targetPackId)
        assertEquals(FloatingActivationSource.HOME, pending.current?.source)
        assertEquals(
            FloatingActivationPhase.NEEDS_DOWNLOAD,
            coordinator.observeSnapshot().first().phase
        )
        assertFalse(settings.current.enabled)
        assertEquals("missing-pack", settings.current.selectedCharacterPackId)
        assertEquals(0, packs.usabilityChecksFor(fallback.packId))
    }

    @Test
    fun `resolved download uses pending target and keeps request id`() = runBlocking {
        val target = pack("pack-b")
        val reporter = RecordingEventReporter()
        val packs = FakeCharacterPackRepository(
            catalog = listOf(pack("pack-a"), target),
            resolveResult = Result.success(CharacterPackResolution.Resolved(target))
        )
        val settings = FakeSettingsRepository()
        val pending = FakeActivationStateRepository()
        val coordinator = FloatingActivationCoordinator(
            packs,
            settings,
            pending,
            reporter,
            FloatingActivationEligibilityChecker { FloatingActivationEligibility.ELIGIBLE }
        )

        coordinator.prepareActivation(FloatingActivationSource.HOME)
        val requestId = pending.current?.requestId
        assertNotNull(requestId)

        val result = coordinator.startResolvedCharacterDownload().getOrThrow()

        assertEquals(requestId, result.requestId)
        assertEquals(target.packId, packs.downloadRequests.single().first)
        assertEquals(requestId, packs.downloadRequests.single().third)
        assertTrue(
            reporter.events.any {
                it.first == FloatingActivationEvent.RESOLVED_CHARACTER_DOWNLOAD_SELECTED
            }
        )
    }

    @Test
    fun `resolved download does not fall back when target is absent from catalog`() = runBlocking {
        val target = pack("pack-b")
        val fallback = pack("pack-a")
        val packs = FakeCharacterPackRepository(
            catalog = listOf(fallback),
            resolveResult = Result.success(CharacterPackResolution.Resolved(target))
        )
        val settings = FakeSettingsRepository()
        val pending = FakeActivationStateRepository()
        val coordinator = coordinator(packs, settings, pending)

        coordinator.prepareActivation(FloatingActivationSource.HOME)
        val result = coordinator.startResolvedCharacterDownload()

        assertTrue(result.isFailure)
        assertTrue(packs.downloadRequests.isEmpty())
        assertEquals(target.packId, pending.current?.targetPackId)
    }

    @Test
    fun `new activation request invalidates earlier request and cannot be stolen by old download`() = runBlocking {
        val first = pack("pack-a")
        val second = pack("pack-b")
        val packs = FakeCharacterPackRepository(catalog = listOf(first, second))
        val settings = FakeSettingsRepository()
        val pending = FakeActivationStateRepository()
        val coordinator = coordinator(packs, settings, pending)

        val firstRequest = coordinator.startActivationDownload(
            first,
            FloatingActivationSource.CHARACTER_SELECTION
        ).getOrThrow()
        val secondRequest = coordinator.startActivationDownload(
            second,
            FloatingActivationSource.CHARACTER_SELECTION
        ).getOrThrow()

        assertNotEquals(firstRequest.requestId, secondRequest.requestId)
        assertEquals(secondRequest, pending.current)
        assertEquals(second.packId, settings.current.selectedCharacterPackId)
        assertEquals(secondRequest.requestId, packs.downloadRequests.last().third)
    }

    @Test
    fun `reconcile disables missing selected pack instead of selecting fallback`() = runBlocking {
        val fallback = installed("pack-b")
        val settings = FakeSettingsRepository(
            FloatingWordSettings(
                enabled = true,
                autoStartOnAppLaunch = true,
                selectedCharacterPackId = "missing-pack"
            )
        )
        val coordinator = coordinator(
            FakeCharacterPackRepository(installed = mapOf(fallback.packId to fallback)),
            settings,
            FakeActivationStateRepository()
        )

        coordinator.reconcileEnabledState()

        assertFalse(settings.current.enabled)
        assertFalse(settings.current.autoStartOnAppLaunch)
        assertEquals("missing-pack", settings.current.selectedCharacterPackId)
    }

    @Test
    fun `effective availability and service startup checks never scan fallback packs`() = runBlocking {
        val fallback = installed("pack-b")
        val packs = FakeCharacterPackRepository(installed = mapOf(fallback.packId to fallback))
        val settings = FakeSettingsRepository(
            FloatingWordSettings(
                enabled = true,
                selectedCharacterPackId = "missing-pack"
            )
        )
        val coordinator = coordinator(packs, settings, FakeActivationStateRepository())

        assertFalse(coordinator.canStartCurrent())
        assertFalse(coordinator.hasUsablePack())
        assertFalse(coordinator.isCurrentPackUsable())
        assertEquals(0, packs.usabilityChecksFor(fallback.packId))
    }

    @Test
    fun `current pack observation handles missing and damaged selection`() = runBlocking {
        val damaged = installed("pack-a")
        val packs = FakeCharacterPackRepository(
            installed = mapOf(damaged.packId to damaged),
            unusablePackIds = setOf(damaged.packId)
        )
        val settings = FakeSettingsRepository(FloatingWordSettings(selectedCharacterPackId = damaged.packId))
        val coordinator = coordinator(packs, settings, FakeActivationStateRepository())

        assertFalse(coordinator.observeCurrentPackInstalled().first())

        settings.saveSettings(FloatingWordSettings(selectedCharacterPackId = null))
        assertFalse(coordinator.observeCurrentPackInstalled().first())
    }

    @Test
    fun `ineligible activation clears state before resolve`() = runBlocking {
        val packs = FakeCharacterPackRepository(
            resolveResult = Result.success(CharacterPackResolution.Resolved(pack("pack-a")))
        )
        val pending = FakeActivationStateRepository(
            PendingFloatingActivation(
                requestId = "c8db8ad5-1de8-42a6-9b74-b5f4ed0e0a1c",
                targetPackId = "pack-a",
                source = FloatingActivationSource.HOME,
                createdAtMs = 1L
            )
        )
        val settings = FakeSettingsRepository(FloatingWordSettings(enabled = true))
        val coordinator = coordinator(
            packs,
            settings,
            pending,
            eligibility = FloatingActivationEligibility.MEMBERSHIP_REQUIRED
        )

        assertEquals(
            FloatingActivationPreparation.INELIGIBLE,
            coordinator.prepareActivation(FloatingActivationSource.HOME)
        )

        assertEquals(0, packs.resolveCalls)
        assertNull(pending.current)
        assertFalse(settings.current.enabled)
    }

    @Test
    fun `permission denial only clears matching request`() = runBlocking {
        val current = PendingFloatingActivation(
            requestId = "b1bff255-e0f6-4e11-90e5-a759d02000f1",
            targetPackId = "pack-a",
            source = FloatingActivationSource.HOME,
            createdAtMs = 1L
        )
        val pending = FakeActivationStateRepository(current)
        val settings = FakeSettingsRepository(FloatingWordSettings(enabled = true))
        val coordinator = coordinator(FakeCharacterPackRepository(), settings, pending)

        assertFalse(coordinator.denyOverlayPermission("another-request"))
        assertEquals(current, pending.current)

        assertTrue(coordinator.denyOverlayPermission(current.requestId))
        assertNull(pending.current)
        assertFalse(settings.current.enabled)
    }

    @Test
    fun `stale page cannot continue or cancel newer request`() = runBlocking {
        val local = installed("pack-a")
        val current = PendingFloatingActivation(
            requestId = "11bff255-e0f6-4e11-90e5-a759d02000f1",
            targetPackId = local.packId,
            source = FloatingActivationSource.CHARACTER_SELECTION,
            createdAtMs = 1L
        )
        val pending = FakeActivationStateRepository(current)
        val settings = FakeSettingsRepository(
            FloatingWordSettings(
                enabled = true,
                autoStartOnAppLaunch = true,
                selectedCharacterPackId = local.packId
            )
        )
        val coordinator = coordinator(
            FakeCharacterPackRepository(installed = mapOf(local.packId to local)),
            settings,
            pending
        )

        assertEquals(
            FloatingActivationContinuation.STALE_REQUEST,
            coordinator.continueActivation(true, expectedRequestId = "old-request")
        )
        assertFalse(coordinator.cancelPending("old-request"))

        assertEquals(current, pending.current)
        assertTrue(settings.current.enabled)
    }

    @Test
    fun `snapshot ignores download progress owned by previous activation request`() = runBlocking {
        val target = pack("pack-a")
        val pending = PendingFloatingActivation(
            requestId = "d3f2d53e-b0a7-4ab4-a0d5-5fcb1b88f505",
            targetPackId = target.packId,
            source = FloatingActivationSource.HOME,
            createdAtMs = 1L
        )
        val packs = FakeCharacterPackRepository(
            catalog = listOf(target),
            downloads = mapOf(
                target.packId to CharacterPackDownloadState(
                    packId = target.packId,
                    status = CharacterPackDownloadStatus.DOWNLOADING,
                    activationRequestId = "f7d0962d-b20e-43dc-b2fa-1997f28c4f5a"
                )
            )
        )
        val coordinator = coordinator(
            packs,
            FakeSettingsRepository(),
            FakeActivationStateRepository(pending)
        )

        assertEquals(FloatingActivationPhase.NEEDS_DOWNLOAD, coordinator.observeSnapshot().first().phase)
    }

    @Test
    fun `failed selection download does not retain orphan pending request`() = runBlocking {
        val target = pack("pack-a")
        val pending = FakeActivationStateRepository()
        val coordinator = coordinator(
            FakeCharacterPackRepository(
                catalog = listOf(target),
                startDownloadResult = Result.failure(IllegalStateException("enqueue failed"))
            ),
            FakeSettingsRepository(),
            pending
        )

        assertTrue(
            coordinator.startActivationDownload(
                target,
                FloatingActivationSource.CHARACTER_SELECTION
            ).isFailure
        )
        assertNull(pending.current)
    }

    private fun coordinator(
        packs: FakeCharacterPackRepository,
        settings: FakeSettingsRepository,
        pending: FakeActivationStateRepository,
        eligibility: FloatingActivationEligibility = FloatingActivationEligibility.ELIGIBLE
    ) = FloatingActivationCoordinator(
        packs,
        settings,
        pending,
        NoOpEventReporter,
        FloatingActivationEligibilityChecker { eligibility }
    )

    private object NoOpEventReporter : FloatingActivationEventReporter {
        override fun report(
            event: FloatingActivationEvent,
            attributes: Map<String, String>
        ) = Unit
    }

    private class RecordingEventReporter : FloatingActivationEventReporter {
        val events = mutableListOf<Pair<FloatingActivationEvent, Map<String, String>>>()

        override fun report(
            event: FloatingActivationEvent,
            attributes: Map<String, String>
        ) {
            events += event to attributes
        }
    }

    private fun pack(id: String) = CharacterPackCatalogItem(
        packId = id,
        packVersion = 1,
        displayName = id,
        previewUrl = "https://example.com/$id.png",
        packageUrl = "https://example.com/$id.zip",
        packageSha256 = "a".repeat(64),
        packageSizeBytes = 100,
        manifestSchemaVersion = 2,
        updatedAtMs = 1
    )

    private fun installed(id: String) = InstalledCharacterPack(
        packId = id,
        packVersion = 1,
        displayName = id,
        installedDirectory = id,
        installedAtMs = 1L
    )

    private class FakeCharacterPackRepository(
        catalog: List<CharacterPackCatalogItem> = emptyList(),
        installed: Map<String, InstalledCharacterPack> = emptyMap(),
        private val unusablePackIds: Set<String> = emptySet(),
        downloads: Map<String, CharacterPackDownloadState> = emptyMap(),
        var resolveResult: Result<CharacterPackResolution> =
            Result.failure(IllegalStateException("resolve not configured")),
        private val startDownloadResult: Result<Unit> = Result.success(Unit)
    ) : CharacterPackRepository {
        private val catalogState = MutableStateFlow(catalog)
        private val installedState = MutableStateFlow(installed)
        private val downloadState = MutableStateFlow(downloads)
        val downloadRequests = mutableListOf<Triple<String, Boolean, String?>>()
        private val packUsabilityChecks = mutableMapOf<String, Int>()
        var resolveCalls: Int = 0
            private set

        override fun observeCatalog(): Flow<List<CharacterPackCatalogItem>> = catalogState
        override fun observeInstalled(): Flow<Map<String, InstalledCharacterPack>> = installedState
        override fun observeDownloads(): Flow<Map<String, CharacterPackDownloadState>> = downloadState
        override suspend fun refreshCatalog(): Result<Unit> = Result.success(Unit)
        override suspend fun resolveAppliedCharacterPack(): Result<CharacterPackResolution> {
            resolveCalls += 1
            return resolveResult
        }
        override suspend fun applyCharacterPack(packId: String): Result<Unit> = Result.success(Unit)
        override suspend fun startDownload(
            item: CharacterPackCatalogItem,
            selectAfterInstall: Boolean,
            activationRequestId: String?
        ): Result<Unit> {
            downloadRequests += Triple(item.packId, selectAfterInstall, activationRequestId)
            return startDownloadResult
        }
        override suspend fun acknowledgeManagementDownloadCompletion(
            packId: String,
            downloadRequestId: String
        ): Boolean = false
        override suspend fun cancelDownload(packId: String) = Unit
        override suspend fun deleteInstalled(packId: String) {
            installedState.value = installedState.value - packId
        }
        override suspend fun getInstalled(packId: String): InstalledCharacterPack? =
            installedState.value[packId]
        override suspend fun isInstalledUsable(packId: String): Boolean {
            packUsabilityChecks[packId] = (packUsabilityChecks[packId] ?: 0) + 1
            return installedState.value.containsKey(packId) && packId !in unusablePackIds
        }

        fun usabilityChecksFor(packId: String): Int = packUsabilityChecks[packId] ?: 0
    }

    private class FakeSettingsRepository(
        initial: FloatingWordSettings = FloatingWordSettings()
    ) : FloatingWordSettingsRepository {
        private val state = MutableStateFlow(initial)
        val current: FloatingWordSettings get() = state.value

        override fun observeSettings(): Flow<FloatingWordSettings> = state
        override suspend fun getSettings(): FloatingWordSettings = state.value
        override suspend fun saveSettings(settings: FloatingWordSettings) {
            state.value = settings
        }
        override suspend fun updateBallPosition(
            x: Int,
            y: Int,
            dockState: FloatingDockState?
        ) {
            state.value = state.value.copy(
                floatingBallX = x,
                floatingBallY = y,
                dockState = dockState
            )
        }
    }

    private class FakeActivationStateRepository(
        initial: PendingFloatingActivation? = null
    ) : FloatingActivationStateRepository {
        private val state = MutableStateFlow(initial)
        val current: PendingFloatingActivation? get() = state.value

        override fun observePending(): Flow<PendingFloatingActivation?> = state
        override suspend fun getPending(): PendingFloatingActivation? = state.value
        override suspend fun savePending(pending: PendingFloatingActivation) {
            state.value = pending
        }
        override suspend fun clearPending(requestId: String?): Boolean {
            if (requestId != null && requestId != state.value?.requestId) return false
            state.value = null
            return true
        }
    }
}
