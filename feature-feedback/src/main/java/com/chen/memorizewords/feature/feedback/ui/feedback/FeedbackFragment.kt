package com.chen.memorizewords.feature.feedback.ui.feedback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.domain.model.feedback.FeedbackImagePayload
import com.chen.memorizewords.feature.feedback.R
import com.chen.memorizewords.feature.feedback.databinding.ModuleFeedbackFragmentFeedbackBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class FeedbackFragment : BaseFragment<FeedbackViewModel, ModuleFeedbackFragmentFeedbackBinding>() {

    override val viewModel: FeedbackViewModel by lazy {
        ViewModelProvider(this)[FeedbackViewModel::class.java]
    }

    private lateinit var imageAdapter: ImageAdapter
    private var isPreparingPayloads: Boolean = false

    private val openMultipleDocuments = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        uris.forEach { uri ->
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
            }
        }
        viewModel.addImages(uris)

        databind.viewModel = viewModel
    }

    override fun initView(savedInstanceState: Bundle?) {
        setupUi()
    }

    private fun setupUi() {
        databind.etFeedback.filters = arrayOf(InputFilter.LengthFilter(200))
        databind.etFeedback.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                viewModel.setContent(text)
                renderCharCount(text.length)
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        databind.etContact.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                viewModel.setContact(s?.toString() ?: "")
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        imageAdapter = ImageAdapter { pos ->
            viewModel.removeImageAt(pos)
        }
        databind.rvImages.apply {
            adapter = imageAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        databind.btnAddImage.setOnClickListener {
            openMultipleDocuments.launch(arrayOf("image/*"))
        }

        databind.btnSend.setOnClickListener {
            if (isPreparingPayloads) return@setOnClickListener
            lifecycleScope.launch {
                isPreparingPayloads = true
                renderSendButton()
                val payloads = buildImagePayloads(viewModel.imageList.value)
                isPreparingPayloads = false
                renderSendButton()
                payloads ?: return@launch
                viewModel.sendFeedback(payloads)
            }
        }
    }

    private suspend fun buildImagePayloads(uris: List<Uri>): List<FeedbackImagePayload>? {
        val resolver = requireContext().contentResolver
        val timestamp = System.currentTimeMillis()
        val sources = withContext(Dispatchers.IO) {
            uris.map { uri ->
                val bytes = runCatching {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull() ?: return@withContext null

                FeedbackImageSource(
                    bytes = bytes,
                    mimeType = resolver.getType(uri).orEmpty().ifBlank { "image/jpeg" },
                    originalFileName = uri.lastPathSegment
                )
            }
        } ?: run {
            Toast.makeText(
                requireContext(),
                getString(R.string.module_feedback_feedback_read_image_failed),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }

        return when (val result = FeedbackImagePayloadBuilder.build(sources, timestamp)) {
            is FeedbackImageBuildResult.Success -> result.payloads
            is FeedbackImageBuildResult.Failure -> {
                val messageRes = when (result.reason) {
                    FeedbackImageBuildFailure.SingleImageTooLarge -> {
                        R.string.module_feedback_feedback_single_image_too_large
                    }

                    FeedbackImageBuildFailure.TotalImagesTooLarge -> {
                        R.string.module_feedback_feedback_total_images_too_large
                    }
                }
                Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    private fun renderSendButton() {
        val canSend = viewModel.canSend.value
        val sending = viewModel.isSending.value || isPreparingPayloads
        databind.btnSend.text = if (sending) {
            getString(R.string.module_feedback_feedback_sending)
        } else {
            getString(R.string.module_feedback_feedback_send)
        }
        databind.btnSend.isEnabled = canSend && !sending
        databind.btnSend.alpha = if (canSend && !sending) 1.0f else 0.6f
    }

    private fun renderCharCount(length: Int) {
        databind.tvCharCount.text = getString(R.string.module_feedback_char_count, length)
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.canSend.collect { canSend ->
                        val sending = viewModel.isSending.value || isPreparingPayloads
                        databind.btnSend.isEnabled = canSend && !sending
                        databind.btnSend.alpha = if (canSend && !sending) 1.0f else 0.6f
                    }
                }

                launch {
                    viewModel.isSending.collect {
                        renderSendButton()
                    }
                }

                launch {
                    viewModel.content.collect { content ->
                        val current = databind.etFeedback.text?.toString() ?: ""
                        if (current != content) {
                            databind.etFeedback.setText(content)
                            databind.etFeedback.setSelection(content.length)
                        }
                        renderCharCount(content.length)
                    }
                }

                launch {
                    viewModel.contact.collect { contact ->
                        val current = databind.etContact.text?.toString() ?: ""
                        if (current != contact) {
                            databind.etContact.setText(contact)
                            databind.etContact.setSelection(contact.length)
                        }
                    }
                }

                launch {
                    viewModel.imageList.collect { list ->
                        imageAdapter.submitList(list)
                        databind.rvImages.visibility = if (list.isNotEmpty()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }

                launch {
                    viewModel.messageEvents.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
