package com.chen.memorizewords.core.ui.fragment

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.contract.UiEffect
import com.chen.memorizewords.core.ui.contract.UiEffectHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun <T> LifecycleOwner.collectEffects(
    effects: Flow<T>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    handler: suspend (T) -> Unit
) {
    lifecycleScope.launch {
        repeatOnLifecycle(minActiveState) {
            effects.collect { effect ->
                handler(effect)
            }
        }
    }
}

fun <E : UiEffect> LifecycleOwner.collectEffects(
    effects: Flow<E>,
    effectHandler: UiEffectHandler<E>,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED
) {
    collectEffects(
        effects = effects,
        minActiveState = minActiveState,
        handler = effectHandler::handle
    )
}
