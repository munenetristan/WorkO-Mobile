// app/src/main/java/com/towmech/app/data/TokenManager.kt
package com.towmech.app.data

import android.content.Context

object TokenManager {

    private const val PREF_NAME = "towmech_prefs"

    private const val KEY_TOKEN = "jwt_token"

    // ✅ Multi-country
    private const val KEY_COUNTRY_CODE = "country_code"
    private const val KEY_DIAL_CODE = "dial_code"
    private const val KEY_LANGUAGE_CODE = "language_code"
    private const val KEY_LAST_PHONE = "last_phone_digits"

    // ✅ NEW: store selected country name (for greyed-out display on registration screens)
    private const val KEY_COUNTRY_NAME = "country_name"

    // ---------------- TOKEN ----------------

    fun saveToken(context: Context, token: String) {
        val cleaned = token.trim()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (cleaned.isBlank()) {
            prefs.edit().remove(KEY_TOKEN).apply()
            return
        }

        prefs.edit().putString(KEY_TOKEN, cleaned).apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)?.trim()
        return if (token.isNullOrBlank()) null else token
    }

    fun clearToken(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    // ---------------- COUNTRY ----------------

    // ✅ Backwards-compatible overload (keeps existing calls working)
    fun saveCountry(context: Context, countryCode: String, dialCode: String, languageCode: String) {
        saveCountry(context, countryCode, dialCode, languageCode, countryName = "")
    }

    // ✅ NEW: allows saving country name too (used by CountryStartScreen once you update it)
    fun saveCountry(
        context: Context,
        countryCode: String,
        dialCode: String,
        languageCode: String,
        countryName: String
    ) {
        val cc = countryCode.trim().uppercase()
        val dial = dialCode.trim()
        val lang = languageCode.trim().lowercase()
        val name = countryName.trim()

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_COUNTRY_CODE, cc)
            .putString(KEY_DIAL_CODE, dial)
            .putString(KEY_LANGUAGE_CODE, lang)
            .putString(KEY_COUNTRY_NAME, name)
            .apply()
    }

    fun getCountryCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_COUNTRY_CODE, "ZA")?.trim()?.uppercase() ?: "ZA"
    }

    fun getDialCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DIAL_CODE, "+27")?.trim() ?: "+27"
    }

    fun getLanguageCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val v = prefs.getString(KEY_LANGUAGE_CODE, null)?.trim()
        return if (v.isNullOrBlank()) null else v.lowercase()
    }

    // ✅ NEW: used to show "🇺🇬 Uganda (UG)" on registration (greyed-out)
    fun getCountryName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val v = prefs.getString(KEY_COUNTRY_NAME, null)?.trim().orEmpty()
        return v.takeIf { it.isNotBlank() }
    }

    fun clearCountry(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_COUNTRY_CODE)
            .remove(KEY_DIAL_CODE)
            .remove(KEY_LANGUAGE_CODE)
            .remove(KEY_COUNTRY_NAME)
            .remove(KEY_LAST_PHONE)
            .apply()
    }

    // ---------------- PHONE (COUNTRY FLOW) ----------------

    fun saveLastPhoneDigits(context: Context, digits: String) {
        val clean = digits.filter { it.isDigit() }.take(15)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_PHONE, clean).apply()
    }

    fun getLastPhoneDigits(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val v = prefs.getString(KEY_LAST_PHONE, null)?.trim().orEmpty()
        return v.takeIf { it.isNotBlank() }
    }
}