package app.mohakash.apod.data.storage

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mohakash.apod.BuildConfig
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
    private val supportChannelId = "mohakash_support"

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
            manager.createNotificationChannel(
                NotificationChannel(
                    supportChannelId,
                    context.getString(R.string.notification_channel_support_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notification_channel_support_description)
                    enableLights(false)
                    enableVibration(false)
                },
            )
        }
    }

    companion object {
        // The singleton deliberately keeps the *application* context only, never an Activity.
        // Lint flags any Context field; this one cannot leak a screen.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: NotificationHelper? = null

        const val NOTIFICATION_ID = 1001
        const val DONATION_NOTIFICATION_ID = 1002

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
        // Android 13+ can revoke/never grant POST_NOTIFICATIONS; never pretend a notification
        // was shown when the system will silently drop it.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val title = entry.title
        val description = entry.explanation.take(512)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // `apod_url` is optional in the API and may be malformed. Only expose the "view" action
        // when it is a real https URL: Uri.parse(null) would throw and break the notification.
        val viewApodPending = validHttpsUri(entry.apodUrl)?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            PendingIntent.getActivity(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

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

        val builder = NotificationCompat.Builder(context, channelId)
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

        if (viewApodPending != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    context.getString(R.string.notification_action_view_apod),
                    viewApodPending,
                ).build(),
            )
        }

        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_settings),
                settingsPending,
            ).build(),
        )

        val notification = builder.build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Returns [raw] as a Uri only when it parses to an absolute https URL with a host. */
    private fun validHttpsUri(raw: String?): Uri? {
        if (raw.isNullOrBlank()) return null
        val uri = try {
            Uri.parse(raw)
        } catch (error: Exception) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        return uri
    }

    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * A rare, optional support reminder. Never auto-opens the browser; the user must tap the
     * notification or its "Support Development" action.
     */
    fun notifyDonation() {
        if (!areNotificationsEnabled()) return
        val uri = validHttpsUri(BuildConfig.DONATE_URL) ?: return
        val donateIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val donatePending = PendingIntent.getActivity(
            context,
            3,
            donateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, supportChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_donation_title))
            .setContentText(context.getString(R.string.notification_donation_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_donation_body)),
            )
            .setContentIntent(donatePending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_heart_24,
                    context.getString(R.string.settings_support_action),
                    donatePending,
                ).build(),
            )
            .build()
        manager.notify(DONATION_NOTIFICATION_ID, notification)
    }
}
