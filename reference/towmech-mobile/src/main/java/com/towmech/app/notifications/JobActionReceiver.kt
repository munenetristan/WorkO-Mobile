package com.towmech.app.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.towmech.app.MainActivity
import com.towmech.app.api.ApiClient
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JobActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "JOB_ACTION"

        const val ACTION_ACCEPT = "com.towmech.app.ACTION_ACCEPT_JOB"
        const val ACTION_REJECT = "com.towmech.app.ACTION_REJECT_JOB"

        const val EXTRA_JOB_ID = "jobId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }

    override fun onReceive(context: Context, intent: Intent) {

        val jobId = intent.getStringExtra(EXTRA_JOB_ID)
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (jobId.isNullOrBlank() || notifId == -1) {
            Log.w(TAG, "Missing jobId/notifId")
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {

            ACTION_REJECT -> {
                // ✅ Cancel ONLY on this provider phone
                notificationManager.cancel(notifId)

                // ✅ Reject on backend (so it won’t be available to this provider again)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val token = TokenManager.getToken(context)
                        if (token.isNullOrBlank()) return@launch

                        ApiClient.apiService.rejectJob(
                            token = "Bearer $token",
                            jobId = jobId
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Reject failed", e)
                    }
                }
            }

            ACTION_ACCEPT -> {
                // ✅ Accept on backend
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val token = TokenManager.getToken(context)
                        if (token.isNullOrBlank()) return@launch

                        ApiClient.apiService.acceptJob(
                            token = "Bearer $token",
                            jobId = jobId
                        )

                        // ✅ Cancel notification after accept
                        notificationManager.cancel(notifId)

                        // ✅ Open app straight to the job (tracking/request)
                        val openApp = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("open", "job_requests")
                            putExtra("jobId", jobId)
                        }

                        context.startActivity(openApp)

                    } catch (e: Exception) {
                        Log.e(TAG, "Accept failed", e)

                        // If job was already taken, cancel and show friendly note
                        notificationManager.cancel(notifId)

                        // Toast must run on main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                context,
                                "Job not available for you",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}