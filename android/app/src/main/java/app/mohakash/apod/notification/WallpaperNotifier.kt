package app.mohakash.apod.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mohakash.apod.MainActivity
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ApodEntry

/**
 * Tells the user that a new wallpaper picture arrived.
 *
 * One notification per installed picture: the sync engine only reports an update when the
 * picture actually changed, so the same entry never notifies twice. The notification opens the
 * app when tapped and disappears as soon as it is opened. When the user denied the
 * POST_NOTIFICATIONS permission nothing is published — updating the wallpaper still works.
 */
class WallpaperNotifier(private val context: Context) {

    /** Publishes the "new wallpaper" notification for [entry]. */
    fun notifyNewWallpaper(entry: ApodEntry) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        createChannel()

        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_new_wallpaper_title))
            .setContentText(entry.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(entry.title)
                    .setSummaryText(context.getString(R.string.notification_new_wallpaper_summary)),
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_wallpaper_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_wallpaper_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        /** The notification channel for new-wallpaper alerts. */
        const val CHANNEL_ID = "wallpaper_updates"

        /** A stable id so every new picture replaces the previous alert. */
        const val NOTIFICATION_ID = 1001

        private const val REQUEST_CODE = 42
    }
}
