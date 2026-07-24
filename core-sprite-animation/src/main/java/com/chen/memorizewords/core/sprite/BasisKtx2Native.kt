package com.chen.memorizewords.core.sprite

/** Thin, thread-confined JNI bridge around the official Basis Universal KTX2 transcoder. */
internal object BasisKtx2Native {
    private val loadLock = Any()

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (!loaded) {
                System.loadLibrary("sprite_basis")
                loaded = true
            }
        }
    }

    @JvmStatic
    external fun nativeCreate(path: String): Long

    /** Returns width, height, page/layer count, and an integer alpha flag. */
    @JvmStatic
    external fun nativeGetInfo(handle: Long): IntArray

    @JvmStatic
    external fun nativeTranscodePage(
        handle: Long,
        pageIndex: Int,
        target: Int,
        decodeAlpha: Boolean
    ): ByteArray

    @JvmStatic
    external fun nativeDestroy(handle: Long)

    const val TARGET_ETC1_RGB = 0
    const val TARGET_ETC2_RGBA = 1
}
