package com.chen.memorizewords.feature.learning.ui.practice

import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal object ShadowingWaveformExtractor {

    fun extractPeaks(file: File, targetPeakCount: Int): List<WaveformPeak> {
        if (!file.exists() || targetPeakCount <= 0) return emptyList()
        val bytes = file.readBytes()
        val dataOffset = findDataOffset(bytes)
        if (dataOffset < 0 || dataOffset >= bytes.size - BYTES_PER_SAMPLE) return emptyList()

        val sampleCount = (bytes.size - dataOffset) / BYTES_PER_SAMPLE
        if (sampleCount <= 0) return emptyList()

        val peakCount = min(targetPeakCount, sampleCount).coerceAtLeast(1)
        val samplesPerPeak = max(1, sampleCount / peakCount)
        val peaks = ArrayList<WaveformPeak>(peakCount)
        var sampleIndex = 0

        while (sampleIndex < sampleCount && peaks.size < peakCount) {
            val windowEnd = min(sampleCount, sampleIndex + samplesPerPeak)
            var minSample = 0
            var maxSample = 0
            var energy = 0.0
            var count = 0
            var index = sampleIndex
            while (index < windowEnd) {
                val byteIndex = dataOffset + index * BYTES_PER_SAMPLE
                val sample = littleEndianShort(bytes, byteIndex)
                minSample = min(minSample, sample)
                maxSample = max(maxSample, sample)
                energy += sample.toDouble() * sample.toDouble()
                count++
                index++
            }
            if (count > 0) {
                peaks += WaveformPeak(
                    min = (minSample / MAX_PCM_VALUE).coerceIn(-1f, 0f),
                    max = (maxSample / MAX_PCM_VALUE).coerceIn(0f, 1f),
                    rms = (sqrt(energy / count) / MAX_PCM_VALUE).toFloat().coerceIn(0f, 1f)
                )
            }
            sampleIndex = windowEnd
        }
        return peaks
    }

    private fun findDataOffset(bytes: ByteArray): Int {
        var index = 12
        while (index + 8 <= bytes.size) {
            val chunkId = String(bytes, index, 4, Charsets.US_ASCII)
            val chunkSize = littleEndianInt(bytes, index + 4)
            val payloadOffset = index + 8
            if (chunkId == "data") {
                return payloadOffset
            }
            index = payloadOffset + chunkSize.coerceAtLeast(0)
            if (index % 2 != 0) index++
        }
        return if (bytes.size > WAV_HEADER_BYTES) WAV_HEADER_BYTES else -1
    }

    private fun littleEndianShort(bytes: ByteArray, index: Int): Int {
        val low = bytes[index].toInt() and 0xff
        val high = bytes[index + 1].toInt()
        return (high shl 8) or low
    }

    private fun littleEndianInt(bytes: ByteArray, index: Int): Int {
        return (bytes[index].toInt() and 0xff) or
            ((bytes[index + 1].toInt() and 0xff) shl 8) or
            ((bytes[index + 2].toInt() and 0xff) shl 16) or
            ((bytes[index + 3].toInt() and 0xff) shl 24)
    }

    private const val BYTES_PER_SAMPLE = 2
    private const val WAV_HEADER_BYTES = 44
    private const val MAX_PCM_VALUE = 32768f
}
