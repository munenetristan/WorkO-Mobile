package com.towmech.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.towmech.app.MainActivity
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.RegisterTokenRequest
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class TowMechFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_DEBUG"

        private const val PREF = "towmech_fcm_pref"
        private const val KEY_LAST_FCM = "last_fcm_token"
        private const val KEY_PENDING_UPLOAD = "pending_fcm_upload"
    }

    private fun saveTokenLocally(token: String) {
        val sp = getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_LAST_FCM, token)
            .putBoolean(KEY_PENDING_UPLOAD, true)
            .apply()
    }

    private fun markUploaded() {
        val sp = getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_PENDING_UPLOAD, false).apply()
    }

    private fun uploadTokenIfPossible(token: String) {
        val jwt = TokenManager.getToken(this)

        if (jwt.isNullOrBlank()) {
            Log.w(TAG, "⚠️ JWT missing. Token saved locally; will upload after login.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.apiService.registerFcmToken(
                    token = "Bearer $jwt",
                    request = RegisterTokenRequest(fcmToken = token)
                )
                markUploaded()
                Log.d(TAG, "✅ FCM token uploaded to backend successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to upload FCM token: ${e.message}", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "✅ NEW FCM TOKEN: $token")

        saveTokenLocally(token)
        uploadTokenIfPossible(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "✅ FCM MESSAGE RECEIVED")
        Log.d(TAG, "notification=${message.notification}")
        Log.d(TAG, "data=${message.data}")

        val dataTitle = message.data["title"]
        val dataBody = message.data["body"]

        val title = dataTitle ?: message.notification?.title ?: "TowMech"
        val body = dataBody ?: message.notification?.body ?: "You have a new job request"

        val open = message.data["open"] ?: "job_requests"
        val jobId = message.data["jobId"] ?: message.data["job_id"]

        showHeadsUpJobNotification(
            title = title,
            body = body,
            open = open,
            jobId = jobId
        )
    }

    private fun showHeadsUpJobNotification(
        title: String,
        body: String,
        open: String?,
        jobId: String?
    ) {
        // ✅ Ensure channel exists (sound + importance configured here)
        NotificationChannels.create(this)

        // ✅ Tap notification -> open app normally
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (!open.isNullOrBlank()) putExtra("open", open)
            if (!jobId.isNullOrBlank()) putExtra("jobId", jobId)
        }

        // ✅ Full-screen/banner -> open banner activity
        val bannerIntent = Intent(this, IncomingJobBannerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("title", title)
            putExtra("body", body)
            putExtra("open", open ?: "job_requests")
            if (!jobId.isNullOrBlank()) putExtra("jobId", jobId)
        }

        val requestCode = jobId?.hashCode() ?: Random.nextInt()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val contentIntent = PendingIntent.getActivity(
            this,
            requestCode,
            openAppIntent,
            flags
        )

        val fullScreenIntent = PendingIntent.getActivity(
            this,
            requestCode + 1,
            bannerIntent,
            flags
        )

        val notifId = jobId?.hashCode() ?: Random.nextInt()

        val builder = NotificationCompat.Builder(this, NotificationChannels.PROVIDER_JOBS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 250, 150, 250, 150, 400))
            .setFullScreenIntent(fullScreenIntent, true)

        try {
            NotificationManagerCompat.from(this).notify(notifId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Notification permission missing", e)
        }

        Log.d(TAG, "✅ NOTIF posted. Banner intent attached. title=$title jobId=$jobId notifId=$notifId")
    }
}