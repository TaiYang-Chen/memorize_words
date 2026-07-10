package com.chen.memorizewords.core.session.logout

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LogoutPresentationState<T>(
    source: Flow<T>,
    logoutState: StateFlow<LogoutState>,
    scope: CoroutineScope,
    initialValue: T
) {

    private var latestValue: T = initialValue
    private val _value = MutableStateFlow(initialValue)
    val value: StateFlow<T> = _value

    init {
        scope.launch {
            source.collect { next ->
                latestValue = next
                if (!logoutState.value.holdsPresentation()) {
                    _value.value = next
                }
            }
        }
        scope.launch {
            logoutState.collect { state ->
                if (!state.holdsPresentation()) {
                    _value.value = latestValue
                }
            }
        }
    }
}

private fun LogoutState.holdsPresentation(): Boolean {
    return this is LogoutState.AwaitingLoadingFrame ||
        this is LogoutState.Executing ||
        this is LogoutState.ReadyToNavigate ||
        this is LogoutState.Navigating
}
