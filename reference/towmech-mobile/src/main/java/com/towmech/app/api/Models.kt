// app/src/main/java/com/towmech/app/api/Models.kt
package com.towmech.app.api

import com.google.gson.annotations.SerializedName

// =========================
// ✅ AUTH REQUESTS/RESPONSES
// =========================

data class RegisterRequest(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val password: String,
    val birthday: String,

    val nationalityType: String,
    val saIdNumber: String? = null,
    val passportNumber: String? = null,
    val country: String? = null,

    val role: String,

    // ✅ TowTruck only
    val towTruckTypes: List<String>? = null,

    // ✅ Mechanic only
    val mechanicCategories: List<String>? = null,

    // ✅ Multi-country (optional fallback; headers are still primary)
    val countryCode: String? = null
)

data class RegisterResponse(
    val message: String?,
    val user: RegisterUser?
)

data class RegisterUser(
    val id: String?,
    val name: String?,
    val email: String?,
    val role: String?
)

data class LoginRequest(
    val phone: String,
    val password: String,

    // ✅ Multi-country (optional fallback; headers are still primary)
    val countryCode: String? = null
)

data class LoginResponse(
    val message: String?,
    val otp: String?
)

data class VerifyOtpRequest(
    val phone: String,
    val otp: String
)

data class VerifyOtpResponse(
    val message: String?,
    val token: String?,
    val user: UserSafe?
)

// =========================
// ✅ COUNTRY OTP (NO TOKEN)
// =========================

data class SendCountryOtpRequest(
    val phone: String,
    val countryCode: String,
    val language: String
)

data class VerifyCountryOtpRequest(
    val phone: String,
    val otp: String,
    val countryCode: String? = null // optional (safe if backend ignores it)
)

// =========================
// ✅ PASSWORD RESET
// =========================

data class ForgotPasswordRequest(val phone: String)
data class ForgotPasswordResponse(val message: String?, val otp: String?)

data class ResetPasswordRequest(
    val phone: String,
    val otp: String,
    val newPassword: String
)

data class ResetPasswordResponse(val message: String?)

// =========================
// ✅ PROFILE
// =========================

data class ProfileResponse(
    val user: UserSafe?
)

// =========================
// ✅ CUSTOMER PROFILE
// =========================

data class CustomerMeResponse(
    val user: UserSafe?
)

/**
 * ✅ SAFE ALIAS:
 * Both /api/auth/me and /api/customers/me return the SAME payload shape: { user: ... }
 */
typealias MeResponse = ProfileResponse

data class UserSafe(
    val _id: String? = null,
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val providerProfile: ProviderProfile? = null
)

data class ProviderProfile(
    val isOnline: Boolean? = null,
    val verificationStatus: String? = null,
    val verificationDocs: VerificationDocs? = null,

    // ✅ Optional: if backend returns these later, Android won't crash
    val towTruckTypes: List<String>? = null,
    val mechanicCategories: List<String>? = null
)

data class VerificationDocs(
    val idDocumentUrl: String? = null,
    val licenseUrl: String? = null,
    val vehicleProofUrl: String? = null,
    val workshopProofUrl: String? = null
)

/**
 * ✅ Wrapper for GET /api/jobs/:id
 * Backend returns: { job: {...} }
 */
data class JobSingleResponse(
    val job: JobResponse? = null
)

// =====================================================
// ✅ CONFIG MODELS (SOURCE OF TRUTH = DASHBOARD)
// GET /api/config/all -> { country, services, ui, pricing, serverTime }
// =====================================================

/**
 * ✅ COMPATIBILITY SAFE FIX:
 * Some older screens expect:
 * - res.vehicleTypes
 * - res.towTruckTypes
 * - res.mechanicCategories
 * - res.success
 *
 * Your dashboard-first config now returns { country, services, ui, pricing, serverTime }.
 * We keep dashboard structure as primary, but add legacy fields (nullable / default empty)
 * so NO existing screens break.
 */
data class ConfigResponse(
    // ✅ NEW dashboard structure (source of truth)
    val country: ConfigCountry? = null,
    val services: CountryServicesConfig? = null,
    val ui: CountryUiConfig? = null,
    val pricing: PricingConfigDto? = null,
    val serverTime: String? = null,

    // ✅ LEGACY / COMPAT FIELDS (do not remove; older screens use them)
    val success: Boolean? = null,
    val vehicleTypes: List<String> = emptyList(),
    val towTruckTypes: List<String> = emptyList(),
    val mechanicCategories: List<String> = emptyList(),

    // ✅ OPTIONAL: some older backends used a different pricing object name/shape
    // Keeping it nullable prevents crashes if present/absent.
    val pricingLegacy: Any? = null
)

data class ConfigCountry(
    val code: String? = null,
    val name: String? = null,
    val currency: String? = null,
    val dialingCode: String? = null,
    val defaultLanguage: String? = null,
    val supportedLanguages: List<String>? = null,
    val isActive: Boolean? = null
)

/**
 * This mirrors backend "resolvedServiceConfig" shape in config.routes.js
 * We only NEED the booleans, but we keep other fields nullable for forward-compat.
 *
 * ✅ PATCH ADDED:
 * Supports BOTH shapes:
 * 1) nested: services: { services: { towingEnabled: ... } }
 * 2) flat:   services: { towingEnabled: ... }
 * 3) legacy: services: { towing: ... }
 */
data class CountryServicesConfig(
    val countryCode: String? = null,
    val enabled: Boolean? = true,

    // ✅ Current (nested) shape: services: { services: { towingEnabled: ... } }
    val services: ServiceFlags? = null,

    // optional blocks (backend may return them)
    val payments: PaymentsFlags? = null,

    // ✅ Compatibility (flat) shape: services: { towingEnabled: ..., mechanicEnabled: ... }
    @SerializedName("towingEnabled")
    val towingEnabled: Boolean? = null,

    @SerializedName("mechanicEnabled")
    val mechanicEnabled: Boolean? = null,

    @SerializedName("emergencySupportEnabled")
    val emergencySupportEnabled: Boolean? = null,

    // legacy alias sometimes used
    @SerializedName("supportEnabled")
    val supportEnabled: Boolean? = null,

    @SerializedName("insuranceEnabled")
    val insuranceEnabled: Boolean? = null,

    @SerializedName("chatEnabled")
    val chatEnabled: Boolean? = null,

    @SerializedName("ratingsEnabled")
    val ratingsEnabled: Boolean? = null,

    // ✅ Even more legacy (short keys) just in case
    @SerializedName("towing")
    val towing: Boolean? = null,

    @SerializedName("mechanic")
    val mechanic: Boolean? = null
)

/**
 * ✅ Dashboard toggles live here.
 * If you add more services later, just add new boolean fields in backend + dashboard;
 * Android will safely default unknown ones (not crash), and you can add fields here anytime.
 */
data class ServiceFlags(
    val towingEnabled: Boolean? = true,
    val mechanicEnabled: Boolean? = true,
    val winchRecoveryEnabled: Boolean? = false,
    val roadsideAssistanceEnabled: Boolean? = false,
    val jumpStartEnabled: Boolean? = false,
    val tyreChangeEnabled: Boolean? = false,
    val fuelDeliveryEnabled: Boolean? = false,
    val lockoutEnabled: Boolean? = false
)

data class PaymentsFlags(
    val paystackEnabled: Boolean? = false,
    val ikhokhaEnabled: Boolean? = false,
    val payfastEnabled: Boolean? = false,
    val mpesaEnabled: Boolean? = false,
    val flutterwaveEnabled: Boolean? = false,
    val stripeEnabled: Boolean? = false,

    val bookingFeeRequired: Boolean? = true,
    val bookingFeePercent: Double? = 0.0,
    val bookingFeeFlat: Double? = 0.0,

    val defaultProvider: String? = null,
    val providers: Map<String, Any>? = null
)

data class CountryUiConfig(
    val countryCode: String? = null,
    val appName: String? = null,
    val primaryColor: String? = null,
    val accentColor: String? = null,
    val mapBackgroundKey: String? = null,
    val heroImageKey: String? = null,
    val enabled: Boolean? = true
)

/**
 * Minimal pricing model (you already use config.pricing?.currency etc in screens)
 * Keep fields nullable to avoid crashes if backend adds/removes keys.
 */
data class PricingConfigDto(
    val currency: String? = null,
    val bookingFees: BookingFeesDto? = null,
    val mechanicCategories: List<String>? = null,
    val mechanicCategoryPricing: Map<String, MechanicCategoryPricing>? = null
)

data class BookingFeesDto(
    val mechanicFixed: Double? = null,
    val towTruckFixed: Double? = null
)

/**
 * ✅ FIX: This class was referenced but missing, causing:
 * "Unresolved reference: MechanicCategoryPricing"
 *
 * ✅ Safe: does NOT change any existing business logic.
 * ✅ Only ensures config parsing compiles.
 */
data class MechanicCategoryPricing(
    val baseFee: Double? = 0.0,
    val nightFee: Double? = 0.0,
    val weekendFee: Double? = 0.0
)

// NOTE:
// The rest of your existing models (JobResponse, JobPreviewResponse, CreateJobRequest, etc.)
// remain unchanged below in your file.
// If you want me to patch RequestServiceScreen.kt to actually HIDE the buttons based on:
//   config.services?.services?.towingEnabled / mechanicEnabled
// share RequestServiceScreen.kt + wherever you load config into app state (usually ViewModel / TokenManager / Splash).