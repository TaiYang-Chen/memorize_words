package com.chen.memorizewords.feature.user.auth.fusion

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import com.alicom.fusion.auth.AlicomFusionAuthCallBack
import com.alicom.fusion.auth.AlicomFusionBusiness
import com.alicom.fusion.auth.AlicomFusionLog
import com.alicom.fusion.auth.HalfWayVerifyResult
import com.alicom.fusion.auth.config.AlicomFusionSceneUtil
import com.alicom.fusion.auth.config.AlicomFusionCheckUtil
import com.alicom.fusion.auth.error.AlicomFusionEvent
import com.alicom.fusion.auth.net.FusionRequestUtils
import com.alicom.fusion.auth.numberauth.NumberAuthUtil
import com.alicom.fusion.auth.smsauth.FusionSmsManager
import com.alicom.fusion.auth.tools.FusionPackageUtils
import com.alicom.fusion.auth.token.AlicomFusionAuthToken
import com.chen.memorizewords.domain.account.usecase.user.GetFusionAuthTokenUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

@Singleton
class FusionPhoneAuthProvider @Inject constructor(
    private val getFusionAuthTokenUseCase: GetFusionAuthTokenUseCase
) {
    private var business: AlicomFusionBusiness? = null
    private var smsSession: FusionSmsSession? = null

    suspend fun requestVerifyToken(
        activity: Activity,
        templateId: String = LOGIN_SCENE_ID,
        phoneForVerification: String = ""
    ): Result<String> = runCatching {
        val token = withContext(Dispatchers.IO) {
            getFusionAuthTokenUseCase().getOrThrow()
        }
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val fusionBusiness = AlicomFusionBusiness()
                business = fusionBusiness
                AlicomFusionBusiness.useSDKSupplyUMSDK(false, "")
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
                        Log.i(TAG, "Fusion token auth success. templateId=$templateId")
                        fusionBusiness.startSceneWithTemplateId(activity, templateId)
                    }

                    override fun onSDKTokenAuthFailure(
                        token: AlicomFusionAuthToken?,
                        event: AlicomFusionEvent?
                    ) {
                        Log.w(TAG, "Fusion token auth failure. ${event.describe(templateId)}")
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
                        Log.i(TAG, "Fusion verify success. templateId=$templateId, nodeName=$nodeName, ${event.describe(templateId)}")
                        if (verifyToken.isNullOrBlank()) {
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(FusionPhoneAuthException(event.message())))
                            }
                        } else if (continuation.isActive) {
                            fusionBusiness.continueSceneWithTemplateId(templateId, true)
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
                        Log.i(TAG, "Fusion halfway verify. templateId=$templateId, nodeName=$nodeName, hasMaskToken=${!maskToken.isNullOrBlank()}, ${event.describe(templateId)}")
                        halfWayVerifyResult?.verifyResult(!maskToken.isNullOrBlank())
                    }

                    override fun onVerifyFailed(event: AlicomFusionEvent?, nodeName: String?) {
                        Log.w(TAG, "Fusion verify failed. templateId=$templateId, nodeName=$nodeName, ${event.describe(templateId)}")
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(FusionPhoneAuthException(event.message())))
                        }
                        destroy()
                    }

                    override fun onTemplateFinish(event: AlicomFusionEvent?) {
                        Log.w(TAG, "Fusion template finish before verify success. ${event.describe(templateId)}")
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(FusionPhoneAuthException(event.message())))
                        }
                        destroy()
                    }

                    override fun onAuthEvent(event: AlicomFusionEvent?) {
                        Log.i(TAG, "Fusion auth event. ${event.describe(templateId)}")
                    }

                    override fun onGetPhoneNumberForVerification(
                        nodeName: String?,
                        event: AlicomFusionEvent?
                    ): String {
                        Log.i(TAG, "Fusion requested phone for verification. templateId=$templateId, nodeName=$nodeName, ${event.describe(templateId)}")
                        return phoneForVerification
                    }

                    override fun onVerifyInterrupt(event: AlicomFusionEvent?) {
                        Log.w(TAG, "Fusion verify interrupted. ${event.describe(templateId)}")
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

    suspend fun sendSmsCodeInPlace(
        context: Context,
        templateId: String,
        phone: String,
        smsNodeId: String
    ): Result<Unit> = runCatching {
        val applicationContext = context.applicationContext
        val normalizedPhone = phone.trim()
        ensureFusionTokenReady(templateId).getOrThrow()
        NumberAuthUtil.getInstance().setTemplatedId(templateId)
        FusionSmsManager.a(applicationContext, smsNodeId)

        val response = try {
            withContext(Dispatchers.IO) {
                FusionRequestUtils.sendVerifyNumCode(applicationContext, normalizedPhone)
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "Fusion sms send failed. templateId=$templateId, smsNodeId=$smsNodeId", throwable)
            throw FusionPhoneAuthException(DEFAULT_ERROR_MESSAGE)
        }
        if (!response.isSuccess) {
            throw FusionPhoneAuthException(response.message ?: DEFAULT_ERROR_MESSAGE)
        }
        val sendVerifyToken = response.model?.verifyToken.orEmpty()
        if (sendVerifyToken.isBlank()) {
            throw FusionPhoneAuthException(DEFAULT_ERROR_MESSAGE)
        }
        smsSession = FusionSmsSession(
            templateId = templateId,
            smsNodeId = smsNodeId,
            phone = normalizedPhone,
            sendVerifyToken = sendVerifyToken
        )
    }

    fun verifySmsCodeInPlace(
        context: Context,
        templateId: String,
        phone: String,
        code: String,
        smsNodeId: String
    ): Result<String> = runCatching {
        val normalizedPhone = phone.trim()
        val session = smsSession
        if (session == null ||
            session.templateId != templateId ||
            session.smsNodeId != smsNodeId ||
            session.phone != normalizedPhone
        ) {
            throw FusionPhoneAuthException(DEFAULT_ERROR_MESSAGE)
        }
        buildSmsVerifyPayload(
            context = context.applicationContext,
            templateId = templateId,
            smsNodeId = smsNodeId,
            phone = normalizedPhone,
            code = code.trim(),
            sendVerifyToken = session.sendVerifyToken
        )
    }

    fun destroy() {
        business?.destory()
        business = null
        smsSession = null
    }

    private suspend fun ensureFusionTokenReady(templateId: String): Result<Unit> = runCatching {
        val token = withContext(Dispatchers.IO) {
            getFusionAuthTokenUseCase().getOrThrow()
        }
        AlicomFusionLog.setLogEnable(false)
        prepareFusionTokenForSms(templateId, token.authToken)
    }

    private fun prepareFusionTokenForSms(templateId: String, sdkToken: String) {
        NumberAuthUtil.getInstance().setTemplatedId(templateId)
        val validateResult = AlicomFusionCheckUtil.checkRealTokenVailed(sdkToken)
        if (validateResult != TOKEN_VALID) {
            Log.w(TAG, "Fusion token invalid for sms. templateId=$templateId, result=$validateResult")
            throw FusionPhoneAuthException(DEFAULT_ERROR_MESSAGE)
        }
        AlicomFusionSceneUtil.getInstance().setSdkToken(sdkToken)
        val authToken = AlicomFusionSceneUtil.getInstance().authToken
        if (authToken == null ||
            authToken.stsToken.isNullOrBlank() ||
            authToken.accessKeyId.isNullOrBlank() ||
            authToken.accessKeySecret.isNullOrBlank()
        ) {
            Log.w(TAG, "Fusion token missing required fields for sms. templateId=$templateId")
            throw FusionPhoneAuthException(DEFAULT_ERROR_MESSAGE)
        }
    }

    private fun buildSmsVerifyPayload(
        context: Context,
        templateId: String,
        smsNodeId: String,
        phone: String,
        code: String,
        sendVerifyToken: String
    ): String {
        NumberAuthUtil.getInstance().setTemplatedId(templateId)
        val authToken = AlicomFusionSceneUtil.getInstance().authToken
        val payload = JSONObject().apply {
            put("verifyCode", code)
            put("phoneNumber", phone)
            put("verifyToken", sendVerifyToken)
            put("nodeId", smsNodeId)
            put("sceneTemplateId", templateId)
            put("bizToken", authToken?.bizToken.orEmpty())
            put("packageName", FusionPackageUtils.a(context))
            put("packageSign", FusionPackageUtils.b(context))
            put("platform", "Android")
            put("authType", SMS_AUTH_TYPE)
        }.toString()
        return String(Base64.encode(payload.toByteArray(), Base64.DEFAULT))
    }

    private fun AlicomFusionEvent?.message(): String {
        val event = this ?: return DEFAULT_ERROR_MESSAGE
        return listOfNotNull(
            event.errorMsg,
            event.innerMsg,
            event.errorCode?.let { "code=$it" },
            event.innerCode?.let { "innerCode=$it" }
        ).firstOrNull { it.isNotBlank() } ?: DEFAULT_ERROR_MESSAGE
    }

    private fun AlicomFusionEvent?.describe(fallbackTemplateId: String): String {
        val event = this ?: return "event=null, templateId=$fallbackTemplateId"
        return "templateId=${event.templatedId ?: fallbackTemplateId}, " +
            "nodeId=${event.nodeId}, " +
            "errorCode=${event.errorCode}, " +
            "errorMsg=${event.errorMsg}, " +
            "innerCode=${event.innerCode}, " +
            "innerMsg=${event.innerMsg}, " +
            "requestId=${event.requestId}, " +
            "innerRequestId=${event.innerRequestId}"
    }

    private companion object {
        const val TAG = "FusionPhoneAuth"
        const val LOGIN_SCENE_ID = "100001"
        const val SMS_AUTH_TYPE = 4
        const val TOKEN_VALID = 0
        const val DEFAULT_ERROR_MESSAGE = "Fusion phone auth failed"
    }

    private data class FusionSmsSession(
        val templateId: String,
        val smsNodeId: String,
        val phone: String,
        val sendVerifyToken: String
    )
}

class FusionPhoneAuthException(message: String) : RuntimeException(message)
