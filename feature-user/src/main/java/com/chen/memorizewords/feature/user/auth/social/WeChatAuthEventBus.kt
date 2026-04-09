package com.chen.memorizewords.feature.user.auth.social

import com.chen.memorizewords.core.navigation.WeChatAuthResultHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface WeChatAuthEvent {
    data class Success(
        val oauthCode: String,
        val state: String?
    ) : WeChatAuthEvent

    data object Cancel : WeChatAuthEvent

    data class Error(val message: String) : WeChatAuthEvent
}

object WeChatAuthEventBus {
    private val _events = MutableSharedFlow<WeChatAuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<WeChatAuthEvent> = _events.asSharedFlow()

    fun dispatch(event: WeChatAuthEvent) {
        _events.tryEmit(event)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WeChatAuthResultHandlerModule {

    @Binds
    @Singleton
    abstract fun bindWeChatAuthResultHandler(
        impl: DefaultWeChatAuthResultHandler
    ): WeChatAuthResultHandler
}

@Singleton
class DefaultWeChatAuthResultHandler @Inject constructor() : WeChatAuthResultHandler {
    override fun onSuccess(oauthCode: String, state: String?) {
        WeChatAuthEventBus.dispatch(WeChatAuthEvent.Success(oauthCode = oauthCode, state = state))
    }

    override fun onCancel() {
        WeChatAuthEventBus.dispatch(WeChatAuthEvent.Cancel)
    }

    override fun onError(message: String) {
        WeChatAuthEventBus.dispatch(WeChatAuthEvent.Error(message))
    }
}
