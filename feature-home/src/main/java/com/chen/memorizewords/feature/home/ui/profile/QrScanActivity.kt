package com.chen.memorizewords.feature.home.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.FeatureHomeActivityQrScanBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScanActivity : AppCompatActivity() {

    private lateinit var binding: FeatureHomeActivityQrScanBinding
    private lateinit var cameraExecutor: ExecutorService
    private val didResolve = AtomicBoolean(false)
    private val scanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    private var cameraProvider: ProcessCameraProvider? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCamera()
            startCamera()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FeatureHomeActivityQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.featureHomeBtnQrScanBack.setOnClickListener { finish() }
        binding.featureHomeBtnRetryQrPermission.setOnClickListener { requestCameraPermission() }
        if (hasCameraPermission()) {
            showCamera()
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showCamera() {
        binding.featureHomePreviewQrScan.visibility = View.VISIBLE
        binding.featureHomePanelQrPermission.visibility = View.GONE
    }

    private fun showPermissionDenied() {
        binding.featureHomePreviewQrScan.visibility = View.GONE
        binding.featureHomePanelQrPermission.visibility = View.VISIBLE
        binding.featureHomeTvQrScanMessage.text =
            getString(R.string.feature_home_profile_qr_camera_denied)
    }

    private fun showCameraUnavailable() {
        binding.featureHomePreviewQrScan.visibility = View.GONE
        binding.featureHomePanelQrPermission.visibility = View.VISIBLE
        binding.featureHomeTvQrScanMessage.text =
            getString(R.string.feature_home_profile_qr_camera_unavailable)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val provider = runCatching { cameraProviderFuture.get() }.getOrNull()
                    ?: run {
                        showCameraUnavailable()
                        return@addListener
                    }
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.featureHomePreviewQrScan.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeImage(imageProxy)
                        }
                    }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                }.onFailure {
                    showCameraUnavailable()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (didResolve.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue?.trim() }
                if (!rawValue.isNullOrBlank() && didResolve.compareAndSet(false, true)) {
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_SCAN_RESULT, rawValue)
                    )
                    finish()
                }
            }
            .addOnFailureListener {
                if (!didResolve.get()) {
                    binding.featureHomeTvQrScanMessage.text =
                        getString(R.string.feature_home_profile_qr_scan_empty)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        scanner.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SCAN_RESULT = "feature_home.extra.SCAN_RESULT"

        fun createIntent(context: Context): Intent {
            return Intent(context, QrScanActivity::class.java)
        }

        fun extractResult(intent: Intent?): String? {
            return intent?.getStringExtra(EXTRA_SCAN_RESULT)
        }
    }
}
