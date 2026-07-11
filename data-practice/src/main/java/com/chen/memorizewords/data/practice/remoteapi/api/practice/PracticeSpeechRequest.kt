package com.chen.memorizewords.data.practice.remoteapi.api.practice

import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PracticeSpeechRequest @Inject constructor(
    private val apiService: PracticeApiService,
    private val requestExecutor: NetworkRequestExecutor
) {
    suspend fun synthesize(request: TtsRequestDto): NetworkResult<TtsResponseDto> =
        requestExecutor.executeAuthenticated {
            apiService.synthesize(request).await<ApiResponse<TtsResponseDto>, TtsResponseDto>()
        }

    suspend fun evaluateShadowing(
        requestId: String,
        referenceText: String,
        provider: String,
        audioFilePath: String,
        audioFormat: String? = null,
        durationMs: Long? = null,
        waveformSamples: List<Int>? = null
    ): NetworkResult<ShadowingEvaluateResponseDto> = requestExecutor.executeAuthenticated {
        val file = File(audioFilePath)
        if (!file.exists() || !file.isFile) {
            return@executeAuthenticated NetworkResult.Failure.GenericError("Audio file not found")
        }
        if (file.length() > MAX_EVALUATION_AUDIO_BYTES) {
            return@executeAuthenticated NetworkResult.Failure.HttpError(
                code = 413,
                message = "Recording is too large",
                businessCode = "REQUEST_PAYLOAD_TOO_LARGE"
            )
        }
        val payload = withContext(Dispatchers.IO) {
            encodeAudioFileToBase64(file)
        }
        val req = ShadowingEvaluateRequestDto(
            requestId = requestId,
            referenceText = referenceText,
            provider = provider,
            audioBase64 = payload,
            audioFormat = audioFormat,
            durationMs = durationMs,
            waveformSamples = waveformSamples
                ?.take(MAX_WAVEFORM_SAMPLES)
                ?.map { it.coerceIn(MIN_WAVEFORM_SAMPLE, MAX_WAVEFORM_SAMPLE) }
        )
        apiService.evaluateShadowing(req)
            .await<ApiResponse<ShadowingEvaluateResponseDto>, ShadowingEvaluateResponseDto>()
    }
}

internal fun encodeAudioFileToBase64(file: File): String {
    val estimatedSize = ((file.length() + 2L) / 3L * 4L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    val output = StringBuilder(estimatedSize)
    val readBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val remainder = ByteArray(3)
    var remainderSize = 0

    file.inputStream().use { input ->
        while (true) {
            val read = input.read(readBuffer)
            if (read <= 0) break

            var index = 0
            if (remainderSize > 0) {
                while (remainderSize < 3 && index < read) {
                    remainder[remainderSize++] = readBuffer[index++]
                }
                if (remainderSize == 3) {
                    appendBase64Triplet(output, remainder[0], remainder[1], remainder[2])
                    remainderSize = 0
                }
            }

            val fullTripletEnd = index + ((read - index) / 3) * 3
            while (index < fullTripletEnd) {
                appendBase64Triplet(
                    output,
                    readBuffer[index],
                    readBuffer[index + 1],
                    readBuffer[index + 2]
                )
                index += 3
            }

            remainderSize = read - index
            if (remainderSize > 0) {
                System.arraycopy(readBuffer, index, remainder, 0, remainderSize)
            }
        }
    }

    when (remainderSize) {
        1 -> appendBase64Remainder(output, remainder[0], null)
        2 -> appendBase64Remainder(output, remainder[0], remainder[1])
    }

    return output.toString()
}

private fun appendBase64Triplet(output: StringBuilder, b0: Byte, b1: Byte, b2: Byte) {
    val i0 = b0.toInt() and 0xff
    val i1 = b1.toInt() and 0xff
    val i2 = b2.toInt() and 0xff
    output.append(BASE64_ALPHABET[i0 ushr 2])
    output.append(BASE64_ALPHABET[((i0 and 0x03) shl 4) or (i1 ushr 4)])
    output.append(BASE64_ALPHABET[((i1 and 0x0f) shl 2) or (i2 ushr 6)])
    output.append(BASE64_ALPHABET[i2 and 0x3f])
}

private fun appendBase64Remainder(output: StringBuilder, first: Byte, second: Byte?) {
    val i0 = first.toInt() and 0xff
    output.append(BASE64_ALPHABET[i0 ushr 2])
    if (second == null) {
        output.append(BASE64_ALPHABET[(i0 and 0x03) shl 4])
        output.append('=')
        output.append('=')
        return
    }

    val i1 = second.toInt() and 0xff
    output.append(BASE64_ALPHABET[((i0 and 0x03) shl 4) or (i1 ushr 4)])
    output.append(BASE64_ALPHABET[(i1 and 0x0f) shl 2])
    output.append('=')
}

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
private const val MAX_EVALUATION_AUDIO_BYTES = 2L * 1024L * 1024L
private const val MAX_WAVEFORM_SAMPLES = 6_000
private const val MIN_WAVEFORM_SAMPLE = 0
private const val MAX_WAVEFORM_SAMPLE = 100
