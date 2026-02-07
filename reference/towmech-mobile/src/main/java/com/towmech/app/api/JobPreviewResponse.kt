// JobPreviewResponse.kt
package com.towmech.app.api

data class JobPreviewResponse(
    val providersFound: Boolean,
    val providerCount: Int,
    val message: String,
    val preview: PreviewPricing? = null
)

data class PreviewPricing(
    val currency: String = "ZAR",
    val distanceKm: Double = 0.0,

    // ✅ TowTruck preview returns ALL tow truck types here
    val resultsByTowTruckType: Map<String, TowTruckPricing>? = null,

    // ✅ Mechanic preview: bookingFee shown, estimatedTotal should be 0 / null
    val bookingFee: Double? = null,
    val estimatedTotal: Double? = null,

    // ✅ NEW: backend may also return disclaimer + mechanicCategory used
    val mechanicCategory: String? = null,
    val disclaimers: PreviewDisclaimers? = null
)

data class PreviewDisclaimers(
    val mechanicFinalFeeNotPredetermined: Boolean? = null,
    val text: String? = null
)

data class TowTruckPricing(
    val estimatedTotal: Int = 0,
    val bookingFee: Int = 0,
    val currency: String = "ZAR",

    // ✅ backend sends this for route distance
    val estimatedDistanceKm: Double? = null,

    // ✅ OPTIONAL: if backend sends multipliers (safe to include)
    val towTruckTypeMultiplier: Double? = null,
    val vehicleTypeMultiplier: Double? = null,

    // ✅ backend provider counts
    val providersCount: Int = 0,
    val status: String = "OFFLINE"
)