package com.chen.memorizewords.feature.user.auth.fusion

import android.content.Context
import android.util.Base64
import android.util.Log
import com.alicom.fusion.auth.AlicomFusionLog
import com.alicom.fusion.auth.config.AlicomFusionSceneUtil
import com.alicom.fusion.auth.config.AlicomFusionCheckUtil
import com.alicom.fusion.auth.error.AlicomFusionEvent
import com.alicom.fusion.auth.net.FusionRequestUtils
import com.alicom.fusion.auth.numberauth.NumberAuthUtil
import com.alicom.fusion.auth.smsauth.FusionSmsManager
import com.alicom.fusion.auth.tools.FusionPackageUtils
import com.chen.memorizewords.domain.account.usecase.user.GetFusionAuthTokenUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class FusionPhoneAuthProvider @Inject constructor(
    private val getFusionAuthTokenUseCase: GetFusionAuthTokenUseCase
) {
    private var smsSession: FusionSmsSession? = null

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
