package com.chen.memorizewords.feature.learning.ui.practice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max

internal class ShadowingWavRecorder(
    private val sampleRate: Int = SAMPLE_RATE,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    data class Result(
        val file: File,
        val durationMs: Long
    )

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L
    @Volatile private var recording: Boolean = false

    fun start(
        file: File,
        onAmplitude: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stopCurrent()
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBuffer, sampleRate / 5 * BYTES_PER_SAMPLE)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord initialization failed.")
        }
        outputFile = file
        audioRecord = recorder
        recording = true
        startedAtMs = System.currentTimeMillis()
        recordingThread = Thread({
            runCatching {
                writeWav(recorder, file, bufferSize, onAmplitude)
            }.onFailure { error ->
                onError(error)
            }
        }, "shadowing-wav-recorder").also { thread ->
            thread.start()
        }
    }

    fun stop(): Result? {
        val file = outputFile ?: return null
        val durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        stopCurrent()
        return if (file.exists() && file.length() > WAV_HEADER_BYTES) {
            Result(file, durationMs)
        } else {
            null
        }
    }

    fun cancel() {
        stopCurrent()
        outputFile?.delete()
        outputFile = null
    }

    private fun stopCurrent() {
        recording = false
        runCatching { audioRecord?.stop() }
        runCatching { recordingThread?.join(700L) }
        runCatching { audioRecord?.release() }
        audioRecord = null
        recordingThread = null
        startedAtMs = 0L
    }

    private fun writeWav(
        recorder: AudioRecord,
        file: File,
        bufferSize: Int,
        onAmplitude: (Int) -> Unit
    ) {
        val buffer = ByteArray(bufferSize)
        var pcmBytes = 0L
        RandomAccessFile(file, "rw").use { output ->
            output.setLength(0L)
            writeHeader(output, 0L)
            recorder.startRecording()
            while (recording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                output.write(buffer, 0, read)
                pcmBytes += read
                onAmplitude(maxAmplitude(buffer, read))
            }
            writeHeader(output, pcmBytes)
        }
    }

    private fun writeHeader(output: RandomAccessFile, pcmBytes: Long) {
        val byteRate = sampleRate * BYTES_PER_SAMPLE
        output.seek(0L)
        output.writeBytes("RIFF")
        output.writeIntLe((36L + pcmBytes).toInt())
        output.writeBytes("WAVE")
        output.writeBytes("fmt ")
        output.writeIntLe(16)
        output.writeShortLe(1)
        output.writeShortLe(1)
        output.writeIntLe(sampleRate)
        output.writeIntLe(byteRate)
        output.writeShortLe(BYTES_PER_SAMPLE)
        output.writeShortLe(16)
        output.writeBytes("data")
        output.writeIntLe(pcmBytes.toInt())
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }

    private fun maxAmplitude(buffer: ByteArray, read: Int): Int {
        var max = 0
        var index = 0
        while (index + 1 < read) {
            val sample = (buffer[index].toInt() and 0xff) or (buffer[index + 1].toInt() shl 8)
            max = max(max, abs(sample))
            index += 2
        }
        return max
    }

    private companion object {
        const val SAMPLE_RATE = 16000
        const val BYTES_PER_SAMPLE = 2
        const val WAV_HEADER_BYTES = 44
    }
}
