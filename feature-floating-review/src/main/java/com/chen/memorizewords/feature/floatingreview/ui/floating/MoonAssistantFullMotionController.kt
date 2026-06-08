package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import com.chen.memorizewords.feature.floatingreview.R
import kotlin.math.roundToInt

enum class MoonAssistantAction {
    IDLE,
    TAP,
    OPEN_CARD_TO_LEFT,
    OPEN_CARD_TO_RIGHT,
    CLOSE_CARD,
    DRAG,
    DROP,
    DOCK_LEFT,
    DOCK_RIGHT,
    UNDOCK_LEFT,
    UNDOCK_RIGHT,
    REMIND,
    LOADING,
    SUCCESS,
    SAD,
    SLEEP,
    WAKE,
    SPEAK,
    POINT_LEFT,
    POINT_RIGHT,
    SURPRISED,
    ANGRY
}

class MoonAssistantFullMotionController(
    private val petRootView: View,
    private val petImage: ImageView
) {
    private val handler = Handler(Looper.getMainLooper())
    private var activeAnimator: Animator? = null
    private var blinkRunnable: Runnable? = null
    private var returnRunnable: Runnable? = null

    fun play(action: MoonAssistantAction) {
        cancelCurrent()
        when (action) {
            MoonAssistantAction.IDLE -> playIdle()
            MoonAssistantAction.TAP -> oneShot(R.drawable.moon_assistant_tap_pose, 760L) {
                pivotBottom()
                playSet(
                    scaleX(760L, 1f, 1.045f, 0.985f, 1f),
                    scaleY(760L, 1f, 0.965f, 1.025f, 1f),
                    rotate(760L, 0f, -5f, 4f, 0f),
                    moveY(760L, 0f, -4f, 2f, 0f)
                )
            }
            MoonAssistantAction.OPEN_CARD_TO_LEFT -> oneShot(R.drawable.moon_assistant_open_card_left_pose, 900L) {
                pivotBottom()
                playSet(moveX(900L, 12f, 0f), rotate(900L, 3f, 0f), scaleY(900L, 0.99f, 1f))
            }
            MoonAssistantAction.OPEN_CARD_TO_RIGHT -> oneShot(R.drawable.moon_assistant_open_card_right_pose, 900L) {
                pivotBottom()
                playSet(moveX(900L, -12f, 0f), rotate(900L, -3f, 0f), scaleY(900L, 0.99f, 1f))
            }
            MoonAssistantAction.CLOSE_CARD -> oneShot(R.drawable.moon_assistant_close_card_pose, 560L) {
                pivotBottom()
                playSet(rotate(560L, 0f, 5f, -3f, 0f), moveY(560L, 0f, -2f, 0f))
            }
            MoonAssistantAction.DRAG -> playDrag()
            MoonAssistantAction.DROP -> oneShot(R.drawable.moon_assistant_drop_pose, 620L) {
                pivotBottom()
                playSet(
                    scaleY(620L, 0.88f, 1.06f, 1f),
                    scaleX(620L, 1.08f, 0.98f, 1f),
                    moveY(620L, 8f, -6f, 0f)
                )
            }
            MoonAssistantAction.DOCK_LEFT -> playDock(R.drawable.moon_assistant_dock_left_pose, fromLeft = true)
            MoonAssistantAction.DOCK_RIGHT -> playDock(R.drawable.moon_assistant_dock_right_pose, fromLeft = false)
            MoonAssistantAction.UNDOCK_LEFT -> oneShot(R.drawable.moon_assistant_dock_left_pose, 420L, MoonAssistantAction.DRAG) {
                pivotCenter()
                playSet(moveX(420L, -34f, 0f), rotate(420L, -2f, 0f))
            }
            MoonAssistantAction.UNDOCK_RIGHT -> oneShot(R.drawable.moon_assistant_dock_right_pose, 420L, MoonAssistantAction.DRAG) {
                pivotCenter()
                playSet(moveX(420L, 34f, 0f), rotate(420L, 2f, 0f))
            }
            MoonAssistantAction.REMIND -> oneShot(R.drawable.moon_assistant_remind_pose, 1900L) {
                pivotBottom()
                playSet(
                    moveY(460L, 0f, -12f, 0f).repeat(3),
                    rotate(460L, 0f, -6f, 6f, 0f).repeat(3)
                )
            }
            MoonAssistantAction.LOADING -> loop(R.drawable.moon_assistant_loading_pose) {
                pivotBottom()
                playSet(
                    rotate(900L, -3f, 3f).loopReverse(),
                    moveY(900L, 0f, 4f).loopReverse()
                )
            }
            MoonAssistantAction.SUCCESS -> oneShot(R.drawable.moon_assistant_success_pose, 980L) {
                pivotBottom()
                activeAnimator = ObjectAnimator.ofFloat(petImage, View.TRANSLATION_Y, 0f, -34f, 0f, -8f, 0f).apply {
                    duration = 980L
                    interpolator = OvershootInterpolator(1.4f)
                    start()
                }
            }
            MoonAssistantAction.SAD -> oneShot(R.drawable.moon_assistant_sad_pose, 940L) {
                pivotBottom()
                playSet(moveY(940L, 0f, 5f, 2f, 0f), rotate(940L, 0f, -2f, 1f, 0f))
            }
            MoonAssistantAction.SLEEP -> loop(R.drawable.moon_assistant_sleep_pose) {
                pivotBottom()
                playSet(scaleY(1500L, 1f, 0.985f).loopReverse(), moveY(1500L, 0f, 3f).loopReverse())
            }
            MoonAssistantAction.WAKE -> oneShot(R.drawable.moon_assistant_wake_pose, 1080L) {
                pivotBottom()
                playSet(scaleY(1080L, 0.96f, 1.06f, 1f), moveY(1080L, 5f, -8f, 0f), rotate(1080L, -4f, 4f, 0f))
            }
            MoonAssistantAction.SPEAK -> loop(R.drawable.moon_assistant_speak_pose) {
                pivotBottom()
                playSet(scaleY(420L, 1f, 0.992f).loopReverse(), rotate(420L, -1.5f, 1.5f).loopReverse())
            }
            MoonAssistantAction.POINT_LEFT -> loop(R.drawable.moon_assistant_point_left_pose) {
                pivotBottom()
                playSet(moveX(820L, 0f, 4f).loopReverse(), rotate(820L, 0f, 1.5f).loopReverse())
            }
            MoonAssistantAction.POINT_RIGHT -> loop(R.drawable.moon_assistant_point_right_pose) {
                pivotBottom()
                playSet(moveX(820L, 0f, -4f).loopReverse(), rotate(820L, 0f, -1.5f).loopReverse())
            }
            MoonAssistantAction.SURPRISED -> oneShot(R.drawable.moon_assistant_surprised_pose, 680L) {
                pivotBottom()
                playSet(scaleX(680L, 1f, 1.08f, 0.98f, 1f), scaleY(680L, 1f, 0.94f, 1.04f, 1f), moveY(680L, 0f, -10f, 0f))
            }
            MoonAssistantAction.ANGRY -> oneShot(R.drawable.moon_assistant_angry_pose, 980L) {
                pivotBottom()
                playSet(rotate(240L, -4f, 4f).repeat(3), moveY(240L, 0f, -5f, 0f).repeat(3))
            }
        }
    }

    fun playOpenCard(cardOnLeft: Boolean) {
        play(if (cardOnLeft) MoonAssistantAction.OPEN_CARD_TO_LEFT else MoonAssistantAction.OPEN_CARD_TO_RIGHT)
    }

    fun playPoint(cardOnLeft: Boolean) {
        play(if (cardOnLeft) MoonAssistantAction.POINT_LEFT else MoonAssistantAction.POINT_RIGHT)
    }

    fun stop() {
        cancelCurrent()
        resetTransform()
    }

    fun updateDragWindowPosition(
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
        rawTouchX: Float,
        rawTouchY: Float
    ) {
        val grabAnchorX = petImage.left + petImage.width * 0.5f
        val grabAnchorY = petImage.top + petImage.height * 0.07f
        params.x = (rawTouchX - grabAnchorX).roundToInt()
        params.y = (rawTouchY - grabAnchorY).roundToInt()
        windowManager.updateViewLayout(petRootView, params)
    }

    private fun playIdle() {
        petImage.setImageResource(R.drawable.moon_assistant_idle_pose)
        petImage.post {
            pivotBottom()
            playSet(
                scaleY(900L, 1f, 0.985f).loopReverse(),
                scaleX(900L, 1f, 1.006f).loopReverse(),
                moveY(900L, 0f, 3f).loopReverse()
            )
            startBlinkLoop()
        }
    }

    private fun playDrag() {
        petImage.setImageResource(R.drawable.moon_assistant_drag_lift_pose)
        petImage.post {
            petImage.pivotX = petImage.width * 0.5f
            petImage.pivotY = petImage.height * 0.07f
            playSet(rotate(480L, -4f, 4f).loopReverse(), moveY(620L, 0f, 7f).loopReverse())
        }
    }

    private fun playDock(drawableRes: Int, fromLeft: Boolean) {
        petImage.setImageResource(drawableRes)
        petImage.post {
            pivotCenter()
            val x = if (fromLeft) 0f to 5f else 0f to -5f
            playSet(moveX(1200L, x.first, x.second).loopReverse(), moveY(1400L, 0f, 3f).loopReverse())
        }
    }

    private fun oneShot(
        drawableRes: Int,
        durationMs: Long,
        returnAction: MoonAssistantAction = MoonAssistantAction.IDLE,
        block: () -> Unit
    ) {
        petImage.setImageResource(drawableRes)
        petImage.post {
            block()
            returnRunnable = Runnable { play(returnAction) }
            handler.postDelayed(returnRunnable!!, durationMs)
        }
    }

    private fun loop(drawableRes: Int, block: () -> Unit) {
        petImage.setImageResource(drawableRes)
        petImage.post(block)
    }

    private fun playSet(vararg animators: ObjectAnimator) {
        val set = AnimatorSet()
        set.interpolator = AccelerateDecelerateInterpolator()
        set.playTogether(animators.toList())
        activeAnimator = set
        set.start()
    }

    private fun startBlinkLoop() {
        val blink = object : Runnable {
            override fun run() {
                petImage.setImageResource(R.drawable.moon_assistant_idle_blink_pose)
                handler.postDelayed({
                    if (blinkRunnable === this) {
                        petImage.setImageResource(R.drawable.moon_assistant_idle_pose)
                    }
                }, 95L)
                handler.postDelayed(this, 3200L)
            }
        }
        blinkRunnable = blink
        handler.postDelayed(blink, 1200L)
    }

    private fun cancelCurrent() {
        activeAnimator?.cancel()
        activeAnimator = null
        blinkRunnable?.let { handler.removeCallbacks(it) }
        blinkRunnable = null
        returnRunnable?.let { handler.removeCallbacks(it) }
        returnRunnable = null
        resetTransform()
    }

    private fun resetTransform() {
        petImage.rotation = 0f
        petImage.translationX = 0f
        petImage.translationY = 0f
        petImage.scaleX = 1f
        petImage.scaleY = 1f
        petImage.alpha = 1f
    }

    private fun pivotBottom() {
        petImage.pivotX = petImage.width * 0.5f
        petImage.pivotY = petImage.height * 0.86f
    }

    private fun pivotCenter() {
        petImage.pivotX = petImage.width * 0.5f
        petImage.pivotY = petImage.height * 0.5f
    }

    private fun moveX(duration: Long, vararg values: Float) =
        ObjectAnimator.ofFloat(petImage, View.TRANSLATION_X, *values).withDuration(duration)

    private fun moveY(duration: Long, vararg values: Float) =
        ObjectAnimator.ofFloat(petImage, View.TRANSLATION_Y, *values).withDuration(duration)

    private fun rotate(duration: Long, vararg values: Float) =
        ObjectAnimator.ofFloat(petImage, View.ROTATION, *values).withDuration(duration)

    private fun scaleX(duration: Long, vararg values: Float) =
        ObjectAnimator.ofFloat(petImage, View.SCALE_X, *values).withDuration(duration)

    private fun scaleY(duration: Long, vararg values: Float) =
        ObjectAnimator.ofFloat(petImage, View.SCALE_Y, *values).withDuration(duration)

    private fun ObjectAnimator.withDuration(durationMs: Long) = apply {
        duration = durationMs
    }

    private fun ObjectAnimator.loopReverse() = apply {
        repeatCount = ObjectAnimator.INFINITE
        repeatMode = ObjectAnimator.REVERSE
    }

    private fun ObjectAnimator.repeat(count: Int) = apply {
        repeatCount = count
        repeatMode = ObjectAnimator.RESTART
    }
}
