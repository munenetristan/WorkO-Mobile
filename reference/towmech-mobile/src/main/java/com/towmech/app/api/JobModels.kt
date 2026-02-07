// app/src/main/java/com/towmech/app/api/JobModels.kt
package com.towmech.app.api

import com.google.gson.*
import java.lang.reflect.Type

// =======================================================
// ✅ NOTE:
// ConfigResponse + ConfigCountry + ServiceFlags + PaymentsFlags
// + CountryUiConfig MUST EXIST ONLY ONCE in the package.
// Your project already has them inside Models.kt,
// so they are REMOVED from this JobModels.kt to FIX redeclaration.
// =======================================================

// =========================
// ✅ INSURANCE MODELS (NEW)
// =========================
data class InsurancePayload(
    val enabled: Boolean = false,
    val code: String? = null,
    val partnerId: String? = null
)

data class InsuranceInfo(
    val enabled: Boolean? = null,
    val code: String? = null,
    val partnerId: String? = null,
    val validatedAt: String? = null
)

// =========================
// ✅ JOB PREVIEW REQUEST MODEL ✅
// =========================
data class JobPreviewRequest(
    val title: String,

    // ✅ keep legacy description for tow truck / general
    val description: String?,

    val roleNeeded: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val pickupAddressText: String?,

    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,
    val dropoffAddressText: String? = null,

    // TowTruck
    val towTruckTypeNeeded: String? = null,
    val vehicleType: String? = null,

    // ✅ Mechanic (NEW)
    // Some backends use mechanicCategoryNeeded, others use mechanicCategory
    val mechanicCategoryNeeded: String? = null,
    val mechanicCategory: String? = null,

    val customerProblemDescription: String? = null,

    // ✅ Insurance (NEW)
    val insurance: InsurancePayload? = null
)

// =========================
// ✅ CREATE JOB MODELS
// =========================
data class CreateJobRequest(
    val title: String,

    // ✅ keep legacy description for tow truck / general
    val description: String?,

    val roleNeeded: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val pickupAddressText: String?,

    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,
    val dropoffAddressText: String? = null,

    // TowTruck
    val towTruckTypeNeeded: String? = null,
    val vehicleType: String? = null,

    // ✅ Mechanic (NEW)
    // Send both keys safely (backend may validate one of them)
    val mechanicCategoryNeeded: String? = null,
    val mechanicCategory: String? = null,

    val customerProblemDescription: String? = null,

    // ✅ Insurance (NEW)
    val insurance: InsurancePayload? = null
)

data class CreateJobResponse(
    val message: String?,
    val job: JobResponse?,
    val payment: Payment?
)

// =========================
// ✅ JOB LIST MODELS
// =========================
data class JobListResponse(
    val jobs: List<JobResponse>?
)

// =========================
// ✅ JOB RESPONSE MODEL (Used everywhere)
// =========================
data class JobResponse(
    val _id: String = "",

    // fallback fields
    val id: String? = null,
    val jobId: String? = null,

    val title: String? = null,

    // legacy/general description
    val description: String? = null,

    val roleNeeded: String? = null,
    val status: String? = null,

    val pickupAddressText: String? = null,
    val dropoffAddressText: String? = null,

    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,

    // ✅ Mechanic-specific fields (NEW)
    val mechanicCategoryNeeded: String? = null,
    val mechanicCategory: String? = null,
    val customerProblemDescription: String? = null,

    // ✅ Disclaimers (NEW; safe defaults prevent crash)
    val disclaimers: JobDisclaimers? = null,

    // ✅ Insurance (NEW)
    val insurance: InsuranceInfo? = null,

    val assignedTo: FlexibleUser? = null,
    val customer: FlexibleUser? = null,

    val pricing: JobPricing? = null,

    // ✅ IMPORTANT TIMESTAMPS
    val lockedAt: String? = null,
    val assignedAt: String? = null,
    val acceptedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class JobDisclaimers(
    val mechanicFinalFeeNotPredetermined: Boolean? = null,
    val text: String? = null
)

// =========================
// ✅ LOCATION HELPER MODEL
// =========================
data class GeoPoint(
    val lat: Double? = null,
    val lng: Double? = null
)

// =========================
// ✅ FLEXIBLE USER MODEL ✅
// Accepts String OR Object
// NOW ALSO HOLDS LIVE LOCATION (optional)
// =========================
data class FlexibleUser(
    val _id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,

    // ✅ NEW (optional): provider live location
    val lat: Double? = null,
    val lng: Double? = null,
    val location: GeoPoint? = null
)

class FlexibleUserAdapter : JsonDeserializer<FlexibleUser> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): FlexibleUser {
        return when {
            json == null || json.isJsonNull -> FlexibleUser()

            // If backend sometimes returns just an ID string:
            json.isJsonPrimitive -> FlexibleUser(_id = json.asString)

            json.isJsonObject -> {
                val obj = json.asJsonObject

                val directLat = obj.get("lat")?.takeIf { it.isJsonPrimitive }?.asDouble
                val directLng = obj.get("lng")?.takeIf { it.isJsonPrimitive }?.asDouble

                val locObj = obj.get("location")?.takeIf { it.isJsonObject }?.asJsonObject
                val locLat = locObj?.get("lat")?.takeIf { it.isJsonPrimitive }?.asDouble
                val locLng = locObj?.get("lng")?.takeIf { it.isJsonPrimitive }?.asDouble

                val coordsArr = locObj?.get("coordinates")?.takeIf { it.isJsonArray }?.asJsonArray
                val geoLng = coordsArr?.getOrNull(0)?.takeIf { it.isJsonPrimitive }?.asDouble
                val geoLat = coordsArr?.getOrNull(1)?.takeIf { it.isJsonPrimitive }?.asDouble

                val finalLat = directLat ?: locLat ?: geoLat
                val finalLng = directLng ?: locLng ?: geoLng

                FlexibleUser(
                    _id = obj.get("_id")?.asString,
                    name = obj.get("name")?.asString,
                    email = obj.get("email")?.asString,
                    role = obj.get("role")?.asString,
                    lat = finalLat,
                    lng = finalLng,
                    location = if (finalLat != null && finalLng != null) GeoPoint(finalLat, finalLng) else null
                )
            }

            else -> FlexibleUser()
        }
    }

    private fun JsonArray.getOrNull(index: Int): JsonElement? {
        return if (index in 0 until size()) get(index) else null
    }
}

// =========================
// ✅ PRICING MODEL
// =========================
data class JobPricing(
    val currency: String? = "ZAR",
    val baseFee: Double? = 0.0,
    val perKmFee: Double? = 0.0,

    val estimatedDistanceKm: Double? = 0.0,
    val estimatedTotal: Double? = 0.0,

    val bookingFee: Double? = 0.0,
    val bookingFeeStatus: String? = null,

    val providerAmountDue: Double? = 0.0,
    val commissionAmount: Double? = 0.0
)

// =========================
// ✅ PAYMENT MODELS
// =========================
data class Payment(
    val _id: String?,
    val job: String?,
    val customer: String?,
    val amount: Double?,
    val currency: String?,
    val status: String?,

    val provider: String? = null,
    val providerReference: String? = null,
    val providerPayload: Any? = null,

    val paidAt: String? = null,
    val refundedAt: String? = null,
    val refundReference: String? = null
)

data class PaystackData(
    val authorization_url: String? = null,
    val access_code: String? = null,
    val reference: String? = null
)

data class IKhokhaData(
    val paymentUrl: String? = null,
    val redirectUrl: String? = null,
    val reference: String? = null,
    val status: String? = null
)

data class PayFastData(
    val paymentUrl: String? = null,
    val reference: String? = null,
    val gateway: String? = null,
    val signature: String? = null
)

data class InitResponse(
    val paymentUrl: String? = null,
    val reference: String? = null,
    val gateway: String? = null,
    val signature: String? = null
)

data class PaymentResponse(
    val message: String?,
    val payment: Payment?,
    val gateway: String? = null,
    val initResponse: InitResponse? = null,
    val payfast: PayFastData? = null,
    val paystack: PaystackData? = null,
    val ikhokha: IKhokhaData? = null
)

data class CreatePaymentRequest(
    val jobId: String
)

// =========================
// ✅ ACCEPT / REJECT MODEL
// =========================
data class AcceptRejectResponse(
    val message: String?,
    val success: Boolean? = true,
    val job: JobResponse? = null
)

// =========================
// ✅ RATING MODELS
// =========================
data class RatingRequest(
    val jobId: String,
    val rating: Int,
    val comment: String?
)

data class RatingResponse(
    val message: String?,
    val success: Boolean? = true
)

// =========================
// ✅ STATUS UPDATE MODELS
// =========================
data class UpdateStatusRequest(
    val status: String
)

data class JobStatusUpdateResponse(
    val message: String?,
    val job: JobResponse?
)