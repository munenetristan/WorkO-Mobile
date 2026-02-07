// app/src/main/java/com/towmech/app/data/CountryPrefs.kt
package com.towmech.app.data

import android.content.Context

object CountryPrefs {

    private const val PREF_NAME = "towmech_country_prefs"
    private const val KEY_COUNTRY_CODE = "country_code"
    private const val KEY_DIAL_CODE = "dial_code"
    private const val KEY_LANGUAGE = "language_code"
    private const val KEY_PHONE = "last_phone"

    fun saveCountry(context: Context, countryCode: String, dialCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_COUNTRY_CODE, countryCode.trim().uppercase())
            .putString(KEY_DIAL_CODE, dialCode.trim())
            .apply()
    }

    fun saveLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LANGUAGE, language.trim().lowercase())
            .apply()
    }

    fun savePhone(context: Context, phone: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PHONE, phone.trim())
            .apply()
    }

    fun getCountryCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_COUNTRY_CODE, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun getDialCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DIAL_CODE, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun getLanguage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "en")?.trim()?.takeIf { it.isNotBlank() }
    }

    fun getLastPhone(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PHONE, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}