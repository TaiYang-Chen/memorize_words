package com.chen.memorizewords.feature.feedback.ui.feedback

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.feedback.FeedbackImagePayload
import com.chen.memorizewords.domain.usecase.feedback.SubmitFeedbackUseCase
import com.chen.memorizewords.feature.feedback.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val submitFeedbackUseCase: SubmitFeedbackUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _contact = MutableStateFlow("")
    val contact: StateFlow<String> = _contact.asStateFlow()

    private val _imageList = MutableStateFlow<List<Uri>>(emptyList())
    val imageList: StateFlow<List<Uri>> = _imageList.asStateFlow()

    private val _canSend = MutableStateFlow(false)
    val canSend: StateFlow<Boolean> = _canSend.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _messageEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageEvents: SharedFlow<String> = _messageEvents.asSharedFlow()

    fun setContent(text: String) {
        val truncated = if (text.length > 200) text.substring(0, 200) else text
        _content.value = truncated
        _canSend.value = truncated.isNotBlank()
    }

    fun setContact(text: String) {
        _contact.value = text
    }

    fun addImages(uris: List<Uri>) {
        val list = _imageList.value.toMutableList()
        if (list.size >= MAX_IMAGES) {
            emitMessage(resourceProvider.getString(R.string.module_feedback_feedback_max_images))
            return
        }
        val remain = (MAX_IMAGES - list.size).coerceAtLeast(0)
        list.addAll(uris.take(remain))
        _imageList.value = list
        if (uris.size > remain) {
            emitMessage(resourceProvider.getString(R.string.module_feedback_feedback_max_images))
        }
    }

    fun removeImageAt(index: Int) {
        val list = _imageList.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _imageList.value = list
        }
    }

    fun sendFeedback(images: List<FeedbackImagePayload>) {
        val feedbackContent = _content.value
        if (feedbackContent.isBlank()) {
            emitMessage(
                resourceProvider.getString(R.string.module_feedback_feedback_content_required)
            )
            return
        }
        if (images.size > MAX_IMAGES) {
            emitMessage(resourceProvider.getString(R.string.module_feedback_feedback_max_images))
            return
        }
        if (_isSending.value) return

        _isSending.value = true
        _canSend.value = false

        viewModelScope.launch {
            submitFeedbackUseCase(
                content = feedbackContent,
                contact = _contact.value,
                images = images
            ).onSuccess {
                emitMessage(
                    resourceProvider.getString(R.string.module_feedback_feedback_submit_success)
                )
                _content.value = ""
                _contact.value = ""
                _imageList.value = emptyList()
            }.onFailure { failure ->
                emitMessage(
                    failure.message ?: resourceProvider.getString(
                        R.string.module_feedback_feedback_submit_failed
                    )
                )
            }

            _isSending.value = false
            _canSend.value = _content.value.isNotBlank()
        }
    }

    private fun emitMessage(message: String) {
        _messageEvents.tryEmit(message)
    }

    companion object {
        private const val MAX_IMAGES = 3
    }
}
