package com.towmech.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.towmech.app.R

/**
 * Foreground service that keeps the provider session "active" while Online.
 * This improves Heads-Up reliability for job request notifications.
 */
class ProviderForegroundService : Service() {

    companion object {
        private const val FOREGROUND_ID = 9001

        fun start(context: Context) {
            val intent = Intent(context, ProviderForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProviderForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Ensure channel exists (safe to call repeatedly)
        NotificationChannels.create(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // This is NOT the job notification — it is a quiet ongoing "online" indicator.
        // Job notifications are still shown by TowMechFirebaseMessagingService.
        val notification = NotificationCompat.Builder(this, NotificationChannels.PROVIDER_JOBS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("TowMech Provider Online")
            .setContentText("Waiting for job requests…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true) // ✅ don't play sound for the service notification
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(FOREGROUND_ID, notification)

        // ✅ Keep running until explicitly stopped (when provider goes Offline / logout)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}