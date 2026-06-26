package com.chen.memorizewords.feature.user.auth.fusion

import android.app.Activity
import com.alicom.fusion.auth.AlicomFusionAuthCallBack
import com.alicom.fusion.auth.AlicomFusionBusiness
import com.alicom.fusion.auth.AlicomFusionLog
import com.alicom.fusion.auth.HalfWayVerifyResult
import com.alicom.fusion.auth.error.AlicomFusionEvent
import com.alicom.fusion.auth.token.AlicomFusionAuthToken
import com.chen.memorizewords.domain.account.usecase.user.GetFusionAuthTokenUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Singleton
class FusionPhoneAuthProvider @Inject constructor(
    private val getFusionAuthTokenUseCase: GetFusionAuthTokenUseCase
) {
    private var business: AlicomFusionBusiness? = null

    suspend fun requestVerifyToken(activity: Activity): Result<String> = runCatching {
        val token = withContext(Dispatchers.IO) {
            getFusionAuthTokenUseCase().getOrThrow()
        }
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val fusionBusiness = AlicomFusionBusiness()
                business = fusionBusiness
                AlicomFusionBusiness.useSDKSupplyUMSDK(true, UMENG_CHANNEL)
                AlicomFusionLog.setLogEnable(false)

                val authToken = AlicomFusionAuthToken().apply {
                    setAuthToken(token.authToken)
                }
                fusionBusiness.initWithToken(activity.applicationContext, token.schemeCode, authToken)
                fusionBusiness.setAlicomFusionAuthCallBack(object : AlicomFusionAuthCallBack {
                    override fun onSDKTokenUpdate(): AlicomFusionAuthToken {
                        return AlicomFusionAuthToken().apply {
                            setAuthToken(token.authToken)
                        }
                    }

                    override fun onSDKTokenAuthSuccess() {
                        fusionBusiness.startSceneWithTemplateId(activity, TEMPLATE_ID)
                    }

                    override fun onSDKTokenAuthFailure(
                        token: AlicomFusionAuthToken?,
                        event: AlicomFusionEvent?
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(FusionPhoneAuthException(event.message())))
                        }
                        destroy()
                    }

                    override fun onVerifySuccess(
                        verifyToken: String?,
                        nodeName: String?,
                        event: AlicomFusionEvent?
                    ) {
                        if (verifyToken.isNullOrBlank()) {
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(FusionPhoneAuthException(event.message())))
                            }
                        } else if (continuation.isActive) {
                            fusionBusiness.continueSceneWithTemplateId(TEMPLATE_ID, true)
                            continuation.resume(Result.success(verifyToken))
                        }
                        destroy()
                    }

                    override fun onHalfWayVerifySuccess(
                        nodeName: String?,
                        maskToken: String?,
                        event: AlicomFusionEvent?,
                        halfWayVerifyResult: HalfWayVerifyResult?
                    ) {
                        halfWayVerifyResult?.verifyResult(!maskToken.isNullOrBlank())
                    }

                    override fun onVerifyFailed(event: AlicomFusionEvent?, nodeName: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(FusionPhoneAuthException(event.message())))
                        }
                        destroy()
                    }

                    override fun onTemplateFinish(event: AlicomFusionEvent?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(FusionPhoneAuthException(event.message())))
                        }
                        destroy()
                    }

                    override fun onAuthEvent(event: AlicomFusionEvent?) = Unit

                    override fun onGetPhoneNumberForVerification(
                        nodeName: String?,
                        event: AlicomFusionEvent?
                    ): String = ""

                    override fun onVerifyInterrupt(event: AlicomFusionEvent?) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(FusionPhoneAuthException(event.message())))
                        }
                        destroy()
                    }
                })

                continuation.invokeOnCancellation {
                    destroy()
                }
            }.getOrThrow()
        }
    }

    fun destroy() {
        business?.destory()
        business = null
    }

    private fun AlicomFusionEvent?.message(): String {
        return this?.errorMsg ?: DEFAULT_ERROR_MESSAGE
    }

    private companion object {
        const val TEMPLATE_ID = "100001"
        const val UMENG_CHANNEL = "umeng"
        const val DEFAULT_ERROR_MESSAGE = "Fusion phone auth failed"
    }
}

class FusionPhoneAuthException(message: String) : RuntimeException(message)
