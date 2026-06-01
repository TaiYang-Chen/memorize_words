package com.chen.memorizewords.core.ui.contract

interface UiState

interface UiAction

interface UiEffect

interface UiReducer<S : UiState, A : UiAction, E : UiEffect> {
    fun reduce(state: S, action: A): UiReduceResult<S, E>
}

interface UiEffectHandler<E : UiEffect> {
    suspend fun handle(effect: E)
}

data class UiReduceResult<S : UiState, E : UiEffect>(
    val state: S,
    val effects: List<E> = emptyList()
)
