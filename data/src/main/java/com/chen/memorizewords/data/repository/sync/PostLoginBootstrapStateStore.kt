package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.domain.model.sync.PostLoginBootstrapState
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class PostLoginBootstrapStateStore @Inject constructor(
    private val mmkv: MMKV
) {

    private val stateFlow = MutableStateFlow(readState())

    fun getState(): PostLoginBootstrapState = stateFlow.value

    fun observeState(): Flow<PostLoginBootstrapState> = stateFlow.asStateFlow()

    fun setState(state: PostLoginBootstrapState) {
        mmkv.encode(KEY_STATE, state.toPersistedValue())
        stateFlow.value = state
    }

    fun reset() {
        mmkv.removeValueForKey(KEY_STATE)
        stateFlow.value = PostLoginBootstrapState.Idle
    }

    private fun readState(): PostLoginBootstrapState {
        return parsePostLoginBootstrapState(mmkv.decodeString(KEY_STATE, null))
    }

    private companion object {
        private const val KEY_STATE = "post_login_bootstrap_state"
    }
}

private fun PostLoginBootstrapState.toPersistedValue(): String {
    return when (this) {
        PostLoginBootstrapState.Idle -> "IDLE"
        PostLoginBootstrapState.Running -> "RUNNING"
        PostLoginBootstrapState.Failed -> "FAILED"
        PostLoginBootstrapState.Succeeded -> "SUCCEEDED"
    }
}

private fun parsePostLoginBootstrapState(value: String?): PostLoginBootstrapState {
    return when (value) {
        "RUNNING" -> PostLoginBootstrapState.Running
        "FAILED" -> PostLoginBootstrapState.Failed
        "SUCCEEDED" -> PostLoginBootstrapState.Succeeded
        else -> PostLoginBootstrapState.Idle
    }
}
