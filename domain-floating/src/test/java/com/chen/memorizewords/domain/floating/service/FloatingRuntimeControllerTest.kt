package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.CharacterPackResolution
import com.chen.memorizewords.domain.floating.model.FloatingDevicePreferences
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEligibility
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeError
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEvent
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSource
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.repository.FloatingDevicePreferencesRepository
import com.chen.memorizewords.domain.floating.repository.FloatingRuntimeSessionRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class FloatingRuntimeControllerTest {

    @Test
    fun `completed runtime download recovers legacy installing once and dispatches one start`() = runBlocking {
        val session = runtimeSession(phase = FloatingRuntimePhase.INSTALLING, revision = 7L)
        val repository = FakeRuntimeRepository(session)
        val characterPacks = FakeCharacterPackRepository(
            downloads = mapOf(
                PACK_ID to CharacterPackDownloadState(
                    packId = PACK_ID,
                    status = CharacterPackDownloadStatus.COMPLETED,
                    runtimeSessionId = session.sessionId,
                    runtimeRevision = 2L
                )
            ),
            usablePackIds = setOf(PACK_ID)
        )
        val gateway = FakeGateway()
        val controller = controller(repository, characterPacks, gateway)

        // State publication itself has no side effect in the controller. The main-process
        // coordinator explicitly decides when a foreground reconciliation may dispatch.
        assertEquals(0, gateway.startedSessions.size)

        controller.reconcileForeground()
        controller.reconcileForeground()

        val current = repository.current
        assertEquals(FloatingRuntimePhase.STARTING, current?.phase)
        assertEquals(1, gateway.startedSessions.size)
        assertEquals(session.sessionId, gateway.startedSessions.single().sessionId)
        assertTrue((current?.startDeadlineAtMs ?: 0L) > 0L)
    }

    @Test
    fun `legacy installing with failed runtime download becomes explicit failure`() = runBlocking {
        val session = runtimeSession(phase = FloatingRuntimePhase.INSTALLING, revision = 5L)
        val repository = FakeRuntimeRepository(session)
        val characterPacks = FakeCharacterPackRepository(
            downloads = mapOf(
                PACK_ID to CharacterPackDownloadState(
                    packId = PACK_ID,
                    status = CharacterPackDownloadStatus.FAILED,
                    runtimeSessionId = session.sessionId
                )
            )
        )
        val gateway = FakeGateway()

        controller(repository, characterPacks, gateway).reconcileForeground()

        assertEquals(FloatingRuntimePhase.FAILED, repository.current?.phase)
        assertEquals(FloatingRuntimeError.DOWNLOAD_FAILED, repository.current?.error)
        assertEquals(0, gateway.startedSessions.size)
    }

    @Test
    fun `service dispatch failure fails the ready session instead of leaving it starting`() = runBlocking {
        val session = runtimeSession(phase = FloatingRuntimePhase.READY_TO_START, revision = 9L)
        val repository = FakeRuntimeRepository(session)
        val gateway = FakeGateway(startResult = Result.failure(IllegalStateException("rejected")))

        controller(
            repository = repository,
            characterPacks = FakeCharacterPackRepository(usablePackIds = setOf(PACK_ID)),
            gateway = gateway
        ).reconcileForeground()

        assertEquals(FloatingRuntimePhase.FAILED, repository.current?.phase)
        assertEquals(FloatingRuntimeError.FOREGROUND_SERVICE_REJECTED, repository.current?.error)
        assertEquals(1, gateway.startedSessions.size)
    }

    @Test
    fun `a renderer confirmation makes later reconciliation ignore an old start revision`() = runBlocking {
        val session = runtimeSession(phase = FloatingRuntimePhase.READY_TO_START, revision = 3L)
        val repository = FakeRuntimeRepository(session)
        val gateway = FakeGateway()
        val reporter = FloatingRuntimeReporter(repository)
        val controller = controller(repository, FakeCharacterPackRepository(usablePackIds = setOf(PACK_ID)), gateway)

        controller.reconcileForeground()
        val starting = checkNotNull(repository.current)
        reporter.transition(starting.sessionId, starting.revision, FloatingRuntimeEvent.RendererReady)
        controller.reconcileForeground()

        assertEquals(FloatingRuntimePhase.RUNNING, repository.current?.phase)
        assertEquals(1, gateway.startedSessions.size)
    }

    private fun controller(
        repository: FakeRuntimeRepository,
        characterPacks: FakeCharacterPackRepository,
        gateway: FakeGateway
    ): FloatingRuntimeController = FloatingRuntimeController(
        runtimeRepository = repository,
        reporter = FloatingRuntimeReporter(repository),
        devicePreferencesRepository = FakeDevicePreferencesRepository(),
        settingsRepository = FakeSettingsRepository(),
        characterPackRepository = characterPacks,
        eligibilityChecker = FloatingRuntimeEligibilityChecker { FloatingRuntimeEligibility.ELIGIBLE },
        serviceGateway = gateway
    )

    private fun runtimeSession(
        phase: FloatingRuntimePhase,
        revision: Long
    ) = FloatingRuntimeSession(
        sessionId = SESSION_ID,
        revision = revision,
        phase = phase,
        source = FloatingRuntimeSource.HOME,
        targetPackId = PACK_ID,
        progress = 100,
        createdAtMs = 1L,
        updatedAtMs = 1L
    )

    private class FakeRuntimeRepository(initial: FloatingRuntimeSession?) :
        FloatingRuntimeSessionRepository {
        private val sessions = MutableStateFlow(initial)
        val current: FloatingRuntimeSession? get() = sessions.value

        override fun observe(): Flow<FloatingRuntimeSnapshot> = sessions.map { session ->
            FloatingRuntimeSnapshot(session)
        }

        override suspend fun getSnapshot(): FloatingRuntimeSnapshot = FloatingRuntimeSnapshot(sessions.value)

        override suspend fun create(session: FloatingRuntimeSession): FloatingRuntimeSession {
            sessions.value = session
            return session
        }

        override suspend fun compareAndSet(
            sessionId: String,
            expectedRevision: Long,
            updated: FloatingRuntimeSession?
        ): FloatingRuntimeSession? {
            val current = sessions.value
            if (current?.sessionId != sessionId || current.revision != expectedRevision) return null
            sessions.value = updated
            return updated
        }

        override suspend fun clear() {
            sessions.value = null
        }
    }

    private class FakeCharacterPackRepository(
        downloads: Map<String, CharacterPackDownloadState> = emptyMap(),
        usablePackIds: Set<String> = emptySet()
    ) : CharacterPackRepository {
        private val catalog = MutableStateFlow<List<CharacterPackCatalogItem>>(emptyList())
        private val installed = MutableStateFlow(emptyMap<String, com.chen.memorizewords.domain.floating.model.InstalledCharacterPack>())
        private val downloadStates = MutableStateFlow(downloads)
        private val usable = usablePackIds.toMutableSet()

        override fun observeCatalog(): Flow<List<CharacterPackCatalogItem>> = catalog

        override fun observeInstalled(): Flow<Map<String, com.chen.memorizewords.domain.floating.model.InstalledCharacterPack>> = installed

        override fun observeDownloads(): Flow<Map<String, CharacterPackDownloadState>> = downloadStates

        override suspend fun refreshCatalog(): Result<Unit> = Result.success(Unit)

        override suspend fun resolveAppliedCharacterPack(): Result<CharacterPackResolution> =
            Result.failure(IllegalStateException("not used by this test"))

        override suspend fun applyCharacterPack(packId: String): Result<Unit> = Result.success(Unit)

        override suspend fun startDownload(
            item: CharacterPackCatalogItem,
            selectAfterInstall: Boolean,
            runtimeSessionId: String?,
            runtimeRevision: Long?
        ): Result<Unit> = Result.success(Unit)

        override suspend fun acknowledgeManagementDownloadCompletion(
            packId: String,
            downloadRequestId: String
        ): Boolean = false

        override suspend fun cancelDownload(packId: String) = Unit

        override suspend fun deleteInstalled(packId: String) = Unit

        override suspend fun getInstalled(
            packId: String
        ): com.chen.memorizewords.domain.floating.model.InstalledCharacterPack? = installed.value[packId]

        override suspend fun isInstalledUsable(packId: String): Boolean = packId in usable
    }

    private class FakeDevicePreferencesRepository : FloatingDevicePreferencesRepository {
        private val state = MutableStateFlow(FloatingDevicePreferences())

        override fun observe(): Flow<FloatingDevicePreferences> = state

        override suspend fun get(): FloatingDevicePreferences = state.value

        override suspend fun update(
            transform: (FloatingDevicePreferences) -> FloatingDevicePreferences
        ): FloatingDevicePreferences = transform(state.value).also { state.value = it }

        override suspend fun clear() {
            state.value = FloatingDevicePreferences()
        }
    }

    private class FakeSettingsRepository : FloatingWordSettingsRepository {
        private val state = MutableStateFlow(FloatingWordSettings())

        override fun observeSettings(): Flow<FloatingWordSettings> = state

        override suspend fun getSettings(): FloatingWordSettings = state.value

        override suspend fun saveSettings(settings: FloatingWordSettings) {
            state.value = settings
        }
    }

    private class FakeGateway(
        private val startResult: Result<Unit> = Result.success(Unit)
    ) : FloatingRuntimeServiceGateway {
        val startedSessions = mutableListOf<FloatingRuntimeSession>()

        override fun canDrawOverlays(): Boolean = true

        override fun dispatchStart(session: FloatingRuntimeSession): Result<Unit> {
            startedSessions += session
            return startResult
        }

        override fun dispatchStop(session: FloatingRuntimeSession?): Result<Unit> = Result.success(Unit)

        override fun dispatchReconfigure(session: FloatingRuntimeSession): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val SESSION_ID = "session"
        const val PACK_ID = "green_pet"
    }
}
