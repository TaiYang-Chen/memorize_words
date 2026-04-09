package com.chen.memorizewords.feature.learning.ui.practice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.service.practice.PracticeFacade
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.ui.speech.playSpeechOutputSuspending
import com.chen.memorizewords.speech.api.SpeechAudioOutput
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechTask
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AudioLoopService : Service() {

    companion object {
        const val ACTION_START = "practice_audio_loop_start"
        const val ACTION_STOP = "practice_audio_loop_stop"
        const val ACTION_NEXT = "practice_audio_loop_next"

        private const val CHANNEL_ID = "practice_audio_loop_channel"
        private const val NOTIFICATION_ID = 5231
    }

    @Inject
    lateinit var synthesizeSpeech: SynthesizeSpeechUseCase

    @Inject
    lateinit var wordReadFacade: WordReadFacade

    @Inject
    lateinit var wordProvider: PracticeWordProvider

    @Inject
    lateinit var practiceFacade: PracticeFacade

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var loopJob: Job? = null
    private var words: List<Word> = emptyList()
    private var index: Int = 0
    private var selectedIds: LongArray? = null
    private var randomCount: Int = 20
    private var startRequestToken: Int = 0
    private var requestedEntryType: PracticeEntryType = PracticeEntryType.RANDOM

    private var sessionEntryType: PracticeEntryType = PracticeEntryType.RANDOM
    private var sessionEntryCount: Int = 0
    private var sessionWordIds: List<Long> = emptyList()
    private var sessionCreatedAt: Long = 0L
    private var sessionStartElapsedMs: Long? = null
    private var sessionTotalDurationMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        selectedIds = resolveAudioLoopSelectedIds(
            action = intent?.action,
            currentSelectedIds = selectedIds,
            incomingSelectedIds = intent?.getLongArrayExtra(PracticeActivity.EXTRA_SELECTED_WORD_IDS)
        )
        requestedEntryType = resolveAudioLoopRequestedEntryType(
            action = intent?.action,
            currentEntryType = requestedEntryType,
            incomingEntryType = intent?.getStringExtra(PracticeActivity.EXTRA_ENTRY_TYPE)
        )
        randomCount = intent?.getIntExtra(PracticeActivity.EXTRA_RANDOM_COUNT, randomCount) ?: randomCount
        when (intent?.action) {
            ACTION_START -> replaceLoopWithLatestSelection()
            ACTION_STOP -> stopLoop()
            ACTION_NEXT -> next()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopPlaybackLoop()
        finishCurrentSession()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun replaceLoopWithLatestSelection() {
        val requestToken = nextAudioLoopStartRequestToken(startRequestToken)
        startRequestToken = requestToken
        stopPlaybackLoop()
        finishCurrentSession()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.practice_audio_loop_notification_ready))
        )
        serviceScope.launch {
            val loadedWords = wordProvider.loadWords(
                selectedIds = selectedIds,
                randomCount = randomCount,
                defaultLimit = 50
            )
            if (!shouldApplyAudioLoopStartRequest(startRequestToken, requestToken)) return@launch
            if (loadedWords.isEmpty()) {
                stopLoop()
                return@launch
            }
            startNewSession(loadedWords)
            words = loadedWords
            index = 0
            startPlaybackLoop()
        }
    }

    private fun startPlaybackLoop() {
        stopPlaybackLoop()
        loopJob = serviceScope.launch {
            while (isActive) {
                val current = words.getOrNull(index) ?: break
                val settings = practiceFacade.getSettings()
                updateNotification(
                    getString(R.string.practice_audio_loop_notification_playing, current.word)
                )
                playWord(current, settings)

                if (settings.loopEnabled) {
                    index = (index + 1) % words.size
                } else {
                    val nextIndex = index + 1
                    if (nextIndex > words.lastIndex) {
                        stopLoop()
                        break
                    }
                    index = nextIndex
                }
                delay(settings.intervalSeconds.coerceAtLeast(1) * 1000L)
            }
        }
    }

    private fun next() {
        if (words.isEmpty()) return
        index = (index + 1) % words.size
        startPlaybackLoop()
    }

    private fun stopLoop() {
        startRequestToken = nextAudioLoopStartRequestToken(startRequestToken)
        stopPlaybackLoop()
        finishCurrentSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopPlaybackLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    private fun startNewSession(loadedWords: List<Word>) {
        sessionEntryType = requestedEntryType
        sessionWordIds = loadedWords.map { it.id }
        sessionEntryCount = sessionWordIds.size
        sessionCreatedAt = System.currentTimeMillis()
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
        sessionTotalDurationMs = 0L
    }

    private fun finishCurrentSession() {
        val totalDurationMs = consumeCurrentSessionDuration()
        if (totalDurationMs <= 0L || sessionWordIds.isEmpty()) {
            resetSessionState()
            return
        }
        val record = PracticeSessionRecord(
            id = 0L,
            date = "",
            mode = PracticeMode.AUDIO_LOOP,
            entryType = sessionEntryType,
            entryCount = sessionEntryCount,
            durationMs = totalDurationMs,
            createdAt = sessionCreatedAt,
            wordIds = sessionWordIds
        )
        resetSessionState()
        serviceScope.launch(Dispatchers.IO) {
            practiceFacade.addPracticeDuration(totalDurationMs)
            practiceFacade.saveSessionRecord(record)
        }
    }

    private fun consumeCurrentSessionDuration(): Long {
        val startElapsed = sessionStartElapsedMs
        if (startElapsed != null) {
            sessionTotalDurationMs += (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(0L)
            sessionStartElapsedMs = null
        }
        return sessionTotalDurationMs
    }

    private fun resetSessionState() {
        sessionEntryType = PracticeEntryType.RANDOM
        sessionEntryCount = 0
        sessionWordIds = emptyList()
        sessionCreatedAt = 0L
        sessionStartElapsedMs = null
        sessionTotalDurationMs = 0L
    }

    private suspend fun playWord(word: Word, settings: PracticeSettings) {
        speakText(word.word, "en-US")
        if (settings.playWordSpelling) {
            val spelling = word.word.toCharArray().joinToString(" ") { it.toString() }
            speakText(spelling, "en-US")
        }
        if (settings.playChineseMeaning) {
            val definition = wordReadFacade.getWordDefinitions(word.id).firstOrNull()
            val meaning = definition?.meaningChinese?.takeIf { it.isNotBlank() } ?: return
            speakText(meaning, "zh-CN")
        }
    }

    private suspend fun speakText(
        text: String,
        language: String
    ) {
        if (text.isBlank()) return
        val result = synthesizeSpeech(
            SpeechTask.SynthesizeSentence(
                text = text,
                locale = language
            )
        )
        val audio = (result as? SpeechAudioSuccess)?.audioOutput ?: return
        if (audio is SpeechAudioOutput.StreamOutput) return
        playSpeechOutputSuspending(audio)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.practice_audio_loop_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.module_learning_ic_volume_up)
            .setContentTitle(getString(R.string.practice_audio_loop_notification_title))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}

internal fun resolveAudioLoopSelectedIds(
    action: String?,
    currentSelectedIds: LongArray?,
    incomingSelectedIds: LongArray?
): LongArray? {
    return if (action == AudioLoopService.ACTION_START) {
        incomingSelectedIds?.takeIf { it.isNotEmpty() }
    } else {
        currentSelectedIds
    }
}

internal fun resolveAudioLoopRequestedEntryType(
    action: String?,
    currentEntryType: PracticeEntryType,
    incomingEntryType: String?
): PracticeEntryType {
    if (action != AudioLoopService.ACTION_START) return currentEntryType
    return runCatching { PracticeEntryType.valueOf(incomingEntryType.orEmpty()) }
        .getOrDefault(PracticeEntryType.RANDOM)
}

internal fun nextAudioLoopStartRequestToken(currentToken: Int): Int {
    return currentToken + 1
}

internal fun shouldApplyAudioLoopStartRequest(activeToken: Int, requestToken: Int): Boolean {
    return activeToken == requestToken
}
