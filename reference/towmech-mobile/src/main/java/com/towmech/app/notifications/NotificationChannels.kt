// NotificationChannels.kt
package com.towmech.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import com.towmech.app.R

object NotificationChannels {

    /**
     * ✅ IMPORTANT:
     * - If you ever installed the app before custom sound/importance changes,
     *   Android caches the channel settings.
     * - Bump the channel ID (v2/v3) OR uninstall/reinstall to apply new sound/importance.
     */
    const val PROVIDER_JOBS_CHANNEL_ID = "provider_jobs_channel_v2"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ✅ Avoid recreating if it already exists
        val existing = manager.getNotificationChannel(PROVIDER_JOBS_CHANNEL_ID)
        if (existing != null) return

        val soundUri: Uri =
            Uri.parse("android.resource://${context.packageName}/${R.raw.car_hoot}")

        val audioAttributes = AudioAttributes.Builder()
            // ✅ For Bolt-style "incoming job", treat as ringtone/call-like
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            PROVIDER_JOBS_CHANNEL_ID,
            "Provider Job Requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new provider job requests"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250, 150, 400)

            // ✅ Heads-up / lockscreen friendliness
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(channel)
    }
}