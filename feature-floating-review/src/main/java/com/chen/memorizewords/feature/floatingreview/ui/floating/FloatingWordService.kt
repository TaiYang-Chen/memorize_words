package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import coil.load
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldConfig
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
    val favoriteEnabled: Boolean,
    val copyEnabled: Boolean
)

internal data class FloatingWordAdvanceResult(
    val words: List<Word>,
    val nextIndex: Int,
    val word: Word?
)

internal fun resolveCardActionState(hasWord: Boolean): FloatingCardActionState {
    return FloatingCardActionState(
        refreshEnabled = hasWord,
        favoriteEnabled = hasWord,
        copyEnabled = hasWord
    )
}

internal fun resolveCardAlpha(cardOpacityPercent: Int): Float {
    return cardOpacityPercent.coerceIn(0, 100) / 100f
}

internal fun resolveBallAlpha(ballOpacityPercent: Int): Float {
    return ballOpacityPercent.coerceIn(0, 100) / 100f
}

internal fun resolveBallSizeScale(ballSizePercent: Int): Float {
    return ballSizePercent.coerceIn(60, 140) / 100f
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
    }

    @Inject
    lateinit var floatingWordController: FloatingWordController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dockManager = FloatingDockManager()
    private val speechLayoutEngine = FloatingSpeechLayoutEngine()
    private lateinit var windowManager: WindowManager

    private var ballView: View? = null
    private var cardView: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var cardParams: WindowManager.LayoutParams? = null
    private var settingsJob: Job? = null

    private var words: List<Word> = emptyList()
    private var currentIndex = 0
    private var currentWord: Word? = null
    private var currentDefinitions: List<WordDefinitions> = emptyList()
    private var currentSettings: FloatingWordSettings = FloatingWordSettings()

    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragStartBallX = 0
    private var dragStartBallY = 0
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
                    applyBallSize()
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
            runCatching {
                currentSettings = floatingWordController.getSettings()
                words = floatingWordController.loadWords(currentSettings)
                if (currentSettings.orderType == FloatingWordOrderType.RANDOM) {
                    words = words.shuffled()
                }
                currentIndex = 0
                if (showNext) {
                    showNextWord()
                }
            }
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

        windowManager.addView(cardView, cardParams)
        windowManager.addView(ballView, ballParams)

        applyFloatingAppearance()
        restoreBallPosition()
        configureBallGestures()
        bindBallDrag()
        bindCardActions()
    }

    private fun restoreBallPosition() {
        serviceScope.launch {
            currentSettings = floatingWordController.getSettings()
            applyFloatingAppearance()
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
            updateFloatingSpeechLayout()
        }
        if (shouldPersist) {
            persistBallPosition(position, resolvedDockState)
        }
    }

    private fun removeViews() {
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
                    dragStartBallX = params.x
                    dragStartBallY = params.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchDownX
                    val dy = event.rawY - touchDownY
                    var dragJustStarted = false
                    if (!isDragging && (abs(dx) > threshold || abs(dy) > threshold)) {
                        isDragging = true
                        dragJustStarted = true
                        updateDraggedBallPosition(params, dx, dy)
                        clearLocalDockState()
                    }
                    if (isDragging && !dragJustStarted) {
                        updateDraggedBallPosition(params, dx, dy)
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

    private fun updateDraggedBallPosition(
        params: WindowManager.LayoutParams,
        dx: Float,
        dy: Float
    ): Boolean {
        params.x = dragStartBallX + dx.roundToInt()
        params.y = dragStartBallY + dy.roundToInt()
        ballView?.let { windowManager.updateViewLayout(it, params) }
        if (isCardVisible()) updateFloatingSpeechLayout()
        return true
    }

    private fun clearLocalDockState() {
        if (currentSettings.dockState != null) {
            currentSettings = currentSettings.copy(dockState = null)
        }
    }

    private fun settleDraggedBall() {
        val params = ballParams ?: return
        val result = dockManager.resolveFreeRestingState(
            bounds = getMovementBounds(currentSettings),
            x = params.x,
            y = params.y
        )
        isDragging = false
        applyBallPosition(result.position)
        persistBallPosition(result.position, result.dockState)
    }

    private fun handleBallSingleTap() {
        when (resolveSingleTapAction(isCardVisible())) {
            FloatingBallSingleTapAction.ShowCard -> showNextWord()

            FloatingBallSingleTapAction.HideCard -> {
                hideCard()
            }
        }
    }

    private fun applyBallPosition(position: FloatingBallPosition) {
        val params = ballParams ?: return
        params.x = position.x
        params.y = position.y
        ballView?.let { windowManager.updateViewLayout(it, params) }
        if (isCardVisible()) updateFloatingSpeechLayout()
    }

    private fun bindCardActions() {
        cardView?.findViewById<View>(R.id.module_floating_review_btn_favorite)?.setOnClickListener {
            toggleCurrentFavorite()
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.setOnClickListener {
            showNextWord()
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_copy)?.setOnClickListener {
            copyCurrentWord()
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_close)?.setOnClickListener {
            when (resolveCardCloseAction()) {
                FloatingCardCloseAction.HideCard -> hideCard()
            }
        }
    }

    private fun hideCard() {
        cardView?.visibility = View.GONE
    }

    private fun previewCard() {
        serviceScope.launch {
            runCatching {
                currentSettings = floatingWordController.getSettings()
                applyFloatingAppearance()
                val word = currentWord
                if (word != null) {
                    val content = floatingWordController.loadCardContent(word, currentSettings)
                    renderCard(word, content.definitions, content.examples, currentSettings)
                    updateNotification(word.word)
                    showCard()
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
                        showCard()
                    } else {
                        val content = floatingWordController.loadCardContent(previewWord, currentSettings)
                        renderCard(previewWord, content.definitions, content.examples, currentSettings)
                        updateNotification(previewWord.word)
                        showCard()
                    }
                }
            }.onFailure {
                renderEmptyCard()
                showCard()
            }
        }
    }

    private fun showNextWord() {
        serviceScope.launch {
            runCatching {
                val nextWord = advanceFloatingWordSequence(words, currentIndex, currentSettings.orderType)
                words = nextWord.words
                currentIndex = nextWord.nextIndex
                val word = nextWord.word

                if (word == null) {
                    currentWord = null
                    renderEmptyCard()
                    showCard()
                    return@launch
                }

                currentWord = word
                val content = floatingWordController.loadCardContent(word, currentSettings)
                renderCard(word, content.definitions, content.examples, currentSettings)
                updateNotification(word.word)
                showCard()
                floatingWordController.recordDisplay(word.id)
            }.onFailure {
                currentWord = null
                renderEmptyCard()
                showCard()
            }
        }
    }

    private fun showCard() {
        cardView?.visibility = View.VISIBLE
        updateFloatingSpeechLayout()
    }

    private fun renderEmptyCard() {
        invalidateCardMeasurement()
        currentDefinitions = emptyList()
        cardView?.findViewById<TextView>(R.id.module_floating_review_tv_word)?.apply {
            text = getString(R.string.module_floating_review_empty)
            visibility = View.VISIBLE
        }
        cardView?.findViewById<View>(R.id.module_floating_review_phonetic_row)?.visibility = View.GONE
        val container = cardView?.findViewById<LinearLayout>(
            R.id.module_floating_review_floating_fields_container
        ) ?: return
        container.removeAllViews()
        applyCardActionState(resolveCardActionState(hasWord = false))
    }

    private fun renderCard(
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        settings: FloatingWordSettings
    ) {
        invalidateCardMeasurement()
        currentDefinitions = definitions
        val container = cardView?.findViewById<LinearLayout>(
            R.id.module_floating_review_floating_fields_container
        ) ?: return
        container.removeAllViews()
        val configs = settings.fieldConfigs.filter { it.enabled }
        if (configs.isEmpty()) {
            renderEmptyCard()
            return
        }
        val enabledTypes = configs.map { it.type }.toSet()
        renderHeader(word, enabledTypes)
        renderPhonetics(word, enabledTypes)
        renderDefinitions(container, definitions, enabledTypes, configs)
        renderExtraFields(container, word, definitions, examples, configs)
        applyCardActionState(resolveCardActionState(hasWord = true))
        refreshFavoriteState(word)
    }

    private fun applyCardActionState(state: FloatingCardActionState) {
        cardView?.findViewById<View>(R.id.module_floating_review_btn_favorite)?.apply {
            isEnabled = state.favoriteEnabled
            alpha = if (state.favoriteEnabled) 1f else 0.38f
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_refresh)?.apply {
            isEnabled = state.refreshEnabled
            alpha = if (state.refreshEnabled) 1f else 0.38f
        }
        cardView?.findViewById<View>(R.id.module_floating_review_btn_copy)?.apply {
            isEnabled = state.copyEnabled
            alpha = if (state.copyEnabled) 1f else 0.38f
        }
    }

    private fun renderHeader(word: Word, enabledTypes: Set<FloatingWordFieldType>) {
        cardView?.findViewById<TextView>(R.id.module_floating_review_tv_word)?.apply {
            text = word.word
            visibility = if (FloatingWordFieldType.WORD in enabledTypes) View.VISIBLE else View.GONE
        }
    }

    private fun renderPhonetics(word: Word, enabledTypes: Set<FloatingWordFieldType>) {
        val row = cardView?.findViewById<View>(R.id.module_floating_review_phonetic_row) ?: return
        val divider = cardView?.findViewById<View>(R.id.module_floating_review_phonetic_divider)
        val uk = word.phoneticUK?.takeIf { it.isNotBlank() }
        val us = word.phoneticUS?.takeIf { it.isNotBlank() }
        val showRow = FloatingWordFieldType.PHONETIC in enabledTypes && (uk != null || us != null)
        row.visibility = if (showRow) View.VISIBLE else View.GONE
        divider?.visibility = if (showRow) View.VISIBLE else View.GONE
        if (!showRow) return

        bindPhoneticGroup(
            groupId = R.id.module_floating_review_phonetic_uk_group,
            textId = R.id.module_floating_review_tv_phonetic_uk,
            value = uk
        )
        bindPhoneticGroup(
            groupId = R.id.module_floating_review_phonetic_us_group,
            textId = R.id.module_floating_review_tv_phonetic_us,
            value = us
        )
    }

    private fun bindPhoneticGroup(groupId: Int, textId: Int, value: String?) {
        val group = cardView?.findViewById<View>(groupId) ?: return
        group.visibility = if (value == null) View.GONE else View.VISIBLE
        cardView?.findViewById<TextView>(textId)?.text = value.orEmpty()
    }

    private fun renderDefinitions(
        container: LinearLayout,
        definitions: List<WordDefinitions>,
        enabledTypes: Set<FloatingWordFieldType>,
        configs: List<FloatingWordFieldConfig>
    ) {
        val showMeaning = FloatingWordFieldType.MEANING in enabledTypes
        val showPartOfSpeech = FloatingWordFieldType.PART_OF_SPEECH in enabledTypes
        if (!showMeaning && !showPartOfSpeech) return

        val text = buildDefinitionLines(
            definitions = definitions,
            showPartOfSpeech = showPartOfSpeech || showMeaning,
            showMeaning = showMeaning
        )
        if (text.isBlank()) return
        val definitionTextSize = resolveFontSize(configs, FloatingWordFieldType.MEANING, 16)
            .coerceAtLeast(16)
        container.addView(
            buildTextView(
                text = text,
                textSizeSp = definitionTextSize.toFloat(),
                color = 0xFF111827.toInt(),
                bold = false
            ).apply {
                includeFontPadding = false
                setLineSpacing(dp(10).toFloat(), 1f)
            }
        )
    }

    private fun renderExtraFields(
        container: LinearLayout,
        word: Word,
        definitions: List<WordDefinitions>,
        examples: List<WordExample>,
        configs: List<FloatingWordFieldConfig>
    ) {
        configs
            .filter { it.type in setOf(FloatingWordFieldType.EXAMPLE, FloatingWordFieldType.NOTE, FloatingWordFieldType.IMAGE) }
            .forEach { config ->
                val view = when (config.type) {
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
                    else -> null
                }

                view?.takeIf { hasRenderableContent(it) }?.let {
                    val layoutParams = (it.layoutParams as? LinearLayout.LayoutParams)
                        ?: LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    layoutParams.topMargin = if (container.childCount > 0) dp(8) else 0
                    it.layoutParams = layoutParams
                    container.addView(it)
                }
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
            includeFontPadding = false
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

    private fun hasRenderableContent(view: View): Boolean {
        return (view as? TextView)?.text?.toString() != EMPTY_PLACEHOLDER
    }

    private fun resolveFontSize(
        configs: List<FloatingWordFieldConfig>,
        type: FloatingWordFieldType,
        fallback: Int
    ): Int {
        return configs.firstOrNull { it.type == type }?.fontSizeSp ?: fallback
    }

    private fun buildDefinitionLines(
        definitions: List<WordDefinitions>,
        showPartOfSpeech: Boolean,
        showMeaning: Boolean
    ): String {
        if (definitions.isEmpty()) return ""
        return definitions.take(2).joinToString("\n") { definition ->
            when {
                showPartOfSpeech && showMeaning ->
                    "${formatPartOfSpeech(definition.partOfSpeech.abbr)} ${definition.meaningChinese}"
                showPartOfSpeech -> formatPartOfSpeech(definition.partOfSpeech.abbr)
                showMeaning -> definition.meaningChinese
                else -> ""
            }
        }
    }

    private fun formatPartOfSpeech(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return trimmed
        return if (trimmed.endsWith(".")) trimmed else "$trimmed."
    }

    private fun refreshFavoriteState(word: Word) {
        val button = cardView?.findViewById<ImageButton>(R.id.module_floating_review_btn_favorite)
        button?.setImageResource(R.drawable.module_floating_review_ic_star)
        button?.contentDescription = getString(R.string.module_floating_review_favorite)
        serviceScope.launch {
            runCatching { floatingWordController.isFavorite(word.id) }
                .onSuccess { favorite ->
                    if (currentWord?.id == word.id) applyFavoriteState(favorite)
                }
        }
    }

    private fun toggleCurrentFavorite() {
        val word = currentWord ?: return
        serviceScope.launch {
            runCatching {
                floatingWordController.toggleFavorite(word)
                floatingWordController.isFavorite(word.id)
            }.onSuccess { favorite ->
                if (currentWord?.id == word.id) applyFavoriteState(favorite)
                Toast.makeText(
                    this@FloatingWordService,
                    getString(
                        if (favorite) {
                            R.string.module_floating_review_favorited
                        } else {
                            R.string.module_floating_review_unfavorited
                        }
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun applyFavoriteState(favorite: Boolean) {
        cardView?.findViewById<ImageButton>(R.id.module_floating_review_btn_favorite)?.apply {
            setImageResource(
                if (favorite) {
                    R.drawable.module_floating_review_ic_star_filled
                } else {
                    R.drawable.module_floating_review_ic_star
                }
            )
            contentDescription = getString(
                if (favorite) {
                    R.string.module_floating_review_unfavorite
                } else {
                    R.string.module_floating_review_favorite
                }
            )
        }
    }

    private fun copyCurrentWord() {
        val word = currentWord ?: return
        val text = buildCopyText(word, currentDefinitions)
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.setPrimaryClip(
            ClipData.newPlainText(word.word, text)
        )
        Toast.makeText(
            this,
            getString(R.string.module_floating_review_copied),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildCopyText(
        word: Word,
        definitions: List<WordDefinitions>
    ): String {
        return buildList {
            add(word.word)
            buildPhoneticText(word).takeIf { it.isNotBlank() }?.let(::add)
            buildDefinitionLines(
                definitions = definitions,
                showPartOfSpeech = true,
                showMeaning = true
            ).takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString("\n")
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

    private fun updateFloatingSpeechLayout() {
        val params = cardParams ?: return
        val ball = ballParams ?: return
        val card = cardView ?: return
        val safeArea = getSafeDisplayRect()
        val maxWidth = resources.getDimensionPixelSize(R.dimen.module_floating_review_card_width)
        val (cardWidth, cardHeight) = measureCardForPosition(card, maxWidth)
        val (petWidth, petHeight) = getPetWindowSize()
        val layout = speechLayoutEngine.resolve(
            safeArea = FloatingSpeechSafeArea(
                left = safeArea.left,
                top = safeArea.top,
                right = safeArea.right,
                bottom = safeArea.bottom
            ),
            petBounds = FloatingSpeechPetBounds(
                x = ball.x,
                y = ball.y,
                width = petWidth,
                height = petHeight
            ),
            cardSize = FloatingSpeechCardSize(
                width = cardWidth,
                height = cardHeight
            ),
            config = FloatingSpeechLayoutConfig(
                edgeMarginPx = resources.getDimensionPixelSize(
                    R.dimen.module_floating_review_card_edge_margin
                ),
                clearancePx = dp(currentSettings.cardGapDp),
                tailWidthPx = resources.getDimensionPixelSize(R.dimen.module_floating_review_tail_width),
                tailSafeInsetPx = resources.getDimensionPixelSize(
                    R.dimen.module_floating_review_tail_safe_inset
                ),
                tailSlotHeightPx = resources.getDimensionPixelSize(
                    R.dimen.module_floating_review_tail_panel_offset
                )
            )
        )
        applyFloatingSpeechTailLayout(layout)
        params.x = layout.cardX
        params.y = layout.cardY
        windowManager.updateViewLayout(card, params)
    }

    private fun applyFloatingSpeechTailLayout(layout: FloatingSpeechLayout) {
        val card = cardView ?: return
        val panel = card.findViewById<View>(R.id.module_floating_review_card_panel) ?: return
        val tail = card.findViewById<FloatingSpeechTailView>(
            R.id.module_floating_review_card_tail
        ) ?: return
        val tailWidth = resources.getDimensionPixelSize(R.dimen.module_floating_review_tail_width)
        val tailHeight = resources.getDimensionPixelSize(R.dimen.module_floating_review_tail_height)
        val panelOffset = resources.getDimensionPixelSize(
            R.dimen.module_floating_review_tail_panel_offset
        )

        (panel.layoutParams as? FrameLayout.LayoutParams)?.let { panelParams ->
            val targetTop = if (layout.placement == FloatingSpeechPlacement.BELOW_PET) panelOffset else 0
            val targetBottom = if (layout.placement == FloatingSpeechPlacement.ABOVE_PET) panelOffset else 0
            if (panelParams.topMargin != targetTop || panelParams.bottomMargin != targetBottom) {
                panelParams.topMargin = targetTop
                panelParams.bottomMargin = targetBottom
                panel.layoutParams = panelParams
                invalidateCardMeasurement()
            }
        }

        (tail.layoutParams as? FrameLayout.LayoutParams)?.let { tailParams ->
            tailParams.width = tailWidth
            tailParams.height = tailHeight
            tailParams.leftMargin = layout.tailCenterX - (tailWidth * 0.82f).roundToInt()
            tailParams.gravity = Gravity.START or when (layout.placement) {
                FloatingSpeechPlacement.ABOVE_PET -> Gravity.BOTTOM
                FloatingSpeechPlacement.BELOW_PET -> Gravity.TOP
            }
            tail.layoutParams = tailParams
        }
        tail.placement = layout.placement
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
        applyBallSize()
        applyBallOpacity()
        applyCardOpacity()
    }

    private fun applyBallSize() {
        val params = ballParams ?: return
        val (petWidth, petHeight) = getPetWindowSize()
        if (params.width == petWidth && params.height == petHeight) return
        params.width = petWidth
        params.height = petHeight
        ballView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        if (isCardVisible()) updateFloatingSpeechLayout()
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
        val scale = resolveBallSizeScale(currentSettings.ballSizePercent)
        val width = resources.getDimensionPixelSize(R.dimen.feature_floating_review_pet_width)
        val height = resources.getDimensionPixelSize(R.dimen.feature_floating_review_pet_height)
        return Pair((width * scale).roundToInt(), (height * scale).roundToInt())
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

