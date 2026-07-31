package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Region
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import com.chen.memorizewords.feature.floatingreview.R
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal interface FloatingCardWindowPort {
    val attached: Boolean
    val frame: FloatingCardWindowFrame
    val supportsInstantPositionChanges: Boolean
        get() = true

    fun attach()
    fun update(frame: FloatingCardWindowFrame)
    fun detach()
}

internal fun interface FloatingCardSurfacePort {
    fun update(height: Int, canvasHeight: Int, placement: FloatingSpeechPlacement)
}

internal class FloatingCardFrameDispatcher(
    private val windowPort: FloatingCardWindowPort,
    private val surfacePort: FloatingCardSurfacePort
) {
    private var generation = 0L
    private var removed = true

    fun attach() {
        generation++
        removed = false
    }

    fun beginTransaction(): Long = ++generation

    fun invalidate() {
        generation++
    }

    fun detach() {
        generation++
        removed = true
    }

    fun isCurrent(transaction: Long): Boolean {
        return !removed && windowPort.attached && transaction == generation
    }

    fun prepare(transaction: Long, snapshot: FloatingCardLayoutSnapshot): Boolean {
        if (!isCurrent(transaction)) return false
        surfacePort.update(
            snapshot.surface.height,
            snapshot.window.height,
            snapshot.placement
        )
        windowPort.update(snapshot.window)
        return true
    }

    fun animateFrame(
        transaction: Long,
        height: Int,
        canvasHeight: Int,
        placement: FloatingSpeechPlacement
    ): Boolean {
        if (!isCurrent(transaction)) return false
        surfacePort.update(height, canvasHeight, placement)
        return true
    }

    fun normalize(transaction: Long, snapshot: FloatingCardLayoutSnapshot): Boolean {
        if (!isCurrent(transaction)) return false
        surfacePort.update(
            snapshot.surface.height,
            snapshot.window.height,
            snapshot.placement
        )
        windowPort.update(snapshot.window)
        return true
    }

    fun moveWindow(frame: FloatingCardWindowFrame): Boolean {
        if (removed || !windowPort.attached) return false
        windowPort.update(frame)
        return true
    }
}

internal class AndroidFloatingCardWindowPort(
    private val windowManager: WindowManager,
    private val root: View,
    private val params: WindowManager.LayoutParams
) : FloatingCardWindowPort {
    override val supportsInstantPositionChanges: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    init {
        params.windowAnimations = 0
        if (supportsInstantPositionChanges) {
            params.setCanPlayMoveAnimation(false)
        }
    }

    override var attached: Boolean = false
        private set

    override val frame: FloatingCardWindowFrame
        get() = FloatingCardWindowFrame(params.x, params.y, params.width, params.height)

    override fun attach() {
        if (attached) return
        windowManager.addView(root, params)
        attached = true
    }

    override fun update(frame: FloatingCardWindowFrame) {
        val changed = params.x != frame.x ||
            params.y != frame.y ||
            params.width != frame.width ||
            params.height != frame.height
        params.x = frame.x
        params.y = frame.y
        params.width = frame.width
        params.height = frame.height
        if (changed && attached && root.isAttachedToWindow) {
            windowManager.updateViewLayout(root, params)
        }
    }

    override fun detach() {
        if (!attached && !root.isAttachedToWindow) return
        attached = false
        if (runCatching { windowManager.removeViewImmediate(root) }.isFailure) {
            runCatching { windowManager.removeView(root) }
        }
    }
}

internal class FloatingCardCoordinator(
    private val root: View,
    private val windowPort: FloatingCardWindowPort,
    private val geometryEngine: FloatingCardGeometryEngine,
    private val durationMillis: Long,
    private val interpolator: Interpolator,
    private val scope: CoroutineScope
) {
    private val surface: View = root.findViewById(R.id.module_floating_review_card_surface)
    private val panel: View = root.findViewById(R.id.module_floating_review_card_panel)
    private val tail: FloatingSpeechTailView = root.findViewById(
        R.id.module_floating_review_card_tail
    )
    private val scroll: ScrollView = root.findViewById(
        R.id.module_floating_review_content_scroll
    )
    private val frameDispatcher = FloatingCardFrameDispatcher(
        windowPort = windowPort,
        surfacePort = FloatingCardSurfacePort(::applySurfaceHeight)
    )

    private var environment: FloatingCardGeometryInput? = null
    private var naturalHeight = 1
    private var visualHeight = 1
    private var targetHeight = 1
    private var placement: FloatingSpeechPlacement? = null
    private var animator: ValueAnimator? = null
    private var visibilityRequested = false
    private var pendingPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var pendingPreDrawContinuation: CancellableContinuation<Unit>? = null
    private var pendingEnvironmentUpdate: PendingEnvironmentUpdate? = null
    private var environmentFrameCallback: Runnable? = null

    private data class PendingEnvironmentUpdate(
        val input: FloatingCardGeometryInput,
        val recomputeHeightLimit: Boolean
    )

    val attached: Boolean
        get() = windowPort.attached

    val visible: Boolean
        get() = visibilityRequested

    private val displayed: Boolean
        get() = root.visibility == View.VISIBLE

    val currentVisualHeight: Int
        get() = visualHeight

    val currentNaturalHeight: Int
        get() = naturalHeight

    fun attach() {
        root.visibility = View.INVISIBLE
        windowPort.attach()
        frameDispatcher.attach()
    }

    fun detach() {
        visibilityRequested = false
        frameDispatcher.detach()
        cancelAnimation()
        cancelPendingPreDraw()
        cancelPendingEnvironmentUpdate()
        root.visibility = View.INVISIBLE
        windowPort.detach()
    }

    fun show(input: FloatingCardGeometryInput) {
        visibilityRequested = true
        environment = input.copy(naturalHeight = naturalHeight)
        val transaction = beginTransaction()
        if (!displayed) root.visibility = View.INVISIBLE
        applyStableGeometry(
            transaction = transaction,
            reselectPlacement = placement == null
        )
        scheduleReveal(transaction)
    }

    fun hide() {
        visibilityRequested = false
        frameDispatcher.invalidate()
        cancelAnimation()
        cancelPendingPreDraw()
        cancelPendingEnvironmentUpdate()
        root.visibility = View.INVISIBLE
        if (environment != null && placement != null && attached) {
            val transaction = beginTransaction()
            applyStableGeometry(transaction, reselectPlacement = false)
            frameDispatcher.invalidate()
        }
        updateTouchableRegion(0, 0)
        placement = null
    }

    fun setAlpha(alpha: Float) {
        surface.alpha = alpha
    }

    suspend fun commitContent(
        input: FloatingCardGeometryInput,
        animate: Boolean,
        renderContent: () -> Unit
    ) {
        naturalHeight = input.naturalHeight.coerceAtLeast(1)
        environment = input.copy(naturalHeight = naturalHeight)
        transition(
            animate = animate && displayed,
            reselectPlacement = placement == null,
            renderContent = renderContent
        )
    }

    fun commitContentImmediately(
        input: FloatingCardGeometryInput,
        renderContent: () -> Unit
    ) {
        naturalHeight = input.naturalHeight.coerceAtLeast(1)
        environment = input.copy(naturalHeight = naturalHeight)
        val transaction = beginTransaction()
        renderContent()
        scroll.scrollTo(0, 0)
        applyStableGeometry(
            transaction = transaction,
            reselectPlacement = placement == null
        )
        scheduleReveal(transaction)
    }

    suspend fun relayout(
        input: FloatingCardGeometryInput,
        animate: Boolean,
        reselectPlacement: Boolean
    ) {
        naturalHeight = input.naturalHeight.coerceAtLeast(1)
        environment = input.copy(naturalHeight = naturalHeight)
        transition(
            animate = animate && displayed,
            reselectPlacement = reselectPlacement,
            renderContent = null
        )
    }

    fun moveAnchor(
        input: FloatingCardGeometryInput,
        recomputeHeightLimit: Boolean = false
    ) {
        environment = input.copy(naturalHeight = naturalHeight)
        val previous = pendingEnvironmentUpdate
        pendingEnvironmentUpdate = PendingEnvironmentUpdate(
            input = checkNotNull(environment),
            recomputeHeightLimit = recomputeHeightLimit ||
                previous?.recomputeHeightLimit == true
        )
        if (!visibilityRequested) {
            pendingEnvironmentUpdate = null
            return
        }
        if (environmentFrameCallback != null) return
        val callback = Runnable {
            environmentFrameCallback = null
            val update = pendingEnvironmentUpdate ?: return@Runnable
            pendingEnvironmentUpdate = null
            val latest = update.input
            environment = latest
            val resolvedPlacement = placement ?: geometryEngine.resolvePlacement(latest).also {
                placement = it
            }
            val resolvedHeight = geometryEngine.resolveTargetHeight(latest, resolvedPlacement)
            if (update.recomputeHeightLimit && resolvedHeight != targetHeight) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    transition(
                        animate = displayed,
                        reselectPlacement = false,
                        renderContent = null
                    )
                }
            } else {
                applyAnchoredCanvasGeometry()
            }
        }
        environmentFrameCallback = callback
        root.postOnAnimation(callback)
    }

    fun cancelForConfigurationChange() {
        frameDispatcher.invalidate()
        cancelAnimation()
        cancelPendingPreDraw()
        cancelPendingEnvironmentUpdate()
        placement = null
    }

    private suspend fun transition(
        animate: Boolean,
        reselectPlacement: Boolean,
        renderContent: (() -> Unit)?
    ) {
        val input = environment ?: return
        val resolvedPlacement = when {
            reselectPlacement || placement == null -> geometryEngine.resolvePlacement(input)
            else -> checkNotNull(placement)
        }
        placement = resolvedPlacement
        val resolvedTargetHeight = geometryEngine.resolveTargetHeight(input, resolvedPlacement)
        val startHeight = visualHeight.coerceAtLeast(1)
        targetHeight = resolvedTargetHeight

        val transaction = beginTransaction()

        val animationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled()
        if (!animate || !animationsEnabled || startHeight == resolvedTargetHeight) {
            renderContent?.invoke()
            scroll.scrollTo(0, 0)
            visualHeight = resolvedTargetHeight
            applyStableGeometry(
                transaction = transaction,
                reselectPlacement = false
            )
            scheduleReveal(transaction)
            return
        }

        val transitionSnapshot = geometryEngine.resolveTransition(
            input = input,
            placement = resolvedPlacement,
            visualHeight = startHeight,
            targetHeight = resolvedTargetHeight,
            existingCanvasHeight = windowPort.frame.height
        )
        applyTailGeometry(transitionSnapshot)
        if (!frameDispatcher.prepare(transaction, transitionSnapshot)) {
            ensureCurrent(transaction)
        }
        updateTouchableRegion(
            top = geometryEngine.surfaceTop(
                transitionSnapshot.window.height,
                maxOf(startHeight, resolvedTargetHeight),
                resolvedPlacement
            ),
            height = maxOf(startHeight, resolvedTargetHeight)
        )
        awaitCommittedLayout(transaction)
        ensureCurrent(transaction)

        renderContent?.invoke()
        scroll.scrollTo(0, 0)
        animateSurfaceHeight(
            transaction = transaction,
            startHeight = startHeight,
            endHeight = resolvedTargetHeight,
            canvasHeight = transitionSnapshot.window.height,
            placement = resolvedPlacement
        )
        ensureCurrent(transaction)

        visualHeight = resolvedTargetHeight
        val settledSnapshot = resolveSettledSnapshot(input, resolvedPlacement)
        applyTailGeometry(settledSnapshot)
        if (!frameDispatcher.normalize(transaction, settledSnapshot)) {
            ensureCurrent(transaction)
        }
        updateTouchableRegion(settledSnapshot.surface.top, resolvedTargetHeight)
        awaitCommittedLayout(transaction)
    }

    private fun applyStableGeometry(
        transaction: Long,
        reselectPlacement: Boolean
    ) {
        ensureCurrent(transaction)
        val input = environment ?: return
        val resolvedPlacement = when {
            reselectPlacement || placement == null -> geometryEngine.resolvePlacement(input)
            else -> checkNotNull(placement)
        }
        placement = resolvedPlacement
        val snapshot = resolveSettledSnapshot(input, resolvedPlacement)
        visualHeight = snapshot.targetHeight
        targetHeight = snapshot.targetHeight
        applyTailGeometry(snapshot)
        if (!frameDispatcher.normalize(transaction, snapshot)) {
            ensureCurrent(transaction)
        }
        updateTouchableRegion(snapshot.surface.top, snapshot.targetHeight)
    }

    private fun resolveSettledSnapshot(
        input: FloatingCardGeometryInput,
        placement: FloatingSpeechPlacement
    ): FloatingCardLayoutSnapshot {
        val stable = geometryEngine.resolveStable(input, placement)
        if (
            !displayed ||
            windowPort.supportsInstantPositionChanges ||
            windowPort.frame.height <= stable.window.height
        ) {
            return stable
        }
        return geometryEngine.resolveTransition(
            input = input,
            placement = placement,
            visualHeight = stable.targetHeight,
            targetHeight = stable.targetHeight,
            existingCanvasHeight = windowPort.frame.height
        )
    }

    private fun applyAnchoredCanvasGeometry() {
        val input = environment ?: return
        val resolvedPlacement = placement ?: geometryEngine.resolvePlacement(input).also {
            placement = it
        }
        val snapshot = geometryEngine.resolveTransition(
            input = input,
            placement = resolvedPlacement,
            visualHeight = visualHeight,
            targetHeight = targetHeight,
            existingCanvasHeight = windowPort.frame.height
        )
        applyTailGeometry(snapshot)
        frameDispatcher.moveWindow(snapshot.window)
    }

    private suspend fun animateSurfaceHeight(
        transaction: Long,
        startHeight: Int,
        endHeight: Int,
        canvasHeight: Int,
        placement: FloatingSpeechPlacement
    ) {
        suspendCancellableCoroutine { continuation ->
            var cancelled = false
            val valueAnimator = ValueAnimator.ofInt(startHeight, endHeight).apply {
                duration = durationMillis
                interpolator = this@FloatingCardCoordinator.interpolator
                addUpdateListener { running ->
                    if (!frameDispatcher.isCurrent(transaction) || !displayed) {
                        cancel()
                        return@addUpdateListener
                    }
                    val height = running.animatedValue as Int
                    if (height == visualHeight) return@addUpdateListener
                    if (
                        frameDispatcher.animateFrame(
                            transaction,
                            height,
                            canvasHeight,
                            placement
                        )
                    ) {
                        visualHeight = height
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (animator === animation) animator = null
                        if (!cancelled && frameDispatcher.isCurrent(transaction)) {
                            visualHeight = endHeight
                            frameDispatcher.animateFrame(
                                transaction,
                                endHeight,
                                canvasHeight,
                                placement
                            )
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })
            }
            animator = valueAnimator
            continuation.invokeOnCancellation {
                if (valueAnimator.isStarted) valueAnimator.cancel()
            }
            valueAnimator.start()
        }
    }

    private fun applySurfaceHeight(
        height: Int,
        canvasHeight: Int,
        placement: FloatingSpeechPlacement
    ) {
        val params = (surface.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        val gravity = Gravity.START or when (placement) {
            FloatingSpeechPlacement.ABOVE_PET -> Gravity.BOTTOM
            FloatingSpeechPlacement.BELOW_PET -> Gravity.TOP
        }
        if (
            params.width == ViewGroup.LayoutParams.MATCH_PARENT &&
            params.height == height &&
            params.gravity == gravity
        ) return
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = height.coerceIn(1, canvasHeight.coerceAtLeast(1))
        params.gravity = gravity
        surface.layoutParams = params
    }

    private fun applyTailGeometry(snapshot: FloatingCardLayoutSnapshot) {
        (panel.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            val offset = root.resources.getDimensionPixelSize(
                R.dimen.module_floating_review_tail_panel_offset
            )
            val top = if (snapshot.placement == FloatingSpeechPlacement.BELOW_PET) offset else 0
            val bottom = if (snapshot.placement == FloatingSpeechPlacement.ABOVE_PET) offset else 0
            if (params.topMargin != top || params.bottomMargin != bottom) {
                params.topMargin = top
                params.bottomMargin = bottom
                panel.layoutParams = params
            }
        }

        (tail.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            val width = root.resources.getDimensionPixelSize(
                R.dimen.module_floating_review_tail_width
            )
            val height = root.resources.getDimensionPixelSize(
                R.dimen.module_floating_review_tail_height
            )
            val left = snapshot.tailCenterX - (width * 0.82f).roundToInt()
            val gravity = Gravity.START or when (snapshot.placement) {
                FloatingSpeechPlacement.ABOVE_PET -> Gravity.BOTTOM
                FloatingSpeechPlacement.BELOW_PET -> Gravity.TOP
            }
            if (
                params.width != width ||
                params.height != height ||
                params.leftMargin != left ||
                params.gravity != gravity
            ) {
                params.width = width
                params.height = height
                params.leftMargin = left
                params.gravity = gravity
                tail.layoutParams = params
            }
        }
        tail.placement = snapshot.placement
    }

    private suspend fun awaitCommittedLayout(transaction: Long) {
        if (!root.isAttachedToWindow) return
        suspendCancellableCoroutine { continuation ->
            lateinit var listener: ViewTreeObserver.OnPreDrawListener
            listener = ViewTreeObserver.OnPreDrawListener {
                if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnPreDrawListener(listener)
                }
                if (pendingPreDrawListener === listener) pendingPreDrawListener = null
                if (pendingPreDrawContinuation === continuation) {
                    pendingPreDrawContinuation = null
                }
                root.postOnAnimation {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                true
            }
            pendingPreDrawListener = listener
            pendingPreDrawContinuation = continuation
            root.viewTreeObserver.addOnPreDrawListener(listener)
            root.requestLayout()
            continuation.invokeOnCancellation {
                if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnPreDrawListener(listener)
                }
                if (pendingPreDrawListener === listener) pendingPreDrawListener = null
                if (pendingPreDrawContinuation === continuation) {
                    pendingPreDrawContinuation = null
                }
            }
        }
        ensureCurrent(transaction)
    }

    private fun scheduleReveal(transaction: Long) {
        if (!visibilityRequested || displayed || !frameDispatcher.isCurrent(transaction)) return
        root.visibility = View.INVISIBLE
        lateinit var listener: ViewTreeObserver.OnPreDrawListener
        listener = ViewTreeObserver.OnPreDrawListener {
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.removeOnPreDrawListener(listener)
            }
            if (pendingPreDrawListener === listener) pendingPreDrawListener = null
            root.postOnAnimation {
                if (
                    visibilityRequested &&
                    frameDispatcher.isCurrent(transaction) &&
                    root.isAttachedToWindow
                ) {
                    root.visibility = View.VISIBLE
                    val resolvedPlacement = placement ?: return@postOnAnimation
                    val top = geometryEngine.surfaceTop(
                        canvasHeight = windowPort.frame.height,
                        visualHeight = visualHeight,
                        placement = resolvedPlacement
                    )
                    updateTouchableRegion(top, visualHeight)
                }
            }
            true
        }
        pendingPreDrawListener = listener
        root.viewTreeObserver.addOnPreDrawListener(listener)
        root.requestLayout()
    }

    private fun updateTouchableRegion(top: Int, height: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !root.isAttachedToWindow) {
            return
        }
        val region = if (height <= 0) {
            Region()
        } else {
            Region(0, top, root.width.coerceAtLeast(windowPort.frame.width), top + height)
        }
        root.rootSurfaceControl?.setTouchableRegion(region)
    }

    private fun cancelAnimation() {
        val running = animator ?: return
        animator = null
        running.cancel()
    }

    private fun beginTransaction(): Long {
        val transaction = frameDispatcher.beginTransaction()
        cancelAnimation()
        cancelPendingPreDraw()
        return transaction
    }

    private fun cancelPendingEnvironmentUpdate() {
        pendingEnvironmentUpdate = null
        environmentFrameCallback?.let(root::removeCallbacks)
        environmentFrameCallback = null
    }

    private fun cancelPendingPreDraw() {
        val listener = pendingPreDrawListener
        pendingPreDrawListener = null
        if (listener != null && root.viewTreeObserver.isAlive) {
            root.viewTreeObserver.removeOnPreDrawListener(listener)
        }
        pendingPreDrawContinuation?.cancel(CancellationException("Card layout superseded"))
        pendingPreDrawContinuation = null
    }

    private fun ensureCurrent(transaction: Long) {
        if (!frameDispatcher.isCurrent(transaction)) throw CancellationException()
    }
}
