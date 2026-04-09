package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import coil.load
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.LearningEntry
import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldType
import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
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
    private var rotationAnimator: ObjectAnimator? = null
    private var positionAnimator: ValueAnimator? = null
    private var settingsJob: Job? = null

    private var words: List<Word> = emptyList()
    private var currentIndex = 0
    private var currentWord: Word? = null
    private var currentSettings: FloatingWordSettings = FloatingWordSettings()

    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var startX = 0
    private var startY = 0
    private var ballGestureDetector: GestureDetector? = null
    private var lastMovementBounds: FloatingMovementBounds? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        ensureChannel()
        settingsJob = serviceScope.launch {
            floatingWordController.observeSettings().collect { settings ->
                currentSettings = settings
                applyCardOpacity()
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
        serviceScope.launch {
            currentSettings = floatingWordController.getSettings()
            words = floatingWordController.loadWords(currentSettings)
            if (currentSettings.orderType == FloatingWordOrderType.RANDOM) {
                words = words.shuffled()
            }
            currentIndex = 0
            if (showNext) showNextWord()
        }
    }

    private fun ensureViews() {
        if (ballView != null && cardView != null) return
        val inflater = LayoutInflater.from(this)
        ballView = inflater.inflate(R.layout.module_floating_review_view_floating_ball, null)
        cardView = inflater.inflate(R.layout.module_floating_review_view_floating_card, null).apply {
            visibility = View.GONE
        }

        ballParams = createBallLayoutParams()
        cardParams = createCardLayoutParams()

        windowManager.addView(ballView, ballParams)
        windowManager.addView(cardView, cardParams)

        applyCardOpacity()
        restoreBallPosition()
        startRotation()
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
        val resolved = resolvePositionForSettings(
            settings = currentSettings,
            bounds = movementBounds,
            previousBounds = lastMovementBounds
        )
        val shouldPersist = persistIfNeeded && needsPersistence(resolved.position, resolved.dockState)
        params.x = resolved.position.x
        params.y = resolved.position.y
        ballView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        updateLocalBallState(resolved.position, resolved.dockState)
        lastMovementBounds = movementBounds
        if (isCardVisible()) {
            updateCardPosition()
        }
        if (shouldPersist) {
            persistBallPosition(resolved.position, resolved.dockState)
        }
    }

    private fun resolvePositionForSettings(
        settings: FloatingWordSettings,
        bounds: FloatingMovementBounds,
        previousBounds: FloatingMovementBounds?
    ): FloatingDockResult {
        val normalizedDockState = settings.dockState?.normalized(settings.dockConfig)
        if (normalizedDockState != null) {
            dockManager.resolveDocked(bounds, settings.dockConfig, normalizedDockState)?.let { return it }
        }
        if (previousBounds != null) {
            dockManager.resolveAnchoredFreePosition(
                previousBounds = previousBounds,
                newBounds = bounds,
                x = settings.floatingBallX,
                y = settings.floatingBallY
            )?.let { anchoredPosition ->
                return FloatingDockResult(position = anchoredPosition, dockState = null)
            }
        }
        if (settings.floatingBallX != 0 || settings.floatingBallY != 0) {
            return FloatingDockResult(
                position = dockManager.clampToFree(bounds, settings.floatingBallX, settings.floatingBallY),
                dockState = null
            )
        }
        return dockManager.buildInitialState(bounds, settings.dockConfig)
    }

    private fun removeViews() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        positionAnimator?.cancel()
        positionAnimator = null
        ballView?.let { runCatching { windowManager.removeView(it) } }
        cardView?.let { runCatching { windowManager.removeView(it) } }
        ballView = null
        cardView = null
        lastMovementBounds = null
    }

    private fun createBallLayoutParams(): WindowManager.LayoutParams {
        val size = resources.getDimensionPixelSize(R.dimen.module_floating_review_ball_size)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            size,
            size,
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

    private fun startRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = ObjectAnimator.ofFloat(ballView, View.ROTATION, 0f, 360f).apply {
            duration = 4000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
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

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    handleBallDoubleTap()
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
                    positionAnimator?.cancel()
                    isDragging = false
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchDownX
                    val dy = event.rawY - touchDownY
                    if (!isDragging && (abs(dx) > threshold || abs(dy) > threshold)) {
                        isDragging = true
                        if (currentSettings.dockState != null) {
                            val revealed = dockManager.revealDockedPosition(
                                getMovementBounds(currentSettings),
                                currentSettings.dockConfig,
                                currentSettings.dockState ?: return@setOnTouchListener false
                            )
                            if (revealed != null) {
                                applyBallPosition(revealed)
                                updateLocalBallState(revealed, null)
                                startX = revealed.x
                                startY = revealed.y
                            }
                        }
                    }
                    if (isDragging) {
                        val target = dockManager.clampToFree(
                            getMovementBounds(currentSettings),
                            startX + dx.roundToInt(),
                            startY + dy.roundToInt()
                        )
                        applyBallPosition(target)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        settleDraggedBall()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun settleDraggedBall() {
        val params = ballParams ?: return
        val position = dockManager.clampToFree(
            bounds = getMovementBounds(currentSettings),
            x = params.x,
            y = params.y
        )
        isDragging = false
        persistBallPosition(position, null)
    }

    private fun handleBallSingleTap() {
        when (resolveSingleTapAction(currentSettings.dockState != null, isCardVisible())) {
            FloatingBallSingleTapAction.NoOp -> Unit

            FloatingBallSingleTapAction.ShowCard -> {
                showNextWord()
            }

            FloatingBallSingleTapAction.HideCard -> {
                cardView?.visibility = View.GONE
            }
        }
    }

    private fun handleBallDoubleTap() {
        when (resolveDoubleTapAction(currentSettings.dockState != null)) {
            FloatingBallDoubleTapAction.RevealDocked -> {
                val reveal = revealDockedBall() ?: return
                updateLocalBallState(reveal, null)
                animateBallTo(reveal) {
                    persistBallPosition(reveal, null)
                }
            }

            FloatingBallDoubleTapAction.HideToNearestEdge -> hideBallToNearestEdge()
        }
    }

    private fun revealDockedBall(): FloatingBallPosition? {
        val dockState = currentSettings.dockState ?: return null
        val normalizedConfig = currentSettings.dockConfig.normalized()
        return dockManager.revealDockedPosition(
            getMovementBounds(currentSettings),
            normalizedConfig,
            dockState
        )
    }

    private fun hideBallToNearestEdge() {
        cardView?.visibility = View.GONE
        val params = ballParams ?: return
        val result = dockManager.dockToNearestEdge(
            bounds = getMovementBounds(currentSettings),
            config = currentSettings.dockConfig.normalized(),
            x = params.x,
            y = params.y
        )
        updateLocalBallState(result.position, result.dockState)
        animateBallTo(result.position) {
            persistBallPosition(result.position, result.dockState)
        }
    }

    private fun applyBallPosition(position: FloatingBallPosition) {
        val params = ballParams ?: return
        params.x = position.x
        params.y = position.y
        ballView?.let { windowManager.updateViewLayout(it, params) }
        if (isCardVisible()) updateCardPosition()
    }

    private fun animateBallTo(target: FloatingBallPosition, onEnd: () -> Unit) {
        val params = ballParams ?: return
        positionAnimator?.cancel()
        val startX = params.x
        val startY = params.y
        if (startX == target.x && startY == target.y) {
            applyBallPosition(target)
            onEnd()
            return
        }
        positionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = currentSettings.dockConfig.normalized().snapAnimationDurationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                applyBallPosition(
                    FloatingBallPosition(
                        x = (startX + (target.x - startX) * fraction).roundToInt(),
                        y = (startY + (target.y - startY) * fraction).roundToInt()
                    )
                )
            }
            doOnEnd {
                applyBallPosition(target)
                onEnd()
            }
            start()
        }
    }

    private fun bindCardActions() {
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.setOnClickListener {
            showNextWord()
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_detail)?.setOnClickListener {
            val word = currentWord ?: return@setOnClickListener
            openWordDetail(word)
            cardView?.visibility = View.GONE
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_close)?.setOnClickListener {
            stopFloating()
        }
    }

    private fun previewCard() {
        serviceScope.launch {
            currentSettings = floatingWordController.getSettings()
            applyCardOpacity()
            val word = currentWord
            if (word != null) {
                val content = floatingWordController.loadCardContent(word, currentSettings)
                renderCard(word, content.definitions, content.examples, currentSettings)
                updateNotification(word.word)
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
                } else {
                    val content = floatingWordController.loadCardContent(previewWord, currentSettings)
                    renderCard(previewWord, content.definitions, content.examples, currentSettings)
                    updateNotification(previewWord.word)
                }
            }
            updateCardPosition()
            cardView?.visibility = View.VISIBLE
        }
    }

    private fun showNextWord() {
        serviceScope.launch {
            val nextWord = advanceFloatingWordSequence(words, currentIndex, currentSettings.orderType)
            words = nextWord.words
            currentIndex = nextWord.nextIndex
            val word = nextWord.word

            if (word == null) {
                currentWord = null
                renderEmptyCard()
                updateCardPosition()
                cardView?.visibility = View.VISIBLE
                return@launch
            }

            currentWord = word
            val content = floatingWordController.loadCardContent(word, currentSettings)
            renderCard(word, content.definitions, content.examples, currentSettings)
            updateNotification(word.word)
            updateCardPosition()
            cardView?.visibility = View.VISIBLE
            floatingWordController.recordDisplay(word.id)
        }
    }

    private fun renderEmptyCard() {
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
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.isEnabled = state.refreshEnabled
        cardView?.findViewById<View>(R.id.module_floating_review_btn_detail)?.isEnabled = state.detailEnabled
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
        card.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cardWidth = card.measuredWidth
        val cardHeight = card.measuredHeight
        val ballSize = resources.getDimensionPixelSize(R.dimen.module_floating_review_ball_size)

        val centerX = ball.x + ballSize / 2
        val minX = safeArea.left + margin
        val maxX = (safeArea.right - cardWidth - margin).coerceAtLeast(minX)
        params.x = (centerX - cardWidth / 2).coerceIn(minX, maxX)

        val minY = safeArea.top + margin
        val maxY = (safeArea.bottom - cardHeight - margin).coerceAtLeast(minY)
        val aboveY = ball.y - cardHeight - margin
        val belowY = ball.y + ballSize + margin
        params.y = if (aboveY >= minY) {
            aboveY
        } else {
            belowY.coerceAtMost(maxY)
        }
        windowManager.updateViewLayout(card, params)
    }

    private fun isCardVisible(): Boolean = cardView?.visibility == View.VISIBLE

    private fun applyCardOpacity() {
        cardView?.alpha = resolveCardAlpha(currentSettings.cardOpacityPercent)
    }

    private fun persistBallPosition(position: FloatingBallPosition, dockState: FloatingDockState?) {
        updateLocalBallState(position, dockState)
        serviceScope.launch {
            floatingWordController.updateBallPosition(position.x, position.y, dockState)
        }
    }

    private fun updateLocalBallState(position: FloatingBallPosition, dockState: FloatingDockState?) {
        currentSettings = currentSettings.copy(
            floatingBallX = position.x,
            floatingBallY = position.y,
            dockState = dockState
        )
    }

    private fun needsPersistence(position: FloatingBallPosition, dockState: FloatingDockState?): Boolean {
        return position.x != currentSettings.floatingBallX ||
            position.y != currentSettings.floatingBallY ||
            dockState != currentSettings.dockState
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
        val size = resources.getDimensionPixelSize(R.dimen.module_floating_review_ball_size)
        return dockManager.createBounds(
            safeArea = FloatingAvailableArea(
                left = safeArea.left,
                top = safeArea.top,
                right = safeArea.right,
                bottom = safeArea.bottom
            ),
            ballSizePx = size,
            config = settings.dockConfig
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

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) = Unit
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
            override fun onAnimationCancel(animation: android.animation.Animator) = Unit
            override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
        })
    }
}

