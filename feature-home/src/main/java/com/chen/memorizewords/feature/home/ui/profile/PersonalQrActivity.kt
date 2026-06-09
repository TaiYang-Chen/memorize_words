package com.chen.memorizewords.feature.home.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import coil.transform.CircleCropTransformation
import com.chen.memorizewords.core.ui.activity.BaseVmDbActivity
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.FeatureHomeActivityPersonalQrBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PersonalQrActivity :
    BaseVmDbActivity<PersonalQrViewModel, FeatureHomeActivityPersonalQrBinding>() {

    override val viewModel: PersonalQrViewModel by lazy {
        ViewModelProvider(this)[PersonalQrViewModel::class.java]
    }

    private var currentPayload: String = ""
    private var currentScanResult: String = ""

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val payload = QrScanActivity.extractResult(result.data)
        if (payload.isNullOrBlank()) return@registerForActivityResult
        currentScanResult = payload
        renderScanResult(payload)
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.featureHomeBtnQrBack.setOnClickListener { finish() }
        databind.featureHomeBtnCopyQrPayload.setOnClickListener {
            copyText(currentPayload, getString(R.string.feature_home_profile_qr_copied))
        }
        databind.featureHomeBtnStartQrScan.setOnClickListener {
            scanLauncher.launch(QrScanActivity.createIntent(this))
        }
        databind.featureHomeTvQrScanResult.setOnClickListener {
            copyText(currentScanResult, getString(R.string.feature_home_profile_qr_scan_copied))
        }
    }

    override fun createObserver() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUser(state)
                }
            }
        }
    }

    private fun renderUser(state: PersonalQrUiState) {
        currentPayload = state.payload
        databind.featureHomeTvQrNickname.text = state.nickname.ifBlank {
            getString(R.string.feature_home_profile_qr_empty_name)
        }
        val userIdText = state.userId.takeIf { it > 0 }?.toString()
            ?: getString(R.string.feature_home_profile_qr_empty_id)
        databind.featureHomeTvQrUserId.text = getString(
            R.string.feature_home_profile_qr_user_id,
            userIdText
        )
        databind.featureHomeIvQrAvatar.load(state.avatarUrl) {
            crossfade(true)
            placeholder(R.drawable.feature_home_ic_avatar_placeholder)
            error(R.drawable.feature_home_ic_avatar_placeholder)
            fallback(R.drawable.feature_home_ic_avatar_placeholder)
            transformations(CircleCropTransformation())
        }
        runCatching { createQrBitmap(state.payload, QR_SIZE_PX) }
            .onSuccess { databind.featureHomeIvQrCode.setImageBitmap(it) }
            .onFailure {
                viewModel.showToast(getString(R.string.feature_home_profile_qr_generate_failed))
            }
    }

    private fun renderScanResult(payload: String) {
        val userCard = PersonalQrPayload.parse(payload)
        databind.featureHomeTvQrScanResult.visibility = View.VISIBLE
        databind.featureHomeTvQrScanResult.text = if (userCard != null) {
            buildString {
                appendLine(getString(R.string.feature_home_profile_qr_scan_user))
                appendLine(
                    getString(
                        R.string.feature_home_profile_qr_scan_uid,
                        userCard.userId.toString()
                    )
                )
                append(
                    getString(
                        R.string.feature_home_profile_qr_scan_name,
                        userCard.nickname.ifBlank {
                            getString(R.string.feature_home_profile_qr_empty_name)
                        }
                    )
                )
            }
        } else {
            getString(R.string.feature_home_profile_qr_scan_raw, payload)
        }
    }

    private fun copyText(text: String, toast: String) {
        if (text.isBlank()) {
            viewModel.showToast(getString(R.string.feature_home_profile_qr_scan_empty))
            return
        }
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("profile_qr", text))
        viewModel.showToast(toast)
    }

    private fun createQrBitmap(payload: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    companion object {
        private const val QR_SIZE_PX = 720

        fun createIntent(context: Context): Intent {
            return Intent(context, PersonalQrActivity::class.java)
        }
    }
}
