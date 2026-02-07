package com.towmech.app.notifications

import android.content.Context

object FcmTokenStore {
    private const val PREF = "towmech_prefs"
    private const val KEY = "fcm_token"

    fun save(context: Context, token: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, token)
            .apply()
    }

    fun get(context: Context): String? {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }
}