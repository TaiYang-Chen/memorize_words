package com.chen.memorizewords.feature.user.auth.social

import android.app.Activity
import android.content.Intent
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.feature.user.BuildConfig
import com.chen.memorizewords.feature.user.R
import com.tencent.connect.common.Constants
import com.tencent.tauth.IUiListener
import com.tencent.tauth.Tencent
import com.tencent.tauth.UiError
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QQAuthProvider @Inject constructor(
    private val resourceProvider: ResourceProvider
) {

    private var pendingListener: IUiListener? = null
    private var pendingCallback: ((Result<SocialAuthCredential>) -> Unit)? = null
    private var pendingState: String? = null

    fun onActivityResultData(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val listener = pendingListener ?: return false
        Tencent.onActivityResultData(requestCode, resultCode, data, listener)
        if (requestCode == Constants.REQUEST_LOGIN && resultCode == Activity.RESULT_CANCELED) {
            finish(
                Result.failure(
                    SocialAuthException(
                        resourceProvider.getString(R.string.module_user_social_qq_authorize_cancelled)
                    )
                )
            )
        }
        return true
    }

    suspend fun requestAuth(activity: Activity): Result<SocialAuthCredential> {
        val appId = BuildConfig.QQ_APP_ID
        if (appId.isBlank()) {
            return Result.failure(
                SocialAuthException(
                    resourceProvider.getString(R.string.module_user_social_qq_config_missing)
                )
            )
        }
        val tencent = Tencent.createInstance(appId, activity.applicationContext)
            ?: return Result.failure(
                SocialAuthException(
                    resourceProvider.getString(R.string.module_user_social_qq_sdk_init_failed)
                )
            )

        return suspendCancellableCoroutine { continuation ->
            pendingState = "qq_${System.currentTimeMillis()}"
            pendingListener = object : IUiListener {
                override fun onComplete(response: Any?) {
                    val json = when (response) {
                        is JSONObject -> response
                        is String -> runCatching { JSONObject(response) }.getOrNull()
                        else -> null
                    }

                    val oauthCode = json?.optString("code").orEmpty().ifBlank {
                        json?.optString("access_token").orEmpty()
                    }

                    if (oauthCode.isBlank()) {
                        finish(
                            Result.failure(
                                SocialAuthException(
                                    resourceProvider.getString(R.string.module_user_social_qq_response_empty)
                                )
                            )
                        )
                        return
                    }

                    finish(
                        Result.success(
                            SocialAuthCredential(
                                oauthCode = oauthCode,
                                state = pendingState
                            )
                        )
                    )
                }

                override fun onError(uiError: UiError?) {
                    val message = uiError?.errorMessage
                        ?: resourceProvider.getString(R.string.module_user_social_qq_authorize_failed)
                    finish(Result.failure(SocialAuthException(message)))
                }

                override fun onCancel() {
                    finish(
                        Result.failure(
                            SocialAuthException(
                                resourceProvider.getString(R.string.module_user_social_qq_authorize_cancelled)
                            )
                        )
                    )
                }

                override fun onWarning(code: Int) {
                    // no-op
                }
            }

            pendingCallback = { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            val loginResult = runCatching {
                tencent.loginServerSide(activity, "all", pendingListener)
            }.getOrElse { throwable ->
                finish(
                    Result.failure(
                        SocialAuthException(
                            throwable.message
                                ?: resourceProvider.getString(R.string.module_user_social_qq_authorize_failed)
                        )
                    )
                )
                return@suspendCancellableCoroutine
            }

            if (loginResult != 0) {
                finish(
                    Result.failure(
                        SocialAuthException(
                            resourceProvider.getString(
                                R.string.module_user_social_qq_launch_failed,
                                loginResult
                            )
                        )
                    )
                )
            }

            continuation.invokeOnCancellation {
                clearPending()
            }
        }
    }

    @Synchronized
    private fun finish(result: Result<SocialAuthCredential>) {
        pendingCallback?.invoke(result)
        clearPending()
    }

    @Synchronized
    private fun clearPending() {
        pendingListener = null
        pendingCallback = null
        pendingState = null
    }
}
