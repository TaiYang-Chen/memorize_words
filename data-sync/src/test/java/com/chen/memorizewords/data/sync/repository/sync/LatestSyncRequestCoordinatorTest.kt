package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncDeliveryMode
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncState
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.FavoriteSyncRequest
import com.chen.memorizewords.data.wordbook.remoteapi.api.datasync.UserDataSyncApiService
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.sync.FailureQueueEventType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import retrofit2.Invocation

class LatestSyncRequestCoordinatorTest {
    @Test
    fun `successful normal request does not access failed event store`() = runBlocking {
        val access = FakeLatestEventAccess()
        val coordinator = coordinator(access)

        val result = coordinator.executeNormal("favorite:123") { NetworkResult.Success(Unit) }

        assertTrue(result is NetworkResult.Success)
        assertEquals(0, access.isClaimedCalls)
    }

    @Test
    fun `failed request after success clears latest tombstone`() = runBlocking {
        val access = FakeLatestEventAccess(claimed = true)
        val coordinator = coordinator(access)
        var replayed = false

        coordinator.executeNormal("favorite:123") { NetworkResult.Success(Unit) }
        coordinator.executeNormal("favorite:123") {
            NetworkResult.Failure.NetworkError(java.io.IOException("offline"))
        }
        val outcome = coordinator.replay(event()) {
            replayed = true
            ReplayOutcome.Success
        }

        assertEquals(ReplayOutcome.Success, outcome)
        assertTrue(replayed)
    }

    @Test
    fun `replay skips event that is no longer claimed`() = runBlocking {
        val access = FakeLatestEventAccess(claimed = false)
        val coordinator = coordinator(access)
        var replayed = false

        val outcome = coordinator.replay(event()) {
            replayed = true
            ReplayOutcome.Success
        }

        assertEquals(ReplayOutcome.Success, outcome)
        assertFalse(replayed)
    }

    @Test
    fun `normal latest request waits for replay of the same key`() = runBlocking {
        val access = FakeLatestEventAccess(claimed = true)
        val identity = LatestSyncIdentity(dedupeKey = "favorite:123")
        val coordinator = coordinator(access)
        val replayStarted = CompletableDeferred<Unit>()
        val allowReplayToFinish = CompletableDeferred<Unit>()

        val replay = async {
            coordinator.replay(event()) {
                replayStarted.complete(Unit)
                allowReplayToFinish.await()
                ReplayOutcome.Success
            }
        }
        replayStarted.await()
        val normal = async {
            coordinator.executeNormal(identity.dedupeKey) { NetworkResult.Success(Unit) }
        }
        yield()

        assertFalse(normal.isCompleted)
        allowReplayToFinish.complete(Unit)
        replay.await()
        normal.await()
    }

    @Test
    fun `newer successful request suppresses replay of claimed stale latest event`() = runBlocking {
        val identity = LatestSyncIdentity(dedupeKey = "favorite:123")
        val access = FakeLatestEventAccess(claimed = true)
        val coordinator = coordinator(access)
        var replayed = false

        val result = coordinator.executeNormal(identity.dedupeKey) { NetworkResult.Success(Unit) }
        val replayOutcome = coordinator.replay(event()) {
            replayed = true
            ReplayOutcome.Success
        }

        assertTrue(result is NetworkResult.Success)
        assertEquals(ReplayOutcome.Success, replayOutcome)
        assertFalse(replayed)
    }

    @Test
    fun `template numbers use one canonical representation`() {
        assertEquals("123", syncTemplateValue(123L))
        assertEquals("123", syncTemplateValue(123.0))
        assertEquals("123.5", syncTemplateValue(123.50))
    }

    @Test
    @Suppress("DEPRECATION")
    fun `favorite body and path resolve the same latest dedupe key`() = runBlocking {
        val mapper = FailureEventMapper(
            moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        )
        val addMethod = UserDataSyncApiService::class.java.getDeclaredMethod(
            "addFavorite",
            FavoriteSyncRequest::class.java
        )
        val removeMethod = UserDataSyncApiService::class.java.getDeclaredMethod(
            "removeFavorite",
            Long::class.javaPrimitiveType
        )
        val addIdentity = mapper.map(
            Invocation.of(
                addMethod,
                listOf(FavoriteSyncRequest(123L, "word", "definition", null, "2026-07-13"))
            )
        )
        val removeIdentity = mapper.map(
            Invocation.of(removeMethod, listOf(123L))
        )

        assertEquals("favorite:123", addIdentity?.dedupeKey)
        assertEquals(addIdentity?.dedupeKey, removeIdentity?.dedupeKey)
    }

    private fun coordinator(access: FakeLatestEventAccess) = DefaultLatestSyncRequestCoordinator(
        eventAccess = access,
        sessionGate = FailureQueueSessionGate(AuthenticatedStateProvider)
    )

    private class FakeLatestEventAccess(
        private val claimed: Boolean = true
    ) : LatestSyncEventAccess {
        var isClaimedCalls: Int = 0

        override suspend fun isClaimed(event: FailedSyncEventEntity): Boolean {
            isClaimedCalls++
            return claimed
        }
    }

    private fun event() = FailedSyncEventEntity(
        eventId = "event-1",
        eventType = FailureQueueEventType.FAVORITE_ADD,
        schemaVersion = 1,
        deliveryMode = FailedSyncDeliveryMode.LATEST,
        dedupeKey = "favorite:123",
        orderingKey = "favorite:123",
        sequence = null,
        paramsJson = "{}",
        state = FailedSyncState.IN_FLIGHT,
        attemptCount = 0,
        lastError = null,
        nextAttemptAtMs = 0L,
        leaseToken = "lease",
        leaseExpiresAtMs = Long.MAX_VALUE,
        occurredAtMs = 1L,
        createdAtMs = 1L,
        updatedAtMs = 1L
    )

    private object AuthenticatedStateProvider : AuthStateProvider {
        override fun isAuthenticated(): Boolean = true
        override fun observeAuthenticated(): Flow<Boolean> = flowOf(true)
    }
}
