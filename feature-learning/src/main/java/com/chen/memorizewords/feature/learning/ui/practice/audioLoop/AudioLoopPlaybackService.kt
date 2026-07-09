package com.chen.memorizewords.feature.learning.ui.practice.audioLoop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.chen.memorizewords.domain.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.practice.AudioLoopPlayOrder
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.domain.practice.service.PracticeFacade
import com.chen.memorizewords.domain.practice.speech.SpeechAudioOutput
import com.chen.memorizewords.domain.practice.speech.SpeechTask
import com.chen.memorizewords.domain.practice.usecase.SynthesizeSpeechUseCase
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.ui.speech.audioOutputOrNull
import com.chen.memorizewords.feature.learning.ui.speech.prepareSpeechOutputAsync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@AndroidEntryPoint
class AudioLoopPlaybackService : Service() {

    @Inject
    lateinit var synthesizeSpeech: SynthesizeSpeechUseCase

    @Inject
    lateinit var practiceFacade: PracticeFacade

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playbackJob: Job? = null
    private var timedStopJob: Job? = null
    private var player: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentQueue: AudioLoopServiceQueue? = null
    private var currentIndex: Int = 0
    private var currentRepeat: Int = 1
    private var accumulatedPlaybackMs: Long = 0L
    private var completedIds = linkedSetOf<Long>()
    private var failedIds = linkedSetOf<Long>()
    private var hadSuccessfulPlayback = false
    @Volatile
    private var sessionPersisted = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (
            change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) {
            pausePlayback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = startPlayback()
                override fun onPause() = pausePlayback()
                override fun onSkipToNext() = skipBy(1)
                override fun onSkipToPrevious() = skipBy(-1)
                override fun onStop() = stopPlayback(stopSelfAfter = true)
            })
            isActive = true
        }
        startForeground(NOTIFICATION_ID, buildNotification(AudioLoopPlaybackStore.state.value))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_QUEUE -> ensureQueueLoadedFromStore(preserveState = true)
            ACTION_PLAY -> startPlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_NEXT -> skipBy(1)
            ACTION_PREVIOUS -> skipBy(-1)
            ACTION_STOP -> stopPlayback(stopSelfAfter = true)
            ACTION_TOGGLE -> {
                if (AudioLoopPlaybackStore.state.value.playerState == AudioLoopServicePlayerState.PLAYING) {
                    pausePlayback()
                } else {
                    startPlayback()
                }
            }
            ACTION_SELECT -> selectIndex(intent.getIntExtra(EXTRA_INDEX, 0))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopPlayback(stopSelfAfter = false)
        mediaSession?.release()
        mediaSession = null
        serviceScope.launch(Dispatchers.IO) { persistSessionIfNeeded() }
        super.onDestroy()
    }

    private fun ensureQueueLoadedFromStore(preserveState: Boolean): Boolean {
        val incoming = AudioLoopPlaybackStore.queue.value ?: return false
        val storeState = AudioLoopPlaybackStore.state.value
        val currentEntryId = if (preserveState) storeState.currentEntry?.id else null
        currentQueue = if (incoming.settings.playOrder == AudioLoopPlayOrder.RANDOM) {
            incoming.copy(entries = incoming.entries.shuffled())
        } else {
            incoming
        }
        val entries = currentQueue?.entries.orEmpty()
        val canPreserveIndex = preserveState &&
            currentEntryId != null &&
            entries.any { it.id == currentEntryId }
        currentIndex = if (canPreserveIndex) {
            entries.indexOfFirst { it.id == currentEntryId }
        } else if (preserveState && storeState.entries.isNotEmpty()) {
            storeState.currentIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
        } else {
            0
        }
        val sameEntries = storeState.entries.map { it.id } == incoming.entries.map { it.id }
        val canPreserveProgress = preserveState && sameEntries
        currentRepeat = if (canPreserveProgress) storeState.currentRepeat.coerceAtLeast(1) else 1
        if (canPreserveProgress) {
            completedIds = LinkedHashSet(storeState.completedIds)
            failedIds = LinkedHashSet(storeState.failedIds)
            hadSuccessfulPlayback = hadSuccessfulPlayback || completedIds.isNotEmpty()
        } else {
            completedIds.clear()
            failedIds.clear()
            hadSuccessfulPlayback = false
            sessionPersisted = false
            accumulatedPlaybackMs = 0L
        }
        val nextPlayerState = if (preserveState) storeState.playerState else AudioLoopServicePlayerState.WAITING
        val nextRepeat = if (canPreserveProgress) storeState.currentRepeat else 0
        publishCurrentPosition(playerState = nextPlayerState, repeat = nextRepeat)
        scheduleTimedStop(incoming.settings)
        return true
    }

    private fun startPlayback() {
        val queue = currentQueue ?: run {
            ensureQueueLoadedFromStore(preserveState = true)
            currentQueue
        } ?: return
        if (queue.entries.isEmpty()) return
        if (!requestAudioFocus()) {
            AudioLoopPlaybackStore.message(getString(R.string.practice_audio_unavailable))
            return
        }
        playbackJob?.cancel()
        playbackJob = serviceScope.launch {
            runPlaybackLoop(queue)
        }
    }

    private suspend fun runPlaybackLoop(initialQueue: AudioLoopServiceQueue) {
        var queue = currentQueue ?: initialQueue
        while (currentCoroutineContext().isActive && queue.entries.isNotEmpty()) {
            val entry = queue.entries.getOrNull(currentIndex) ?: break
            updateStore(AudioLoopServicePlayerState.PREPARING) {
                copy(
                    currentIndex = this@AudioLoopPlaybackService.currentIndex,
                    currentRepeat = this@AudioLoopPlaybackService.currentRepeat,
                    totalRepeats = queue.settings.playTimes.coerceAtLeast(1)
                )
            }
            val cues = buildCues(entry, queue.settings)
            var entryHadSuccess = false
            repeat(queue.settings.playTimes.coerceAtLeast(1)) { repeatIndex ->
                currentRepeat = repeatIndex + 1
                updateStore(AudioLoopServicePlayerState.PLAYING) {
                    copy(currentRepeat = this@AudioLoopPlaybackService.currentRepeat)
                }
                for (cue in cues) {
                    if (!currentCoroutineContext().isActive) return
                    if (cue.kind == AudioLoopCueKind.SILENCE) {
                        delay(cue.delayAfterMs)
                    } else {
                        entryHadSuccess = playCue(cue, queue.settings) || entryHadSuccess
                    }
                    if (cue.delayAfterMs > 0L && cue.kind != AudioLoopCueKind.SILENCE) {
                        delay(cue.delayAfterMs)
                    }
                }
                if (repeatIndex < queue.settings.playTimes.coerceAtLeast(1) - 1) {
                    delay(queue.settings.intervalSeconds.coerceAtLeast(0) * 1000L)
                }
            }
            if (entryHadSuccess) {
                completedIds += entry.id
                hadSuccessfulPlayback = true
            } else {
                failedIds += entry.id
                AudioLoopPlaybackStore.message(
                    getString(R.string.feature_learning_audio_loop_audio_skipped, entry.word)
                )
            }
            updateStore(AudioLoopServicePlayerState.PLAYING) {
                copy(
                    completedIds = this@AudioLoopPlaybackService.completedIds.toSet(),
                    failedIds = this@AudioLoopPlaybackService.failedIds.toSet()
                )
            }
            val nextIndex = currentIndex + 1
            if (nextIndex <= queue.entries.lastIndex) {
                currentIndex = nextIndex
                currentRepeat = 1
                publishCurrentPosition(AudioLoopServicePlayerState.PREPARING, repeat = 1)
                delay(queue.settings.intervalSeconds.coerceAtLeast(0) * 1000L)
                continue
            }
            if (queue.settings.loopEnabled) {
                if (completedIds.isEmpty()) {
                    updateStore(AudioLoopServicePlayerState.WAITING) { copy(currentRepeat = 0) }
                    AudioLoopPlaybackStore.message(getString(R.string.feature_learning_audio_loop_round_failed))
                    return
                }
                currentIndex = 0
                currentRepeat = 1
                completedIds.clear()
                failedIds.clear()
                queue = if (queue.settings.playOrder == AudioLoopPlayOrder.RANDOM) {
                    queue.copy(entries = queue.entries.shuffled())
                } else {
                    queue
                }
                currentQueue = queue
                publishCurrentPosition(AudioLoopServicePlayerState.PREPARING, repeat = 1)
                delay(queue.settings.intervalSeconds.coerceAtLeast(0) * 1000L)
            } else {
                serviceScope.launch(Dispatchers.IO) { persistSessionIfNeeded() }
                updateStore(AudioLoopServicePlayerState.WAITING) { copy(currentRepeat = 0) }
                return
            }
        }
    }

    private fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        releasePlayer()
        abandonAudioFocus()
        updateStore(AudioLoopServicePlayerState.PAUSED) {
            copy(currentRepeat = this@AudioLoopPlaybackService.currentRepeat)
        }
    }

    private fun skipBy(delta: Int) {
        val queue = currentQueue ?: run {
            ensureQueueLoadedFromStore(preserveState = true)
            currentQueue
        } ?: return
        if (queue.entries.isEmpty()) return
        val target = (currentIndex + delta).coerceIn(0, queue.entries.lastIndex)
        if (target == currentIndex && delta != 0) return
        val wasPlaying = AudioLoopPlaybackStore.state.value.playerState == AudioLoopServicePlayerState.PLAYING ||
            AudioLoopPlaybackStore.state.value.playerState == AudioLoopServicePlayerState.PREPARING
        playbackJob?.cancel()
        releasePlayer()
        currentIndex = target
        currentRepeat = 1
        publishCurrentPosition(
            playerState = if (wasPlaying) AudioLoopServicePlayerState.PREPARING else AudioLoopServicePlayerState.WAITING,
            repeat = if (wasPlaying) 1 else 0
        )
        if (wasPlaying) startPlayback()
    }

    private fun selectIndex(index: Int) {
        val queue = currentQueue ?: run {
            ensureQueueLoadedFromStore(preserveState = true)
            currentQueue
        } ?: return
        if (index !in queue.entries.indices) return
        val wasPlaying = AudioLoopPlaybackStore.state.value.playerState == AudioLoopServicePlayerState.PLAYING ||
            AudioLoopPlaybackStore.state.value.playerState == AudioLoopServicePlayerState.PREPARING
        playbackJob?.cancel()
        releasePlayer()
        currentIndex = index
        currentRepeat = 1
        publishCurrentPosition(
            playerState = if (wasPlaying) AudioLoopServicePlayerState.PREPARING else AudioLoopServicePlayerState.WAITING,
            repeat = if (wasPlaying) 1 else 0
        )
        if (wasPlaying) startPlayback()
    }

    private fun stopPlayback(stopSelfAfter: Boolean) {
        playbackJob?.cancel()
        playbackJob = null
        timedStopJob?.cancel()
        timedStopJob = null
        releasePlayer()
        abandonAudioFocus()
        serviceScope.launch(Dispatchers.IO) { persistSessionIfNeeded() }
        updateStore(AudioLoopServicePlayerState.STOPPED) { copy(currentRepeat = 0) }
        if (stopSelfAfter) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun playCue(cue: AudioLoopServiceCue, settings: PracticeSettings): Boolean {
        val output = resolveAudioOutput(cue) ?: return false
        return playOutput(output, settings.playbackSpeed)
    }

    private suspend fun resolveAudioOutput(cue: AudioLoopServiceCue): SpeechAudioOutput? {
        if (cue.text.isBlank()) return null
        val task = when (cue.kind) {
            AudioLoopCueKind.WORD -> SpeechTask.SynthesizeWord(text = cue.text, locale = cue.locale)
            AudioLoopCueKind.SENTENCE -> SpeechTask.SynthesizeSentence(text = cue.text, locale = cue.locale)
            AudioLoopCueKind.SILENCE -> return null
        }
        return runCatching { synthesizeSpeech(task).audioOutputOrNull() }.getOrNull()
            ?.takeUnless { it is SpeechAudioOutput.StreamOutput }
    }

    private suspend fun playOutput(output: SpeechAudioOutput, speed: Float): Boolean {
        return suspendCancellableCoroutine { continuation ->
            releasePlayer()
            val newPlayer = MediaPlayer()
            var playbackStartedAtMs = 0L
            val started = newPlayer.prepareSpeechOutputAsync(
                output = output,
                onPrepared = { prepared ->
                    player = prepared
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching {
                            prepared.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.5f, 2.0f))
                        }
                    }
                    prepared.setOnCompletionListener {
                        addPlaybackElapsed(playbackStartedAtMs)
                        playbackStartedAtMs = 0L
                        releasePlayer()
                        if (continuation.isActive) continuation.resume(true)
                    }
                    playbackStartedAtMs = SystemClock.elapsedRealtime()
                    prepared.start()
                    updateStore(AudioLoopServicePlayerState.PLAYING) { this }
                },
                onError = {
                    addPlaybackElapsed(playbackStartedAtMs)
                    playbackStartedAtMs = 0L
                    releasePlayer()
                    if (continuation.isActive) continuation.resume(false)
                }
            )
            if (!started && continuation.isActive) {
                continuation.resume(false)
            }
            player = newPlayer
            continuation.invokeOnCancellation {
                addPlaybackElapsed(playbackStartedAtMs)
                playbackStartedAtMs = 0L
                releasePlayer()
            }
        }
    }

    private fun addPlaybackElapsed(startedAtMs: Long) {
        if (startedAtMs <= 0L) return
        accumulatedPlaybackMs += (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
    }

    private fun buildCues(
        entry: AudioLoopServiceEntry,
        settings: PracticeSettings
    ): List<AudioLoopServiceCue> {
        val wordDelay = if (settings.playbackMode == AudioLoopPlaybackMode.DICTATION) {
            settings.dictationPauseSeconds.coerceAtLeast(0) * 1000L
        } else {
            settings.revealDelaySeconds.coerceAtLeast(0) * 1000L
        }
        return buildList {
            repeat(settings.wordRepeatTimes.coerceAtLeast(1)) { index ->
                add(
                    AudioLoopServiceCue(
                        key = "word_${entry.id}_$index",
                        kind = AudioLoopCueKind.WORD,
                        text = entry.word,
                        locale = entry.speechLocale.ifBlank { "en-US" },
                        delayAfterMs = wordDelay
                    )
                )
            }
            when (settings.playbackMode) {
                AudioLoopPlaybackMode.WORD_ONLY -> Unit
                AudioLoopPlaybackMode.WORD_WITH_MEANING,
                AudioLoopPlaybackMode.DICTATION,
                AudioLoopPlaybackMode.FULL_DETAIL -> {
                    if (entry.meaning.isNotBlank()) {
                        add(
                            AudioLoopServiceCue(
                                key = "meaning_${entry.id}",
                                kind = AudioLoopCueKind.SENTENCE,
                                text = entry.meaning,
                                locale = "zh-CN"
                            )
                        )
                    }
                }
                AudioLoopPlaybackMode.WORD_WITH_EXAMPLE -> Unit
            }
            if (
                settings.playbackMode == AudioLoopPlaybackMode.WORD_WITH_EXAMPLE ||
                settings.playbackMode == AudioLoopPlaybackMode.FULL_DETAIL
            ) {
                repeat(settings.exampleRepeatTimes.coerceAtLeast(1)) { index ->
                    if (entry.exampleSentence.isNotBlank()) {
                        add(
                            AudioLoopServiceCue(
                                key = "example_${entry.id}_$index",
                                kind = AudioLoopCueKind.SENTENCE,
                                text = entry.exampleSentence,
                                locale = "en-US"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun releasePlayer() {
        val old = player
        player = null
        runCatching { old?.stop() }
        runCatching { old?.release() }
    }

    private fun scheduleTimedStop(settings: PracticeSettings) {
        timedStopJob?.cancel()
        val minutes = settings.timedStopMinutes
        if (minutes <= 0) return
        timedStopJob = serviceScope.launch {
            delay(minutes * 60_000L)
            stopPlayback(stopSelfAfter = true)
        }
    }

    private suspend fun persistSessionIfNeeded() {
        val queue = currentQueue ?: return
        if (sessionPersisted || !hadSuccessfulPlayback || completedIds.isEmpty() || accumulatedPlaybackMs <= 0L) return
        sessionPersisted = true
        val duration = accumulatedPlaybackMs.coerceAtLeast(1L)
        runCatching { practiceFacade.addPracticeDuration(duration) }
        runCatching {
            practiceFacade.saveSessionRecord(
                PracticeSessionRecord(
                    id = 0L,
                    date = "",
                    mode = PracticeMode.AUDIO_LOOP,
                    entryType = queue.entryType,
                    entryCount = queue.entries.size,
                    durationMs = duration,
                    createdAtMs = queue.createdAtMs,
                    wordIds = queue.entries.map { it.id },
                    questionCount = queue.entries.size,
                    completedCount = completedIds.size,
                    correctCount = 0,
                    submitCount = completedIds.size
                )
            )
        }
        hadSuccessfulPlayback = false
        accumulatedPlaybackMs = 0L
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = ContextCompat.getSystemService(this, AudioManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = ContextCompat.getSystemService(this, AudioManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun updateStore(
        playerState: AudioLoopServicePlayerState,
        reducer: AudioLoopServiceState.() -> AudioLoopServiceState
    ) {
        AudioLoopPlaybackStore.updateState {
            it.reducer().copy(playerState = playerState)
        }
        val state = AudioLoopPlaybackStore.state.value
        updateMediaSession(state)
        val notification = buildNotification(state)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun publishCurrentPosition(
        playerState: AudioLoopServicePlayerState,
        repeat: Int = currentRepeat
    ) {
        val queue = currentQueue
        updateStore(playerState) {
            copy(
                entries = queue?.entries ?: entries,
                currentIndex = this@AudioLoopPlaybackService.currentIndex,
                currentRepeat = repeat,
                totalRepeats = queue?.settings?.playTimes?.coerceAtLeast(1) ?: totalRepeats,
                completedIds = this@AudioLoopPlaybackService.completedIds.toSet(),
                failedIds = this@AudioLoopPlaybackService.failedIds.toSet()
            )
        }
    }

    private fun updateMediaSession(state: AudioLoopServiceState) {
        val playbackState = when (state.playerState) {
            AudioLoopServicePlayerState.PLAYING,
            AudioLoopServicePlayerState.PREPARING -> PlaybackStateCompat.STATE_PLAYING
            AudioLoopServicePlayerState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            AudioLoopServicePlayerState.EMPTY,
            AudioLoopServicePlayerState.WAITING,
            AudioLoopServicePlayerState.STOPPED -> PlaybackStateCompat.STATE_STOPPED
        }
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP
                )
                .setState(playbackState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    private fun buildNotification(state: AudioLoopServiceState): Notification {
        val isPlaying = state.playerState == AudioLoopServicePlayerState.PLAYING ||
            state.playerState == AudioLoopServicePlayerState.PREPARING
        val entry = state.currentEntry
        val text = when {
            entry == null -> getString(R.string.feature_learning_audio_loop_notification_ready)
            isPlaying -> getString(R.string.feature_learning_audio_loop_notification_playing, entry.word)
            else -> entry.word
        }
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.feature_learning_ic_pause,
                getString(R.string.feature_learning_audio_loop_notification_pause),
                servicePendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.feature_learning_ic_play,
                getString(R.string.feature_learning_audio_loop_notification_play),
                servicePendingIntent(ACTION_PLAY)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.feature_learning_ic_play)
            .setContentTitle(getString(R.string.feature_learning_audio_loop_notification_title))
            .setContentText(text)
            .setContentIntent(launchPendingIntent())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(servicePendingIntent(ACTION_STOP))
            )
            .addAction(
                R.drawable.feature_learning_ic_audio_loop_previous,
                getString(R.string.feature_learning_audio_loop_notification_previous),
                servicePendingIntent(ACTION_PREVIOUS)
            )
            .addAction(playPauseAction)
            .addAction(
                R.drawable.feature_learning_ic_audio_loop_next,
                getString(R.string.feature_learning_audio_loop_notification_next),
                servicePendingIntent(ACTION_NEXT)
            )
            .addAction(
                R.drawable.feature_learning_ic_pause,
                getString(R.string.feature_learning_audio_loop_notification_stop),
                servicePendingIntent(ACTION_STOP)
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.feature_learning_audio_loop_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, AudioLoopPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun launchPendingIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_SET_QUEUE = "com.chen.memorizewords.audioLoop.SET_QUEUE"
        const val ACTION_PLAY = "com.chen.memorizewords.audioLoop.PLAY"
        const val ACTION_PAUSE = "com.chen.memorizewords.audioLoop.PAUSE"
        const val ACTION_TOGGLE = "com.chen.memorizewords.audioLoop.TOGGLE"
        const val ACTION_NEXT = "com.chen.memorizewords.audioLoop.NEXT"
        const val ACTION_PREVIOUS = "com.chen.memorizewords.audioLoop.PREVIOUS"
        const val ACTION_STOP = "com.chen.memorizewords.audioLoop.STOP"
        const val ACTION_SELECT = "com.chen.memorizewords.audioLoop.SELECT"
        const val EXTRA_INDEX = "extra_audio_loop_index"

        private const val CHANNEL_ID = "audio_loop_playback"
        private const val NOTIFICATION_ID = 42037
        private const val MEDIA_SESSION_TAG = "AudioLoopPlayback"

        fun start(context: Context, action: String) {
            val intent = Intent(context, AudioLoopPlaybackService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun select(context: Context, index: Int) {
            val intent = Intent(context, AudioLoopPlaybackService::class.java)
                .setAction(ACTION_SELECT)
                .putExtra(EXTRA_INDEX, index)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
