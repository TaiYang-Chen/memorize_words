package com.chen.memorizewords.feature.user.ui.profile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import coil.transform.CircleCropTransformation
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.social.QQAuthProvider
import com.chen.memorizewords.feature.user.auth.social.WeChatAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentProfileBinding
import com.chen.memorizewords.feature.user.ui.profile.avatar.AvatarActionBottomSheetDialog
import com.chen.memorizewords.feature.user.ui.profile.avatar.AvatarImageProcessor
import com.yalantis.ucrop.UCrop
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class ProfileFragment : BaseFragment<ProfileViewModel, ModuleUserFragmentProfileBinding>() {

    override val viewModel: ProfileViewModel by lazy {
        ViewModelProvider(this)[ProfileViewModel::class.java]
    }

    @Inject
    lateinit var wechatAuthProvider: WeChatAuthProvider

    @Inject
    lateinit var qqAuthProvider: QQAuthProvider

    private var pendingCameraUri: Uri? = null

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@registerForActivityResult
        launchAvatarCrop(uri)
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@registerForActivityResult
        pendingCameraUri?.let { launchAvatarCrop(it) }
        pendingCameraUri = null
    }

    private val cropImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val croppedUri = result.data?.let(UCrop::getOutput)
                if (croppedUri == null) {
                    viewModel.showToast(getString(R.string.module_user_profile_get_cropped_avatar_failed))
                    return@registerForActivityResult
                }
                processAndUploadAvatar(croppedUri)
            }

            Activity.RESULT_CANCELED -> Unit
            else -> {
                val error = result.data?.let(UCrop::getError)
                viewModel.showToast(
                    error?.message ?: getString(R.string.module_user_profile_crop_avatar_failed)
                )
            }
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.ivAvatar.clipToOutline = true
        observeAvatar()
    }

    override fun createObserver() {
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (val target = event.target) {
            is ProfileViewModel.Route.ToAvatarPreview -> {
                findNavController().navigate(
                    R.id.action_module_login_profilefragment_to_avatarPreviewFragment,
                    Bundle().apply { putString("avatarSource", target.avatarSource) }
                )
            }

            ProfileViewModel.Route.ToChangePassword -> {
                findNavController().navigate(
                    R.id.action_module_login_profilefragment_to_changePasswordFragment
                )
            }

            ProfileViewModel.Route.ToDeleteAccountConfirm -> {
                findNavController().navigate(
                    R.id.action_module_login_profilefragment_to_deleteAccountConfirmFragment
                )
            }

            ProfileViewModel.Route.ToBindPhone -> {
                findNavController().navigate(
                    R.id.action_module_login_profilefragment_to_bindPhoneFragment
                )
            }
        }
    }

    override fun customConfirmDialog(event: UiEvent.Dialog.CustomConfirmDialog) {
        when (event.custom) {
            ProfileViewModel.DIALOG_CHANGE_GENDER -> {
                ChangeGenderBottomDialog(
                    confirm = viewModel::confirmGenderChange
                ).show(parentFragmentManager, "ShowConfirmEditDialog")
            }

            ProfileViewModel.DIALOG_BIND_WECHAT -> startWechatAuth()
            ProfileViewModel.DIALOG_BIND_QQ -> startQqAuth()
            ProfileViewModel.DIALOG_AVATAR_ACTIONS -> showAvatarActions()
        }
    }

    override fun onConfirmEditDialog(event: UiEvent.Dialog.ConfirmEdit, value: String) {
        if (event.action == ProfileViewModel.ACTION_CHANGE_NICKNAME) {
            viewModel.confirmNicknameChange(value)
            return
        }
        super.onConfirmEditDialog(event, value)
    }

    private fun observeAvatar() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.user.collect { user ->
                    databind.ivAvatar.load(user?.avatarUrl) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.module_user_ic_avatar_placeholder)
                        error(R.drawable.module_user_ic_avatar_placeholder)
                        fallback(R.drawable.module_user_ic_avatar_placeholder)
                    }
                }
            }
        }
    }

    private fun showAvatarActions() {
        AvatarActionBottomSheetDialog(
            onViewAvatar = {
                viewModel.openAvatarPreview()
            },
            onPickFromGallery = {
                pickMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = {
                launchTakePhoto()
            }
        ).show(parentFragmentManager, "AvatarActionBottomSheetDialog")
    }

    private fun launchTakePhoto() {
        val photoUri = createTempAvatarUri("avatar_capture_") ?: run {
            viewModel.showToast(getString(R.string.module_user_profile_create_camera_file_failed))
            return
        }
        pendingCameraUri = photoUri
        takePictureLauncher.launch(photoUri)
    }

    private fun createTempAvatarUri(filePrefix: String): Uri? = runCatching {
        val cacheDir = File(requireContext().cacheDir, "avatar").apply {
            if (!exists()) mkdirs()
        }
        val file = File.createTempFile(filePrefix, ".jpg", cacheDir)
        FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
    }.getOrNull()

    private fun launchAvatarCrop(sourceUri: Uri) {
        val destinationUri = createTempAvatarUri("avatar_crop_") ?: run {
            viewModel.showToast(getString(R.string.module_user_profile_create_crop_file_failed))
            return
        }
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(100)
            setToolbarTitle(getString(R.string.module_user_profile_crop_avatar_toolbar))
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
            setShowCropGrid(true)
            setShowCropFrame(true)
        }
        val intent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1440, 1440)
            .withOptions(options)
            .getIntent(requireContext())
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        cropImageLauncher.launch(intent)
    }

    private fun processAndUploadAvatar(uri: Uri) {
        lifecycleScope.launch {
            val imageBytesResult = withContext(Dispatchers.Default) {
                AvatarImageProcessor.process(requireContext(), uri)
            }
            imageBytesResult.onSuccess { bytes ->
                viewModel.changeAvatar(bytes)
            }.onFailure { throwable ->
                viewModel.showToast(
                    throwable.message ?: getString(R.string.module_user_profile_process_avatar_failed)
                )
            }
        }
    }

    private fun startWechatAuth() {
        lifecycleScope.launch {
            wechatAuthProvider.requestAuth(requireActivity()).onSuccess { credential ->
                viewModel.bindWechat(
                    oauthCode = credential.oauthCode,
                    state = credential.state
                )
            }.onFailure { failure ->
                viewModel.showToast(
                    failure.message ?: getString(R.string.module_user_profile_bind_wechat_failed)
                )
            }
        }
    }

    private fun startQqAuth() {
        lifecycleScope.launch {
            qqAuthProvider.requestAuth(requireActivity()).onSuccess { credential ->
                viewModel.bindQq(
                    oauthCode = credential.oauthCode,
                    state = credential.state
                )
            }.onFailure { failure ->
                viewModel.showToast(
                    failure.message ?: getString(R.string.module_user_profile_bind_qq_failed)
                )
            }
        }
    }
}
