package com.towmech.app.api

data class ProviderJobListResponse(
    val jobs: List<JobResponse>? = null,
    val message: String? = null
)