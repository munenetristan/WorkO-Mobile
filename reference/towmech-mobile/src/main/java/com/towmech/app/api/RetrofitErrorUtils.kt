// app/src/main/java/com/towmech/app/api/RetrofitErrorUtils.kt
package com.towmech.app.api

import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException

/**
 * Friendly backend error messages using HttpException.readErrorMessage()
 * - Safe: never crashes if backend returns non-JSON
 */
fun HttpException.readErrorMessage(): String? {
    return try {
        val raw = response()?.errorBody()?.string()?.trim().orEmpty()
        if (raw.isBlank()) return null

        // Common shapes:
        // { "message": "..." }
        // { "error": "..." }
        // { "msg": "..." }
        // { "errors": [ { "message": "..." } ] }
        val json = JSONObject(raw)
        when {
            json.has("message") -> json.optString("message")
            json.has("error") -> json.optString("error")
            json.has("msg") -> json.optString("msg")
            json.has("errors") -> {
                val arr = json.optJSONArray("errors")
                if (arr != null && arr.length() > 0) {
                    val first = arr.optJSONObject(0)
                    first?.optString("message") ?: first?.optString("msg")
                } else null
            }
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

/** Optional: fallback message by status code */
fun friendlyHttpFallback(code: Int): String {
    return when (code) {
        400 -> "Bad request. Please check your input."
        401 -> "Unauthorized. Please log in again."
        403 -> "Access denied."
        404 -> "Not found."
        409 -> "Conflict. This record may already exist."
        422 -> "Validation failed. Please check your input."
        500 -> "Server error. Please try again later."
        else -> "Request failed. Please try again."
    }
}