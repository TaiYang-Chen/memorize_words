package com.chen.memorizewords.data.practice.repository

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningPracticePreferencesRepositoryImplTest {

    @Test
    fun `defaults are empty mode and hint not shown`() {
        val repository = ListeningPracticePreferencesRepositoryImpl(FakeListeningPracticePreferencesStore())

        assertNull(runSuspend { repository.getLastListeningPracticeModeName() })
        assertFalse(runSuspend { repository.hasShownModeSwitchHint() })
        assertEquals("MEANING", repository.getDefaultListeningPracticeModeName())
    }

    @Test
    fun `saveLastListeningPracticeModeName stores valid mode`() {
        val repository = ListeningPracticePreferencesRepositoryImpl(FakeListeningPracticePreferencesStore())

        runSuspend { repository.saveLastListeningPracticeModeName("SPELLING") }

        assertEquals("SPELLING", runSuspend { repository.getLastListeningPracticeModeName() })
    }

    @Test
    fun `markModeSwitchHintShown persists state`() {
        val repository = ListeningPracticePreferencesRepositoryImpl(FakeListeningPracticePreferencesStore())

        runSuspend { repository.markModeSwitchHintShown() }

        assertTrue(runSuspend { repository.hasShownModeSwitchHint() })
    }

    @Test
    fun `clearLocalState resets saved mode and hint`() {
        val repository = ListeningPracticePreferencesRepositoryImpl(FakeListeningPracticePreferencesStore())

        runSuspend {
            repository.saveLastListeningPracticeModeName("SPELLING")
            repository.markModeSwitchHintShown()
        }
        repository.clearLocalState()

        assertNull(runSuspend { repository.getLastListeningPracticeModeName() })
        assertFalse(runSuspend { repository.hasShownModeSwitchHint() })
    }

    @Test
    fun `normalizeListeningPracticeModeName rejects invalid values`() {
        assertNull(normalizeListeningPracticeModeName(null))
        assertNull(normalizeListeningPracticeModeName(""))
        assertNull(normalizeListeningPracticeModeName("INVALID"))
        assertNull(normalizeListeningPracticeModeName("RANDOM"))
        assertNull(normalizeListeningPracticeModeName("MIXED"))
        assertNull(normalizeListeningPracticeModeName("ADVANCED_REVIEW"))
        assertEquals("MEANING", normalizeListeningPracticeModeName("MEANING"))
        assertEquals("SPELLING", normalizeListeningPracticeModeName("SPELLING"))
    }
}

private class FakeListeningPracticePreferencesStore : ListeningPracticePreferencesStore {
    private val values = mutableMapOf<String, Any>()

    override fun getString(key: String): String? = values[key] as? String

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defaultValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var value: T? = null
    var error: Throwable? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            result
                .onSuccess { value = it }
                .onFailure { error = it }
        }
    })
    error?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return value as T
}
