package com.chen.memorizewords.feature.home.notification

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chen.memorizewords.core.navigation.WordBookEntry
import com.chen.memorizewords.core.navigation.WordBookEntryDestination
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateImportance
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdatePrompt
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WordBookUpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wordBookEntry: WordBookEntry
) {
    @SuppressLint("MissingPermission")
    fun notifyUpdate(prompt: WordBookUpdatePrompt) {
        if (!canPostNotifications()) return
        ensureChannel()
        val candidate = prompt.candidate
        val deepLink = prompt.deepLink?.takeIf { it.isNotBlank() }
            ?: WordBookEntryDestination.myBooksDeepLink(source = prompt.trigger.name.lowercase())
        val content = prompt.summaryText?.takeIf { it.isNotBlank() }
            ?: context.getString(
                R.string.feature_home_wordbook_update_notify_content,
                candidate.bookName
            )

        val intent = wordBookEntry.createWordBookIntent(
            context = context,
            deepLink = deepLink
        ).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            buildNotificationId(candidate.bookId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.feature_home_wordbook_update_notify_title)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(resolvePriority(candidate.importance))
            .build()

        runCatching {
            val manager = NotificationManagerCompat.from(context)
            val notificationId = buildNotificationId(candidate.bookId)
            val collapseKey = prompt.collapseKey?.takeIf { it.isNotBlank() }
            if (collapseKey == null) {
                manager.notify(notificationId, notification)
            } else {
                manager.notify(collapseKey, notificationId, notification)
            }
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.feature_home_wordbook_update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun buildNotificationId(bookId: Long): Int {
        return (bookId % 1_000_000L).toInt() + 7_000
    }

    private fun resolvePriority(importance: WordBookUpdateImportance): Int {
        return when (importance) {
            WordBookUpdateImportance.LOW -> NotificationCompat.PRIORITY_LOW
            WordBookUpdateImportance.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
            WordBookUpdateImportance.HIGH -> NotificationCompat.PRIORITY_HIGH
            WordBookUpdateImportance.CRITICAL -> NotificationCompat.PRIORITY_MAX
        }
    }

    private companion object {
        private const val CHANNEL_ID = "wordbook_update"
    }
}
