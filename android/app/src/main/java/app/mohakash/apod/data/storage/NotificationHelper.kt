package app.mohakash.apod.data.storage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import app.mohakash.apod.MainActivity
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ApodEntry

/**
 * Posts notifications when the live wallpaper picture changes.
 *
 * Android 13 (API 33) and newer require the `POST_NOTIFICATIONS` runtime permission;
 * this helper posts the notification and gracefully degrades when the permission is
 * not granted.
 */
class NotificationHelper private constructor(private val context: Context) {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "mohakash_wallpaper"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_wallpaper_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_wallpaper_description)
                enableLights(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        @Volatile private var instance: NotificationHelper? = null

        const val NOTIFICATION_ID = 1001

        fun get(context: Context): NotificationHelper =
            instance ?: synchronized(this) {
                instance ?: NotificationHelper(context.applicationContext).also { instance = it }
            }
    }

    /**
     * Notifies the user that the live wallpaper picture was updated.
     *
     * @param entry the new APOD entry whose picture is now the wallpaper
     */
    fun notifyWallpaperChanged(entry: ApodEntry) {
        val title = entry.title
        val description = entry.explanation.take(512)
        val apodUrl = entry.apodUrl

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val viewApodIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apodUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val viewApodPending = PendingIntent.getActivity(
            context,
            1,
            viewApodIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val settingsIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val settingsPending = PendingIntent.getActivity(
            context,
            2,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(description.ifBlank { context.getString(R.string.notification_no_description) })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(description),
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    context.getString(R.string.notification_action_open_app),
                    openAppPending,
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    context.getString(R.string.notification_action_view_apod),
                    viewApodPending,
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    context.getString(R.string.notification_action_settings),
                    settingsPending,
                ).build(),
            )
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
