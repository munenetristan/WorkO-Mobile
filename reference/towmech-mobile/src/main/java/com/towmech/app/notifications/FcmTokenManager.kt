package com.towmech.app.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {

    fun fetchAndStore(context: Context, onDone: (String?) -> Unit = {}) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("FCM", "❌ Fetch token failed", task.exception)
                    onDone(null)
                    return@addOnCompleteListener
                }

                val token = task.result
                Log.d("FCM", "✅ Fetched FCM token: $token")
                if (!token.isNullOrBlank()) {
                    FcmTokenStore.save(context, token)
                }
                onDone(token)
            }
    }
}