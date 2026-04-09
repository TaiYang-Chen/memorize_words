package com.chen.memorizewords.feature.user.auth.social

import android.app.Activity
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.feature.user.BuildConfig
import com.chen.memorizewords.feature.user.R
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeChatAuthProvider @Inject constructor(
    private val resourceProvider: ResourceProvider
) {

    suspend fun requestAuth(activity: Activity): Result<SocialAuthCredential> {
        val appId = BuildConfig.WX_APP_ID
        if (appId.isBlank()) {
            return Result.failure(
                SocialAuthException(
                    resourceProvider.getString(R.string.module_user_social_wechat_config_missing)
                )
            )
        }

        val api = WXAPIFactory.createWXAPI(activity, appId, true).apply {
            registerApp(appId)
        }
        if (!api.isWXAppInstalled) {
            return Result.failure(
                SocialAuthException(
                    resourceProvider.getString(R.string.module_user_social_wechat_not_installed)
                )
            )
        }

        val state = "wx_${System.currentTimeMillis()}"
        val request = SendAuth.Req().apply {
            scope = "snsapi_userinfo"
            this.state = state
        }
        if (!api.sendReq(request)) {
            return Result.failure(
                SocialAuthException(
                    resourceProvider.getString(R.string.module_user_social_wechat_launch_failed)
                )
            )
        }

        return runCatching {
            withTimeout(60_000L) {
                WeChatAuthEventBus.events
                    .mapNotNull { event ->
                        when (event) {
                            is WeChatAuthEvent.Success -> {
                                if (event.state == state || event.state.isNullOrBlank()) {
                                    Result.success(
                                        SocialAuthCredential(
                                            oauthCode = event.oauthCode,
                                            state = event.state
                                        )
                                    )
                                } else {
                                    null
                                }
                            }

                            WeChatAuthEvent.Cancel -> {
                                Result.failure<SocialAuthCredential>(
                                    SocialAuthException(
                                        resourceProvider.getString(
                                            R.string.module_user_social_wechat_authorize_cancelled
                                        )
                                    )
                                )
                            }

                            is WeChatAuthEvent.Error -> {
                                Result.failure<SocialAuthCredential>(
                                    SocialAuthException(event.message)
                                )
                            }
                        }
                    }
                    .first()
            }.getOrThrow()
        }
    }
}
