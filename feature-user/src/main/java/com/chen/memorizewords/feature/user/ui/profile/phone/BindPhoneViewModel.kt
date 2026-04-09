package com.chen.memorizewords.feature.user.ui.profile.phone

import androidx.lifecycle.MutableLiveData
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class BindPhoneViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val phoneNumber = MutableLiveData("")
    val smsCode = MutableLiveData("")
    val sendCodeText = MutableLiveData(resourceProvider.getString(R.string.module_user_send_code))
    val isFormEnabled = MutableLiveData(false)
    val isSendCodeEnabled = MutableLiveData(false)
    val isConfirmEnabled = MutableLiveData(false)

    fun sendCode() {
        showToast(resourceProvider.getString(R.string.module_user_bind_phone_feature_coming_soon))
    }

    fun confirmBind() {
        showToast(resourceProvider.getString(R.string.module_user_bind_phone_feature_coming_soon))
    }
}
