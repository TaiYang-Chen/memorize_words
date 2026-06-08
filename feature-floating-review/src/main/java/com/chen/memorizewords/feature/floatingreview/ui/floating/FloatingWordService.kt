package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import coil.load
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.LearningEntry
import com.chen.memorizewords.domain.floating.model.FloatingDockEdge
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldType
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.feature.floatingreview.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class FloatingCardActionState(
    val refreshEnabled: Boolean,
    val detailEnabled: Boolean
)

internal data class FloatingWordAdvanceResult(
    val words: List<Word>,
    val nextIndex: Int,
    val word: Word?
)

internal fun resolveCardActionState(hasWord: Boolean): FloatingCardActionState {
    return FloatingCardActionState(
        refreshEnabled = hasWord,
        detailEnabled = hasWord
    )
}

internal fun resolveCardAlpha(cardOpacityPercent: Int): Float {
    return cardOpacityPercent.coerceIn(0, 100) / 100f
}

internal fun resolveBallAlpha(ballOpacityPercent: Int): Float {
    return ballOpacityPercent.coerceIn(0, 100) / 100f
}

internal fun advanceFloatingWordSequence(
    words: List<Word>,
    currentIndex: Int,
    orderType: FloatingWordOrderType,
    shuffleWords: (List<Word>) -> List<Word> = { it.shuffled() }
): FloatingWordAdvanceResult {
    if (words.isEmpty()) {
        return FloatingWordAdvanceResult(
            words = words,
            nextIndex = 0,
            word = null
        )
    }

    val resolvedIndex = if (currentIndex in words.indices) currentIndex else 0
    val currentWord = words[resolvedIndex]
    val reachedEnd = resolvedIndex + 1 >= words.size
    val nextWords = if (reachedEnd && orderType == FloatingWordOrderType.RANDOM) {
        shuffleWords(words)
    } else {
        words
    }

    return FloatingWordAdvanceResult(
        words = nextWords,
        nextIndex = if (reachedEnd) 0 else resolvedIndex + 1,
        word = currentWord
    )
}

internal fun resolveBallPositionForSettings(
    settings: FloatingWordSettings,
    bounds: FloatingMovementBounds,
    previousBounds: FloatingMovementBounds?,
    dockManager: FloatingDockManager = FloatingDockManager()
): FloatingBallPosition {
    settings.dockState?.let { dockState ->
        dockManager.resolveDocked(
            bounds = bounds,
            config = settings.dockConfig,
            dockState = dockState
        )?.let { docked ->
            return docked.position
        }
    }
    if (previousBounds != null) {
        dockManager.resolveAnchoredFreePosition(
            previousBounds = previousBounds,
            newBounds = bounds,
            x = settings.floatingBallX,
            y = settings.floatingBallY
        )?.let { anchoredPosition ->
            return anchoredPosition
        }
    }
    if (settings.floatingBallX != 0 || settings.floatingBallY != 0) {
        return dockManager.clampToFree(bounds, settings.floatingBallX, settings.floatingBallY)
    }
    return FloatingBallPosition(
        x = bounds.freeRight,
        y = ((bounds.freeTop + bounds.freeBottom) / 2f).roundToInt()
    )
}

@AndroidEntryPoint
class FloatingWordService : Service() {

    companion object {
        const val ACTION_START = FloatingWordActions.ACTION_START
        const val ACTION_STOP = FloatingWordActions.ACTION_STOP
        const val ACTION_REFRESH = FloatingWordActions.ACTION_REFRESH
        const val ACTION_PREVIEW_CARD = FloatingWordActions.ACTION_PREVIEW_CARD

        private const val CHANNEL_ID = "floating_word_review_channel"
        private const val NOTIFICATION_ID = 5321
        private const val EMPTY_PLACEHOLDER = "-"
        private const val PET_SLEEP_TIMEOUT_MS = 60_000L
        private const val PET_TAP_DURATION_MS = 1_000L
        private const val PET_OPEN_CARD_DURATION_MS = 1_000L
        private const val PET_CLOSE_CARD_DURATION_MS = 750L
    }

    @Inject
    lateinit var floatingWordController: FloatingWordController

    @Inject
    lateinit var learningEntry: LearningEntry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dockManager = FloatingDockManager()
    private lateinit var windowManager: WindowManager

    private var ballView: View? = null
    private var cardView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var cardParams: WindowManager.LayoutParams? = null
    private val petActionHandler = Handler(Looper.getMainLooper())
    private var petMotion: MoonAssistantSequenceFrameController? = null
    private var petSequenceRunnable: Runnable? = null
    private var petSleepRunnable: Runnable? = null
    private var isPetSleeping = false
    private var settingsJob: Job? = null

    private var words: List<Word> = emptyList()
    private var currentIndex = 0
    private var currentWord: Word? = null
    private var currentSettings: FloatingWordSettings = FloatingWordSettings()

    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var ballGestureDetector: GestureDetector? = null
    private var lastMovementBounds: FloatingMovementBounds? = null
    private var cachedCardWidth = 0
    private var cachedCardHeight = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        ensureChannel()
        settingsJob = serviceScope.launch {
            floatingWordController.observeSettings().collect { settings ->
                currentSettings = settings
                applyFloatingAppearance()
                if (ballView != null && !isDragging) {
                    reconcileBallPosition(persistIfNeeded = false)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopFloating()
            ACTION_PREVIEW_CARD -> if (ensureForegroundAndViews()) previewCard()
            ACTION_REFRESH -> if (ensureForegroundAndViews()) refreshWords(showNext = false)
            else -> if (ensureForegroundAndViews()) refreshWords(showNext = false)
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reconcileBallPosition(persistIfNeeded = true)
    }

    override fun onDestroy() {
        removeViews()
        settingsJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForegroundAndViews(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return false
        }
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.module_floating_review_notification_ready))
        )
        ensureViews()
        return true
    }

    private fun stopFloating() {
        removeViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refreshWords(showNext: Boolean) {
        wakePetForInteraction()
        playPetAction(MoonAssistantFrameAction.LOADING)
        serviceScope.launch {
            runCatching {
                currentSettings = floatingWordController.getSettings()
                words = floatingWordController.loadWords(currentSettings)
                if (currentSettings.orderType == FloatingWordOrderType.RANDOM) {
                    words = words.shuffled()
                }
                currentIndex = 0
                if (showNext) {
                    showNextWord(playOpenMotion = true)
                } else {
                    playPetAction(MoonAssistantFrameAction.IDLE)
                }
            }.onFailure {
                playPetAction(MoonAssistantFrameAction.SAD)
            }
        }
    }

    private fun ensureViews() {
        if (ballView != null && cardView != null) return
        val inflater = LayoutInflater.from(this)
        ballView = inflater.inflate(R.layout.moon_assistant_pet_overlay, null)
        cardView = inflater.inflate(R.layout.module_floating_review_view_floating_card, null).apply {
            visibility = View.GONE
        }

        ballParams = createBallLayoutParams()
        cardParams = createCardLayoutParams()

        windowManager.addView(ballView, ballParams)
        windowManager.addView(cardView, cardParams)

        initializePetMotion()
        applyFloatingAppearance()
        restoreBallPosition()
        configureBallGestures()
        bindBallDrag()
        bindCardActions()
    }

    private fun restoreBallPosition() {
        serviceScope.launch {
            currentSettings = floatingWordController.getSettings()
            reconcileBallPosition(persistIfNeeded = true)
        }
    }

    private fun reconcileBallPosition(persistIfNeeded: Boolean) {
        val params = ballParams ?: return
        val movementBounds = getMovementBounds(currentSettings)
        val position = resolveBallPositionForSettings(
            settings = currentSettings,
            bounds = movementBounds,
            previousBounds = lastMovementBounds,
            dockManager = dockManager
        )
        val shouldPersist = persistIfNeeded && needsPersistence(position)
        val resolvedDockState = currentSettings.dockState?.normalized(currentSettings.dockConfig)
        params.x = position.x
        params.y = position.y
        ballView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        updateLocalBallState(position, resolvedDockState)
        lastMovementBounds = movementBounds
        if (isCardVisible()) {
            updateCardPosition()
        }
        if (shouldPersist) {
            persistBallPosition(position, resolvedDockState)
        }
    }

    private fun removeViews() {
        stopPetMotion()
        ballView?.let { runCatching { windowManager.removeView(it) } }
        cardView?.let { runCatching { windowManager.removeView(it) } }
        ballView = null
        cardView = null
        lastMovementBounds = null
    }

    private fun createBallLayoutParams(): WindowManager.LayoutParams {
        val (petWidth, petHeight) = getPetWindowSize()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            petWidth,
            petHeight,
            type,
            floatingBallWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
    }

    private fun createCardLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            floatingCardWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun initializePetMotion() {
        val petRootView = ballView ?: return
        val petImage = petImageView() ?: return
        petMotion = MoonAssistantSequenceFrameController(petRootView, petImage)
        playPetAction(MoonAssistantFrameAction.IDLE)
    }

    private fun stopPetMotion() {
        cancelPetSequence()
        cancelPetSleep()
        petMotion?.stop()
        petMotion = null
        isPetSleeping = false
    }

    private fun petImageView(): ImageView? = ballView?.findViewById(R.id.petImage)

    private fun playPetAction(action: MoonAssistantFrameAction, resetSleepTimer: Boolean = true) {
        if (action == MoonAssistantFrameAction.SLEEP) {
            isPetSleeping = true
            petMotion?.play(action)
            return
        }
        isPetSleeping = false
        petMotion?.play(action)
        if (resetSleepTimer) schedulePetSleep()
    }

    private fun wakePetForInteraction() {
        cancelPetSequence()
        if (isPetSleeping) {
            isPetSleeping = false
            petMotion?.play(MoonAssistantFrameAction.WAKE)
        }
        schedulePetSleep()
    }

    private fun schedulePetSequence(delayMs: Long, block: () -> Unit) {
        cancelPetSequence()
        val runnable = Runnable(block)
        petSequenceRunnable = runnable
        petActionHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPetSequence() {
        petSequenceRunnable?.let(petActionHandler::removeCallbacks)
        petSequenceRunnable = null
    }

    private fun schedulePetSleep() {
        cancelPetSleep()
        val runnable = Runnable {
            cancelPetSequence()
            playPetAction(MoonAssistantFrameAction.SLEEP, resetSleepTimer = false)
        }
        petSleepRunnable = runnable
        petActionHandler.postDelayed(runnable, PET_SLEEP_TIMEOUT_MS)
    }

    private fun cancelPetSleep() {
        petSleepRunnable?.let(petActionHandler::removeCallbacks)
        petSleepRunnable = null
    }

    private fun configureBallGestures() {
        ballGestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    handleBallSingleTap()
                    return true
                }
            }
        )
    }

    private fun bindBallDrag() {
        val threshold = dp(6).toFloat()
        ballView?.setOnTouchListener { _, event ->
            val params = ballParams ?: return@setOnTouchListener false
            val gestureDetector = ballGestureDetector
            gestureDetector?.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    wakePetForInteraction()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchDownX
                    val dy = event.rawY - touchDownY
                    var dragJustStarted = false
                    if (!isDragging && (abs(dx) > threshold || abs(dy) > threshold)) {
                        isDragging = true
                        dragJustStarted = true
                        updatePetDragWindowPosition(params, event)
                        playPetAction(resolveDragStartAction())
                        clearLocalDockState()
                    }
                    if (isDragging && !dragJustStarted) {
                        updatePetDragWindowPosition(params, event)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        playPetAction(settleDraggedBall())
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun updatePetDragWindowPosition(
        params: WindowManager.LayoutParams,
        event: MotionEvent
    ): Boolean {
        val motion = petMotion ?: return false
        motion.updateDragWindowPosition(
            windowManager = windowManager,
            params = params,
            rawTouchX = event.rawX,
            rawTouchY = event.rawY
        )
        if (isCardVisible()) updateCardPosition()
        return true
    }

    private fun resolveDragStartAction(): MoonAssistantFrameAction {
        return when (currentSettings.dockState?.dockedEdge) {
            FloatingDockEdge.LEFT -> MoonAssistantFrameAction.UNDOCK_LEFT
            FloatingDockEdge.RIGHT -> MoonAssistantFrameAction.UNDOCK_RIGHT
            else -> MoonAssistantFrameAction.DRAG
        }
    }

    private fun clearLocalDockState() {
        if (currentSettings.dockState != null) {
            currentSettings = currentSettings.copy(dockState = null)
        }
    }

    private fun settleDraggedBall(): MoonAssistantFrameAction {
        val params = ballParams ?: return MoonAssistantFrameAction.DROP
        val result = dockManager.resolveRestingState(
            bounds = getMovementBounds(currentSettings),
            config = currentSettings.dockConfig,
            snapTriggerDistancePx = dp(currentSettings.dockConfig.snapTriggerDistanceDp),
            x = params.x,
            y = params.y
        )
        isDragging = false
        applyBallPosition(result.position)
        persistBallPosition(result.position, result.dockState)
        return when (result.dockState?.dockedEdge) {
            FloatingDockEdge.LEFT -> MoonAssistantFrameAction.DOCK_LEFT
            FloatingDockEdge.RIGHT -> MoonAssistantFrameAction.DOCK_RIGHT
            else -> MoonAssistantFrameAction.DROP
        }
    }

    private fun handleBallSingleTap() {
        wakePetForInteraction()
        when (resolveSingleTapAction(isCardVisible())) {
            FloatingBallSingleTapAction.ShowCard -> {
                playPetAction(MoonAssistantFrameAction.TAP)
                schedulePetSequence(PET_TAP_DURATION_MS) {
                    showNextWord(playOpenMotion = true)
                }
            }

            FloatingBallSingleTapAction.HideCard -> {
                hideCardWithMotion()
            }
        }
    }

    private fun applyBallPosition(position: FloatingBallPosition) {
        val params = ballParams ?: return
        params.x = position.x
        params.y = position.y
        ballView?.let { windowManager.updateViewLayout(it, params) }
        if (isCardVisible()) updateCardPosition()
    }

    private fun bindCardActions() {
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.setOnClickListener {
            wakePetForInteraction()
            showNextWord(playOpenMotion = true)
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_detail)?.setOnClickListener {
            val word = currentWord ?: return@setOnClickListener
            hideCardWithMotion()
            openWordDetail(word)
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_close)?.setOnClickListener {
            closeThenStopFloating()
        }
    }

    private fun hideCardWithMotion() {
        cancelPetSequence()
        playPetAction(MoonAssistantFrameAction.CLOSE_CARD)
        cardView?.visibility = View.GONE
    }

    private fun closeThenStopFloating() {
        cancelPetSequence()
        playPetAction(MoonAssistantFrameAction.CLOSE_CARD)
        cardView?.visibility = View.GONE
        schedulePetSequence(PET_CLOSE_CARD_DURATION_MS) {
            stopFloating()
        }
    }

    private fun previewCard() {
        wakePetForInteraction()
        playPetAction(MoonAssistantFrameAction.LOADING)
        serviceScope.launch {
            runCatching {
                currentSettings = floatingWordController.getSettings()
                applyFloatingAppearance()
                val word = currentWord
                if (word != null) {
                    val content = floatingWordController.loadCardContent(word, currentSettings)
                    renderCard(word, content.definitions, content.examples, currentSettings)
                    updateNotification(word.word)
                    showCardWithMotion(playOpenMotion = true)
                } else {
                    if (words.isEmpty()) {
                        words = floatingWordController.loadWords(currentSettings)
                        if (currentSettings.orderType == FloatingWordOrderType.RANDOM) {
                            words = words.shuffled()
                        }
                        currentIndex = 0
                    }
                    val preview = advanceFloatingWordSequence(
                        words = words,
                        currentIndex = currentIndex,
                        orderType = currentSettings.orderType
                    )
                    words = preview.words
                    currentIndex = preview.nextIndex
                    val previewWord = preview.word
                    currentWord = previewWord
                    if (previewWord == null) {
                        renderEmptyCard()
                        showCardWithoutMotion()
                        playPetAction(MoonAssistantFrameAction.SAD)
                    } else {
                        val content = floatingWordController.loadCardContent(previewWord, currentSettings)
                        renderCard(previewWord, content.definitions, content.examples, currentSettings)
                        updateNotification(previewWord.word)
                        showCardWithMotion(playOpenMotion = true)
                    }
                }
            }.onFailure {
                renderEmptyCard()
                showCardWithoutMotion()
                playPetAction(MoonAssistantFrameAction.SAD)
            }
        }
    }

    private fun showNextWord(playOpenMotion: Boolean = true) {
        playPetAction(MoonAssistantFrameAction.LOADING)
        serviceScope.launch {
            runCatching {
                val nextWord = advanceFloatingWordSequence(words, currentIndex, currentSettings.orderType)
                words = nextWord.words
                currentIndex = nextWord.nextIndex
                val word = nextWord.word

                if (word == null) {
                    currentWord = null
                    renderEmptyCard()
                    showCardWithoutMotion()
                    playPetAction(MoonAssistantFrameAction.SAD)
                    return@launch
                }

                currentWord = word
                val content = floatingWordController.loadCardContent(word, currentSettings)
                renderCard(word, content.definitions, content.examples, currentSettings)
                updateNotification(word.word)
                showCardWithMotion(playOpenMotion = playOpenMotion)
                floatingWordController.recordDisplay(word.id)
            }.onFailure {
                currentWord = null
                renderEmptyCard()
                showCardWithoutMotion()
                playPetAction(MoonAssistantFrameAction.SAD)
            }
        }
    }

    private fun showCardWithoutMotion() {
        updateCardPosition()
        cardView?.visibility = View.VISIBLE
    }

    private fun showCardWithMotion(playOpenMotion: Boolean) {
        showCardWithoutMotion()
        val motion = petMotion ?: return
        val cardOnLeft = isCardOnLeft()
        cancelPetSequence()
        if (playOpenMotion) {
            motion.playOpenCard(cardOnLeft)
            schedulePetSequence(PET_OPEN_CARD_DURATION_MS) {
                petMotion?.playPoint(cardOnLeft)
                schedulePetSleep()
            }
        } else {
            motion.playPoint(cardOnLeft)
            schedulePetSleep()
        }
    }

    private fun isCardOnLeft(): Boolean {
        val card = cardView ?: return true
        val cardParams = cardParams ?: return true
        val ballParams = ballParams ?: return true
        val cardWidth = card.measuredWidth.takeIf { it > 0 } ?: card.width
        val cardCenterX = cardParams.x + cardWidth / 2
        val petCenterX = ballParams.x + getPetWindowSize().first / 2
        return cardCenterX < petCenterX
    }

    private fun renderEmptyCard() {
        invalidateCardMeasurement()
        val container = cardView?.findViewById<LinearLayout>(
            R.id.module_floating_review_floating_fields_container
        ) ?: return
        container.removeAllViews()
        val textView = TextView(this).apply {
            text = getString(R.string.module_floating_review_empty)
            setTextColor(0xFF64748B.toInt())
            textSize = 13f
        }
        container.addView(textView)
        applyCardActionState(resolveCardActionState(hasWord = false))
    }

    private fun renderCard(
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        settings: FloatingWordSettings
    ) {
        invalidateCardMeasurement()
        val container = cardView?.findViewById<LinearLayout>(
            R.id.module_floating_review_floating_fields_container
        ) ?: return
        container.removeAllViews()
        val configs = settings.fieldConfigs.filter { it.enabled }
        if (configs.isEmpty()) {
            renderEmptyCard()
            return
        }
        applyCardActionState(resolveCardActionState(hasWord = true))
        configs.forEachIndexed { index, config ->
            val view = when (config.type) {
                FloatingWordFieldType.WORD -> buildTextView(
                    word.word,
                    config.fontSizeSp.toFloat(),
                    0xFF0F172A.toInt(),
                    true
                )

                FloatingWordFieldType.PHONETIC -> buildTextView(
                    buildPhoneticText(word),
                    config.fontSizeSp.toFloat(),
                    0xFF64748B.toInt(),
                    false
                )

                FloatingWordFieldType.MEANING -> buildTextView(
                    buildMeaningText(definitions),
                    config.fontSizeSp.toFloat(),
                    0xFF1F2937.toInt(),
                    false
                )

                FloatingWordFieldType.PART_OF_SPEECH -> buildTextView(
                    buildPartOfSpeechText(definitions),
                    config.fontSizeSp.toFloat(),
                    0xFF475569.toInt(),
                    false
                )

                FloatingWordFieldType.EXAMPLE -> buildTextView(
                    buildExampleText(examples),
                    config.fontSizeSp.toFloat(),
                    0xFF334155.toInt(),
                    false
                )

                FloatingWordFieldType.NOTE -> buildTextView(
                    word.notes.orEmpty(),
                    config.fontSizeSp.toFloat(),
                    0xFF334155.toInt(),
                    false
                )

                FloatingWordFieldType.IMAGE -> buildImageView(word.mnemonicImageUrl, config.fontSizeSp)
            }

            view?.let {
                val layoutParams = (it.layoutParams as? LinearLayout.LayoutParams)
                    ?: LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                if (index > 0) layoutParams.topMargin = dp(6)
                it.layoutParams = layoutParams
                container.addView(it)
            }
        }
    }

    private fun applyCardActionState(state: FloatingCardActionState) {
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.apply {
            isEnabled = state.refreshEnabled
            alpha = if (state.refreshEnabled) 1f else 0.38f
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_detail)?.apply {
            isEnabled = state.detailEnabled
            alpha = if (state.detailEnabled) 1f else 0.38f
        }
    }

    private fun buildTextView(
        text: String,
        textSizeSp: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        val content = text.ifBlank { EMPTY_PLACEHOLDER }
        val isPlaceholder = content == EMPTY_PLACEHOLDER
        return TextView(this).apply {
            this.text = content
            setTextColor(if (isPlaceholder) 0xFF94A3B8.toInt() else color)
            this.textSize = textSizeSp
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun buildImageView(url: String?, sizeDp: Int): View {
        if (url.isNullOrBlank()) {
            return buildTextView(EMPTY_PLACEHOLDER, 12f, 0xFF64748B.toInt(), false)
        }
        val height = dp(sizeDp.coerceAtLeast(80))
        return ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(url)
        }
    }

    private fun buildPhoneticText(word: Word): String {
        val us = word.phoneticUS?.takeIf { it.isNotBlank() }
        val uk = word.phoneticUK?.takeIf { it.isNotBlank() }
        return when {
            us != null && uk != null -> getString(
                R.string.module_floating_review_phonetic_both,
                us,
                uk
            )
            us != null -> getString(R.string.module_floating_review_phonetic_us_only, us)
            uk != null -> getString(R.string.module_floating_review_phonetic_uk_only, uk)
            else -> ""
        }
    }

    private fun buildMeaningText(definitions: List<WordDefinitions>): String {
        if (definitions.isEmpty()) return ""
        return definitions.take(2).joinToString("\n") { definition ->
            "${definition.partOfSpeech.abbr} ${definition.meaningChinese}"
        }
    }

    private fun buildPartOfSpeechText(definitions: List<WordDefinitions>): String {
        if (definitions.isEmpty()) return ""
        return definitions.map { it.partOfSpeech.abbr }.distinct().joinToString(" ")
    }

    private fun buildExampleText(examples: List<WordExample>): String {
        val example = examples.firstOrNull() ?: return ""
        val zh = example.chineseTranslation?.takeIf { it.isNotBlank() }
        return if (zh != null) "${example.englishSentence}\n$zh" else example.englishSentence
    }

    private fun updateCardPosition() {
        val params = cardParams ?: return
        val ball = ballParams ?: return
        val card = cardView ?: return
        val safeArea = getSafeDisplayRect()
        val margin = dp(12)
        val maxWidth = resources.getDimensionPixelSize(R.dimen.module_floating_review_card_width)
        val (cardWidth, cardHeight) = measureCardForPosition(card, maxWidth)
        val (petWidth, petHeight) = getPetWindowSize()

        val centerX = ball.x + petWidth / 2
        val minX = safeArea.left + margin
        val maxX = (safeArea.right - cardWidth - margin).coerceAtLeast(minX)
        params.x = (centerX - cardWidth / 2).coerceIn(minX, maxX)

        val minY = safeArea.top + margin
        val maxY = (safeArea.bottom - cardHeight - margin).coerceAtLeast(minY)
        val aboveY = ball.y - cardHeight - margin
        val belowY = ball.y + petHeight + margin
        params.y = if (aboveY >= minY) {
            aboveY
        } else {
            belowY.coerceAtMost(maxY)
        }
        windowManager.updateViewLayout(card, params)
    }

    private fun measureCardForPosition(card: View, maxWidth: Int): Pair<Int, Int> {
        if (cachedCardWidth <= 0 || cachedCardHeight <= 0 || card.isLayoutRequested) {
            card.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            cachedCardWidth = card.measuredWidth
            cachedCardHeight = card.measuredHeight
        }
        return cachedCardWidth to cachedCardHeight
    }

    private fun invalidateCardMeasurement() {
        cachedCardWidth = 0
        cachedCardHeight = 0
    }

    private fun isCardVisible(): Boolean = cardView?.visibility == View.VISIBLE

    private fun applyCardOpacity() {
        cardView?.alpha = resolveCardAlpha(currentSettings.cardOpacityPercent)
    }

    private fun applyBallOpacity() {
        ballView?.alpha = resolveBallAlpha(currentSettings.ballOpacityPercent)
    }

    private fun applyFloatingAppearance() {
        applyBallOpacity()
        applyCardOpacity()
    }

    private fun persistBallPosition(
        position: FloatingBallPosition,
        dockState: FloatingDockState? = null
    ) {
        updateLocalBallState(position, dockState)
        serviceScope.launch {
            floatingWordController.updateBallPosition(position.x, position.y, dockState)
        }
    }

    private fun updateLocalBallState(
        position: FloatingBallPosition,
        dockState: FloatingDockState? = null
    ) {
        currentSettings = currentSettings.copy(
            floatingBallX = position.x,
            floatingBallY = position.y,
            dockState = dockState
        )
    }

    private fun needsPersistence(position: FloatingBallPosition): Boolean {
        return position.x != currentSettings.floatingBallX ||
            position.y != currentSettings.floatingBallY ||
            currentSettings.dockState != null
    }

    private fun openWordDetail(word: Word) {
        startActivity(
            learningEntry.createOpenWordIntent(this, word.id, true).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun getMovementBounds(settings: FloatingWordSettings): FloatingMovementBounds {
        val safeArea = getSafeDisplayRect()
        val (petWidth, petHeight) = getPetWindowSize()
        return dockManager.createBounds(
            safeArea = FloatingAvailableArea(
                left = safeArea.left,
                top = safeArea.top,
                right = safeArea.right,
                bottom = safeArea.bottom
            ),
            ballWidthPx = petWidth,
            ballHeightPx = petHeight,
            config = settings.dockConfig
        )
    }

    private fun getPetWindowSize(): Pair<Int, Int> {
        return Pair(
            resources.getDimensionPixelSize(R.dimen.feature_floating_review_pet_width),
            resources.getDimensionPixelSize(R.dimen.feature_floating_review_pet_height)
        )
    }

    private fun getSafeDisplayRect(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()
            )
            return Rect(
                bounds.left + insets.left,
                bounds.top + insets.top,
                bounds.right - insets.right,
                bounds.bottom - insets.bottom
            )
        }

        val display = windowManager.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val rootInsets = ballView?.rootWindowInsets
        val leftInset = 0
        val rightInset = 0
        val topInset = rootInsets?.systemWindowInsetTop
            ?.takeIf { it > 0 }
            ?: getSystemDimension("status_bar_height")
        val bottomInset = listOf(
            rootInsets?.stableInsetBottom ?: 0,
            rootInsets?.systemWindowInsetBottom ?: 0,
            getSystemDimension("navigation_bar_height")
        ).maxOrNull() ?: 0
        return Rect(
            leftInset,
            topInset,
            metrics.widthPixels - rightInset,
            metrics.heightPixels - bottomInset
        )
    }

    private fun getSystemDimension(name: String): Int {
        val resourceId = resources.getIdentifier(name, "dimen", "android")
        if (resourceId == 0) return 0
        return resources.getDimensionPixelSize(resourceId)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.module_floating_review_notification_channel),
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
            .setSmallIcon(R.drawable.module_floating_review_ic_volume_up)
            .setContentTitle(getString(R.string.module_floating_review_notification_title))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}

