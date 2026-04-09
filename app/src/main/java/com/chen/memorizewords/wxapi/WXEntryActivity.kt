package com.chen.memorizewords.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.chen.memorizewords.BuildConfig
import com.chen.memorizewords.R
import com.chen.memorizewords.core.navigation.WeChatAuthResultHandler
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelmsg.SendAuth
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class WXEntryActivity : Activity(), IWXAPIEventHandler {

    private val authResultHandler: WeChatAuthResultHandler by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            WeChatAuthHandlerEntryPoint::class.java
        ).wechatAuthResultHandler()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val appId = BuildConfig.WX_APP_ID
        if (appId.isBlank()) {
            authResultHandler.onError(getString(R.string.app_wechat_config_missing))
            finish()
            return
        }
        val api = WXAPIFactory.createWXAPI(this, appId, false)
        api.handleIntent(intent, this)
    }

    override fun onReq(req: BaseReq?) {
        finish()
    }

    override fun onResp(resp: BaseResp?) {
        val authResp = resp as? SendAuth.Resp
        when (resp?.errCode) {
            BaseResp.ErrCode.ERR_OK -> {
                val code = authResp?.code.orEmpty()
                if (code.isBlank()) {
                    authResultHandler.onError(getString(R.string.app_wechat_auth_code_empty))
                } else {
                    authResultHandler.onSuccess(oauthCode = code, state = authResp?.state)
                }
            }

            BaseResp.ErrCode.ERR_USER_CANCEL -> {
                authResultHandler.onCancel()
            }

            else -> {
                authResultHandler.onError(
                    resp?.errStr ?: getString(R.string.app_wechat_authorization_failed)
                )
            }
        }
        finish()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WeChatAuthHandlerEntryPoint {
    fun wechatAuthResultHandler(): WeChatAuthResultHandler
}
