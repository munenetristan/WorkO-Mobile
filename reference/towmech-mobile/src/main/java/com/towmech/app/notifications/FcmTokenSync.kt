package com.towmech.app.notifications

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.towmech.app.api.ApiClient
import com.towmech.app.api.RegisterTokenRequest
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

object FcmTokenSync {

    private const val TAG = "FCM_SYNC"

    private const val PREF = "towmech_fcm_pref"
    private const val KEY_LAST_UPLOADED_TOKEN = "last_uploaded_fcm_token"
    private const val KEY_LAST_UPLOADED_JWT = "last_uploaded_jwt"

    /**
     * ✅ Call on app start to sync token IF logged in
     */
    suspend fun syncIfLoggedIn(context: Context) {
        val jwt = TokenManager.getToken(context)

        if (jwt.isNullOrBlank()) {
            Log.d(TAG, "No JWT saved yet → skip FCM sync")
            return
        }

        syncWithJwt(context, jwt)
    }

    /**
     * ✅ Call immediately after OTP verify (after saving JWT)
     */
    suspend fun syncWithJwt(context: Context, jwt: String) {
        try {
            val fcmToken = FirebaseMessaging.getInstance().token.await()

            if (fcmToken.isBlank()) {
                Log.e(TAG, "FCM token is blank ❌")
                return
            }

            // ✅ Skip re-upload if nothing changed
            val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val lastToken = sp.getString(KEY_LAST_UPLOADED_TOKEN, null)
            val lastJwt = sp.getString(KEY_LAST_UPLOADED_JWT, null)

            if (lastToken == fcmToken && lastJwt == jwt) {
                Log.d(TAG, "FCM already uploaded for this JWT ✅ (skip)")
                return
            }

            Log.d(TAG, "FCM token ready ✅ ${fcmToken.take(25)}... uploading...")

            val res = withContext(Dispatchers.IO) {
                ApiClient.apiService.registerFcmToken(
                    token = "Bearer $jwt",
                    request = RegisterTokenRequest(fcmToken = fcmToken)
                )
            }

            // ✅ Save cache
            sp.edit()
                .putString(KEY_LAST_UPLOADED_TOKEN, fcmToken)
                .putString(KEY_LAST_UPLOADED_JWT, jwt)
                .apply()

            Log.d(TAG, "Backend register-token ✅ message=${res.message} role=${res.role}")

        } catch (e: Exception) {
            Log.e(TAG, "FCM sync failed ❌ ${e.message}", e)
        }
    }
}