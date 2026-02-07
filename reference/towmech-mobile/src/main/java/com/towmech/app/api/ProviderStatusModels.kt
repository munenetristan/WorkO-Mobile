package com.towmech.app.api

// ✅ Request body when provider toggles Online/Offline
// ✅ Optional lat/lng allows sending location when available
data class ProviderStatusRequest(
    val isOnline: Boolean,
    val lat: Double? = null,
    val lng: Double? = null
)

// ✅ Response from backend after updating status
data class ProviderStatusResponse(
    val message: String? = null,
    val isOnline: Boolean? = false,
    val lat: Double? = null,
    val lng: Double? = null
)