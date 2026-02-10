package com.openclaw.dashboard.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.openclaw.dashboard.MainActivity
import com.openclaw.dashboard.R

/**
 * Helper class for managing notifications
 */
object NotificationHelper {

    const val CHANNEL_ID_CHAT = "chat_replies"
    private const val NOTIFICATION_ID_CHAT = 1001

    /**
     * Create notification channels (must be called on app startup)
     */
    fun createNotificationChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID_CHAT,
            context.getString(R.string.notification_channel_chat),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_chat_desc)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * Show a notification for AI reply
     */
    fun showChatReplyNotification(
        context: Context,
        sessionTitle: String?,
        messagePreview: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = sessionTitle ?: context.getString(R.string.notification_chat_default_title)

        // Truncate long messages
        val preview = if (messagePreview.length > 200) {
            messagePreview.take(200) + "…"
        } else {
            messagePreview
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_CHAT, notification)
    }

    /**
     * Cancel chat notification (e.g., when user opens the chat)
     */
    fun cancelChatNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_CHAT)
    }
}
