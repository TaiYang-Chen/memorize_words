package com.chen.memorizewords.feature.user.ui.login.fusion

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.FusionPhoneLoginUseCase
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FusionPhoneLoginViewModel @Inject constructor(
    private val fusionPhoneLoginUseCase: FusionPhoneLoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    fun loginByFusionVerifyToken(verifyToken: String) {
        launchWithLoading(resourceProvider.getString(R.string.module_user_login_loading)) {
            fusionPhoneLoginUseCase(verifyToken).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

                    is LoginError.EmptyOauthCode ->
                        showToast(resourceProvider.getString(R.string.module_user_fusion_login_failed))

                    else -> showToast(
                        resolveAuthFailureMessage(
                            failure,
                            resourceProvider.getString(R.string.module_user_fusion_login_failed)
                        )
                    )
                }
            }
        }
    }
}
