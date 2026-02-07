package com.towmech.app.api

data class RegisterTokenRequest(
    val fcmToken: String
)

data class RegisterTokenResponse(
    val message: String?,
    val role: String? = null,
    val savedInProviderProfile: Boolean? = null
)