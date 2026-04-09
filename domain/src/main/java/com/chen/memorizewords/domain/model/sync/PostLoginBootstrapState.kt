package com.chen.memorizewords.domain.model.sync

sealed interface PostLoginBootstrapState {
    data object Idle : PostLoginBootstrapState
    data object Running : PostLoginBootstrapState
    data object Failed : PostLoginBootstrapState
    data object Succeeded : PostLoginBootstrapState
}
