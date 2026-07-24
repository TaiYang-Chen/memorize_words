package com.chen.memorizewords.feature.floatingreview.ui.floating

internal enum class FloatingNotificationUpdateAction {
    KEEP,
    CANCEL_PENDING,
    REPLACE_PENDING
}

internal fun resolveFloatingNotificationUpdateAction(
    lastDeliveredContent: String?,
    pendingContent: String?,
    incomingContent: String
): FloatingNotificationUpdateAction = when {
    incomingContent == pendingContent -> FloatingNotificationUpdateAction.KEEP
    incomingContent == lastDeliveredContent && pendingContent != null ->
        FloatingNotificationUpdateAction.CANCEL_PENDING
    incomingContent == lastDeliveredContent -> FloatingNotificationUpdateAction.KEEP
    else -> FloatingNotificationUpdateAction.REPLACE_PENDING
}

internal fun shouldUpdateFloatingCardWindow(
    currentX: Int,
    currentY: Int,
    targetX: Int,
    targetY: Int
): Boolean = currentX != targetX || currentY != targetY

internal const val FLOATING_NOTIFICATION_FIRST_FRAME_DELAY_MS = 80L
