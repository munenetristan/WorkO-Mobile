package com.towmech.app.data

import android.content.Context

object ProviderStatusStore {
    private const val PREFS_NAME = "provider_status_prefs"
    private const val KEY_IS_ONLINE = "is_online"

    fun setIsOnline(context: Context, isOnline: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_ONLINE, isOnline).apply()
    }

    fun getIsOnline(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_ONLINE, false)
    }
}