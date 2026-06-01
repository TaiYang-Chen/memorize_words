package com.chen.memorizewords.domain.sync.model
sealed interface PostLoginBootstrapState {
    data object Idle : PostLoginBootstrapState
    data object Running : PostLoginBootstrapState
    data object Failed : PostLoginBootstrapState
    data object Succeeded : PostLoginBootstrapState
}
