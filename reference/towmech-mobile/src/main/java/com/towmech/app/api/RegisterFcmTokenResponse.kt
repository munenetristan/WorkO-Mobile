package com.towmech.app.api

data class RegisterFcmTokenResponse(
    val message: String,
    val role: String? = null,
    val savedInProviderProfile: Boolean? = null
)