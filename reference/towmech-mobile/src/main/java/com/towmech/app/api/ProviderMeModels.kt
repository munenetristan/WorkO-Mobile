package com.towmech.app.api

data class ProviderMeResponse(
    val user: ProviderUser? = null
)

data class ProviderUser(
    val _id: String? = null,

    // Common identity
    val name: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,

    // Account
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,

    // Registration details (missing before)
    val birthday: String? = null,          // backend may return ISO string
    val nationalityType: String? = null,   // "SouthAfrican" | "ForeignNational"
    val country: String? = null,
    val saIdNumber: String? = null,
    val passportNumber: String? = null,

    // Provider profile
    val providerProfile: ProviderProfile? = null
)

/**
 * ✅ Provider update payload (PATCH /api/providers/me)
 * Keep email/phone for backwards compatibility.
 * Add towTruckTypes / mechanicCategories for provider skills updates.
 */
data class UpdateProviderMeRequest(
    val email: String? = null,
    val phone: String? = null,
    val towTruckTypes: List<String>? = null,
    val mechanicCategories: List<String>? = null,
    val carTypesSupported: List<String>? = null
)