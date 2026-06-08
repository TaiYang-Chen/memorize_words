package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.graphics.drawable.AnimationDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.chen.memorizewords.feature.floatingreview.R
import kotlin.math.roundToInt

enum class MoonAssistantFrameAction(val stateName: String) {
    IDLE("idle"),
    TAP("tap"),
    OPEN_CARD_LEFT("open_card_left"),
    OPEN_CARD_RIGHT("open_card_right"),
    CLOSE_CARD("close_card"),
    DRAG("drag"),
    DROP("drop"),
    DOCK_LEFT("dock_left"),
    DOCK_RIGHT("dock_right"),
    UNDOCK_LEFT("undock_left"),
    UNDOCK_RIGHT("undock_right"),
    REMIND("remind"),
    LOADING("loading"),
    SUCCESS("success"),
    SAD("sad"),
    SLEEP("sleep"),
    WAKE("wake"),
    SPEAK("speak"),
    POINT_LEFT("point_left"),
    POINT_RIGHT("point_right"),
    SURPRISED("surprised"),
    ANGRY("angry");

    companion object {
        fun fromStateName(stateName: String): MoonAssistantFrameAction {
            return values().firstOrNull { it.stateName == stateName }
                ?: error("Unknown MoonAssistantFrameAction state: $stateName")
        }
    }
}

class MoonAssistantSequenceFrameController(
    private val petRootView: View,
    private val petImage: ImageView
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentAction: MoonAssistantFrameAction? = null
    private var activeDrawable: AnimationDrawable? = null
    private var returnRunnable: Runnable? = null

    fun play(stateName: String) {
        play(MoonAssistantFrameAction.fromStateName(stateName))
    }

    fun play(action: MoonAssistantFrameAction) {
        if (currentAction == action && action.isLoop()) return

        cancelReturn()
        stopCurrentDrawable()
        currentAction = action

        petImage.setImageResource(action.drawableRes())
        val drawable = petImage.drawable as? AnimationDrawable
        activeDrawable = drawable

        petImage.post {
            drawable?.stop()
            drawable?.start()
        }

        val returnAction = action.returnAction()
        if (returnAction != null) {
            returnRunnable = Runnable {
                if (currentAction == action) {
                    play(returnAction)
                }
            }
            handler.postDelayed(returnRunnable!!, action.durationMs())
        }
    }

    fun playOpenCard(cardOnLeft: Boolean) {
        play(if (cardOnLeft) MoonAssistantFrameAction.OPEN_CARD_LEFT else MoonAssistantFrameAction.OPEN_CARD_RIGHT)
    }

    fun playPoint(cardOnLeft: Boolean) {
        play(if (cardOnLeft) MoonAssistantFrameAction.POINT_LEFT else MoonAssistantFrameAction.POINT_RIGHT)
    }

    fun stop() {
        cancelReturn()
        stopCurrentDrawable()
        currentAction = null
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

    private fun cancelReturn() {
        returnRunnable?.let { handler.removeCallbacks(it) }
        returnRunnable = null
    }

    private fun stopCurrentDrawable() {
        activeDrawable?.stop()
        activeDrawable = null
    }

    private fun MoonAssistantFrameAction.isLoop(): Boolean {
        return when (this) {
            MoonAssistantFrameAction.IDLE,
            MoonAssistantFrameAction.DRAG,
            MoonAssistantFrameAction.DOCK_LEFT,
            MoonAssistantFrameAction.DOCK_RIGHT,
            MoonAssistantFrameAction.LOADING,
            MoonAssistantFrameAction.SLEEP,
            MoonAssistantFrameAction.SPEAK,
            MoonAssistantFrameAction.POINT_LEFT,
            MoonAssistantFrameAction.POINT_RIGHT -> true
            else -> false
        }
    }

    private fun MoonAssistantFrameAction.returnAction(): MoonAssistantFrameAction? {
        return when (this) {
            MoonAssistantFrameAction.UNDOCK_LEFT,
            MoonAssistantFrameAction.UNDOCK_RIGHT -> MoonAssistantFrameAction.DRAG
            MoonAssistantFrameAction.TAP,
            MoonAssistantFrameAction.OPEN_CARD_LEFT,
            MoonAssistantFrameAction.OPEN_CARD_RIGHT,
            MoonAssistantFrameAction.CLOSE_CARD,
            MoonAssistantFrameAction.DROP,
            MoonAssistantFrameAction.REMIND,
            MoonAssistantFrameAction.SUCCESS,
            MoonAssistantFrameAction.SAD,
            MoonAssistantFrameAction.WAKE,
            MoonAssistantFrameAction.SURPRISED,
            MoonAssistantFrameAction.ANGRY -> MoonAssistantFrameAction.IDLE
            else -> null
        }
    }

    private fun MoonAssistantFrameAction.durationMs(): Long {
        return when (this) {
            MoonAssistantFrameAction.TAP,
            MoonAssistantFrameAction.OPEN_CARD_LEFT,
            MoonAssistantFrameAction.OPEN_CARD_RIGHT,
            MoonAssistantFrameAction.SUCCESS,
            MoonAssistantFrameAction.SAD,
            MoonAssistantFrameAction.ANGRY -> 1_000L
            MoonAssistantFrameAction.CLOSE_CARD,
            MoonAssistantFrameAction.DROP,
            MoonAssistantFrameAction.SURPRISED,
            MoonAssistantFrameAction.UNDOCK_LEFT,
            MoonAssistantFrameAction.UNDOCK_RIGHT -> 750L
            MoonAssistantFrameAction.REMIND -> 1_500L
            MoonAssistantFrameAction.WAKE -> 1_250L
            else -> 0L
        }
    }

    private fun MoonAssistantFrameAction.drawableRes(): Int {
        return when (this) {
            MoonAssistantFrameAction.IDLE -> R.drawable.moon_assistant_idle
            MoonAssistantFrameAction.TAP -> R.drawable.moon_assistant_tap
            MoonAssistantFrameAction.OPEN_CARD_LEFT -> R.drawable.moon_assistant_open_card_left
            MoonAssistantFrameAction.OPEN_CARD_RIGHT -> R.drawable.moon_assistant_open_card_right
            MoonAssistantFrameAction.CLOSE_CARD -> R.drawable.moon_assistant_close_card
            MoonAssistantFrameAction.DRAG -> R.drawable.moon_assistant_drag
            MoonAssistantFrameAction.DROP -> R.drawable.moon_assistant_drop
            MoonAssistantFrameAction.DOCK_LEFT -> R.drawable.moon_assistant_dock_left
            MoonAssistantFrameAction.DOCK_RIGHT -> R.drawable.moon_assistant_dock_right
            MoonAssistantFrameAction.UNDOCK_LEFT -> R.drawable.moon_assistant_undock_left
            MoonAssistantFrameAction.UNDOCK_RIGHT -> R.drawable.moon_assistant_undock_right
            MoonAssistantFrameAction.REMIND -> R.drawable.moon_assistant_remind
            MoonAssistantFrameAction.LOADING -> R.drawable.moon_assistant_loading
            MoonAssistantFrameAction.SUCCESS -> R.drawable.moon_assistant_success
            MoonAssistantFrameAction.SAD -> R.drawable.moon_assistant_sad
            MoonAssistantFrameAction.SLEEP -> R.drawable.moon_assistant_sleep
            MoonAssistantFrameAction.WAKE -> R.drawable.moon_assistant_wake
            MoonAssistantFrameAction.SPEAK -> R.drawable.moon_assistant_speak
            MoonAssistantFrameAction.POINT_LEFT -> R.drawable.moon_assistant_point_left
            MoonAssistantFrameAction.POINT_RIGHT -> R.drawable.moon_assistant_point_right
            MoonAssistantFrameAction.SURPRISED -> R.drawable.moon_assistant_surprised
            MoonAssistantFrameAction.ANGRY -> R.drawable.moon_assistant_angry
        }
    }
}
