package com.chen.memorizewords.core.ui.vm

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.contract.UiAction
import com.chen.memorizewords.core.ui.contract.UiEffect
import com.chen.memorizewords.core.ui.contract.UiReducer
import com.chen.memorizewords.core.ui.contract.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class ReducerViewModel<S : UiState, A : UiAction, E : UiEffect>(
    initialState: S,
    private val reducer: UiReducer<S, A, E>
) : BaseViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<E>(extraBufferCapacity = DEFAULT_EFFECT_BUFFER)
    val effects: SharedFlow<E> = _effects.asSharedFlow()

    fun dispatch(action: A) {
        val result = reducer.reduce(_state.value, action)
        _state.value = result.state
        result.effects.forEach { effect ->
            _effects.tryEmit(effect)
        }
        onReduced(action = action, state = result.state, effects = result.effects)
    }

    protected fun emitUiEffect(effect: E) {
        _effects.tryEmit(effect)
    }

    protected fun dispatchAsync(action: A) {
        viewModelScope.launch {
            dispatch(action)
        }
    }

    protected open fun onReduced(action: A, state: S, effects: List<E>) = Unit

    private companion object {
        const val DEFAULT_EFFECT_BUFFER = 32
    }
}
