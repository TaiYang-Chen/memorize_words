package com.chen.memorizewords.core.sprite

import java.io.File

/**
 * Performs the expensive install-time/runtime-readiness validation that structural header parsing
 * cannot provide: official Basis parsing, DFD color/alpha checks, and full-page transcoding.
 */
object Ktx2BasisRuntimeValidator {
    fun validateAllPages(file: File, spec: Ktx2PagedTextureSpec) {
        require(file.isFile) { "KTX2 file is missing" }
        BasisKtx2Native.ensureLoaded()
        val handle = BasisKtx2Native.nativeCreate(file.absolutePath)
        require(handle != 0L) { "Official Basis transcoder rejected ${file.name}" }
        try {
            val info = BasisKtx2Native.nativeGetInfo(handle)
            require(info.size >= REQUIRED_INFO_FIELDS) { "Incomplete native KTX2 metadata" }
            require(info[0] == spec.pageWidth && info[1] == spec.pageHeight) {
                "Native KTX2 dimensions do not match the manifest"
            }
            require(info[2] == spec.pageCount) {
                "Native KTX2 layer count does not match the manifest"
            }
            val hasAlpha = info[3] != 0
            require(hasAlpha == (spec.alphaMode != SpriteAlphaMode.OPAQUE)) {
                "Native KTX2 alpha metadata does not match the manifest"
            }
            require((info[IS_SRGB_INDEX] != 0) == (spec.colorSpace == SpriteColorSpace.SRGB)) {
                "Native KTX2 transfer function does not match the manifest"
            }
            require(info[COLOR_MODEL_INDEX] == UASTC_LDR_COLOR_MODEL) {
                "Native KTX2 DFD is not UASTC LDR"
            }
            require(info[TOTAL_SAMPLES_INDEX] == 1 && info[CHANNEL_ZERO_INDEX] == UASTC_RGBA) {
                "Native KTX2 DFD does not describe one RGBA sample"
            }
            require(info[DFD_FLAGS_INDEX] == STRAIGHT_ALPHA_FLAGS) {
                "Native KTX2 DFD is not straight alpha"
            }
            require(info[COLOR_PRIMARIES_INDEX] == BT709_PRIMARIES) {
                "Native KTX2 color primaries are not BT.709/sRGB"
            }

            val etc2Bytes = compressedByteCount(spec.pageWidth, spec.pageHeight, 16)
            val etc1Bytes = compressedByteCount(spec.pageWidth, spec.pageHeight, 8)
            repeat(spec.pageCount) { page ->
                require(
                    BasisKtx2Native.nativeTranscodePage(
                        handle,
                        page,
                        BasisKtx2Native.TARGET_ETC2_RGBA,
                        false
                    ).size == etc2Bytes
                ) { "Unable to fully transcode KTX2 page $page to ETC2 RGBA" }
                require(
                    BasisKtx2Native.nativeTranscodePage(
                        handle,
                        page,
                        BasisKtx2Native.TARGET_ETC1_RGB,
                        false
                    ).size == etc1Bytes
                ) { "Unable to fully transcode KTX2 page $page to ETC1 RGB" }
                if (hasAlpha) {
                    require(
                        BasisKtx2Native.nativeTranscodePage(
                            handle,
                            page,
                            BasisKtx2Native.TARGET_ETC1_RGB,
                            true
                        ).size == etc1Bytes
                    ) { "Unable to fully transcode KTX2 page $page to ETC1 alpha" }
                }
            }
        } finally {
            BasisKtx2Native.nativeDestroy(handle)
        }
    }

    private fun compressedByteCount(width: Int, height: Int, bytesPerBlock: Int): Int {
        val blocksWide = (width.toLong() + 3L) / 4L
        val blocksHigh = (height.toLong() + 3L) / 4L
        return Math.multiplyExact(
            Math.multiplyExact(blocksWide, blocksHigh),
            bytesPerBlock.toLong()
        ).let(Math::toIntExact)
    }

    private const val REQUIRED_INFO_FIELDS = 10
    private const val IS_SRGB_INDEX = 4
    private const val COLOR_MODEL_INDEX = 5
    private const val TOTAL_SAMPLES_INDEX = 6
    private const val CHANNEL_ZERO_INDEX = 7
    private const val DFD_FLAGS_INDEX = 8
    private const val COLOR_PRIMARIES_INDEX = 9
    private const val UASTC_LDR_COLOR_MODEL = 166
    private const val UASTC_RGBA = 3
    private const val STRAIGHT_ALPHA_FLAGS = 0
    private const val BT709_PRIMARIES = 1
}
