// app/src/main/java/com/towmech/app/api/ApiService.kt
package com.towmech.app.api

import com.towmech.app.data.ChatMessagesResponse
import com.towmech.app.data.ChatSendRequest
import com.towmech.app.data.ChatSendResponse
import com.towmech.app.data.ChatThreadResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // =========================
    // ✅ AUTH ROUTES
    // =========================
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): VerifyOtpResponse

    // ✅✅✅ COUNTRY OTP (NO TOKEN) ✅ NEW
    @POST("api/auth/country/send-otp")
    suspend fun sendCountryOtp(@Body request: SendCountryOtpRequest): GenericMessageResponse

    @POST("api/auth/country/verify-otp")
    suspend fun verifyCountryOtp(@Body request: VerifyCountryOtpRequest): GenericMessageResponse

    // ✅✅✅ PUBLIC: CHECK IF PHONE EXISTS (NO TOKEN) ✅ NEW
    @POST("api/auth/check-phone")
    suspend fun checkPhoneExists(@Body request: CheckPhoneExistsRequest): CheckPhoneExistsResponse

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): GenericMessageResponse

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): GenericMessageResponse

    @GET("api/auth/me")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): ProfileResponse

    @PATCH("api/auth/me")
    suspend fun updateMyProfile(
        @Header("Authorization") token: String,
        @Body request: Map<String, String>
    ): ProfileResponse

    // =========================
    // ✅ CUSTOMER PROFILE ROUTE (OPTIONAL BACKUP)
    // =========================
    @GET("api/customers/me")
    suspend fun getCustomerMe(
        @Header("Authorization") token: String
    ): CustomerMeResponse

    // =========================
    // ✅ CONFIG ROUTES
    // =========================
    @GET("api/config/all")
    suspend fun getAppConfig(): ConfigResponse

    // =========================
    // ✅ COUNTRIES (PUBLIC) ✅ NEW
    // =========================
    @GET("api/countries")
    suspend fun getCountries(): CountriesResponse

    // =========================
    // ✅ NOTIFICATIONS (FCM TOKEN)
    // =========================
    @POST("api/notifications/register-token")
    suspend fun registerFcmToken(
        @Header("Authorization") token: String,
        @Body request: RegisterTokenRequest
    ): RegisterFcmTokenResponse

    // =========================
    // ✅ CUSTOMER JOB ROUTES
    // =========================
    @POST("api/jobs/preview")
    suspend fun previewJob(
        @Header("Authorization") token: String,
        @Body request: JobPreviewRequest
    ): JobPreviewResponse

    @POST("api/jobs")
    suspend fun createJob(
        @Header("Authorization") token: String,
        @Body request: CreateJobRequest
    ): CreateJobResponse

    @GET("api/jobs/my/active")
    suspend fun getCustomerActiveJobs(
        @Header("Authorization") token: String
    ): JobListResponse

    @GET("api/jobs/my/history")
    suspend fun getCustomerJobHistory(
        @Header("Authorization") token: String
    ): JobListResponse

    @GET("api/jobs/{jobId}")
    suspend fun getJobById(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): JobSingleResponse

    // =========================
    // ✅ CUSTOMER CANCEL + DRAFT DELETE (NEW)
    // =========================
    @PATCH("api/jobs/{jobId}/cancel")
    suspend fun cancelJobCustomer(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String,
        @Body request: Map<String, String> = emptyMap()
    ): GenericMessageResponse

    @DELETE("api/jobs/{jobId}/draft")
    suspend fun deleteDraftJob(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): GenericMessageResponse

    // =========================
    // ✅ PAYMENT ROUTES
    // =========================
    @POST("api/payments/create")
    suspend fun createBookingPayment(
        @Header("Authorization") token: String,
        @Body request: CreatePaymentRequest
    ): PaymentResponse

    @GET("api/payments/verify/paystack/{reference}")
    suspend fun verifyPaystackPayment(
        @Header("Authorization") token: String,
        @Path("reference") reference: String
    ): PaymentResponse

    @GET("api/payments/verify/ikhokha/{reference}")
    suspend fun verifyIKhokhaPayment(
        @Header("Authorization") token: String,
        @Path("reference") reference: String
    ): PaymentResponse

    @PATCH("api/payments/{paymentId}/mark-paid")
    suspend fun markPaymentPaid(
        @Header("Authorization") token: String,
        @Path("paymentId") paymentId: String
    ): PaymentResponse

    @PATCH("api/payments/job/{jobId}/mark-paid")
    suspend fun markPaymentPaidByJob(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): PaymentResponse

    // =========================
    // ✅ PROVIDER ROUTES
    // =========================
    @GET("api/providers/me")
    suspend fun getProviderMe(
        @Header("Authorization") token: String
    ): ProviderMeResponse

    @PATCH("api/providers/me")
    suspend fun updateProviderMe(
        @Header("Authorization") token: String,
        @Body request: UpdateProviderMeRequest
    ): ProviderMeResponse

    @Multipart
    @PATCH("api/providers/me/documents")
    suspend fun uploadProviderDocuments(
        @Header("Authorization") token: String,
        @Part idDocumentUrl: MultipartBody.Part? = null,
        @Part licenseUrl: MultipartBody.Part? = null,
        @Part vehicleProofUrl: MultipartBody.Part? = null,
        @Part workshopProofUrl: MultipartBody.Part? = null
    ): ProviderMeResponse

    @PATCH("api/providers/location")
    suspend fun updateProviderLocation(
        @Header("Authorization") token: String,
        @Body request: ProviderLocationRequest
    ): GenericMessageResponse

    @GET("api/providers/jobs/broadcasted")
    suspend fun getBroadcastedJobs(
        @Header("Authorization") token: String
    ): ProviderJobListResponse

    @GET("api/providers/jobs/broadcasted")
    suspend fun getAvailableJobsForProvider(
        @Header("Authorization") token: String
    ): ProviderJobListResponse

    @GET("api/providers/jobs/broadcasted/{jobId}")
    suspend fun getBroadcastedJobById(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): JobResponse

    @GET("api/providers/jobs/{jobId}")
    suspend fun getProviderJobById(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): JobResponse

    @PATCH("api/providers/jobs/{jobId}/accept")
    suspend fun acceptJob(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): AcceptRejectResponse

    @PATCH("api/providers/jobs/{jobId}/reject")
    suspend fun rejectJob(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): AcceptRejectResponse

    @PATCH("api/providers/jobs/{jobId}/cancel")
    suspend fun cancelJob(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String,
        @Body request: Map<String, String> = emptyMap()
    ): AcceptRejectResponse

    @GET("api/providers/jobs/assigned")
    suspend fun getProviderActiveJobs(
        @Header("Authorization") token: String
    ): ProviderJobListResponse

    @GET("api/providers/jobs/history")
    suspend fun getProviderJobHistory(
        @Header("Authorization") token: String
    ): ProviderJobListResponse

    @PATCH("api/providers/me/status")
    suspend fun updateProviderStatus(
        @Header("Authorization") token: String,
        @Body request: ProviderStatusRequest
    ): ProviderStatusResponse

    // =========================
    // ✅ RATINGS ROUTE
    // =========================
    @POST("api/jobs/rate")
    suspend fun submitRating(
        @Header("Authorization") token: String,
        @Body request: RatingRequest
    ): RatingResponse

    // =========================
    // ✅ UPDATE JOB STATUS
    // =========================
    @PATCH("api/jobs/{id}/status")
    suspend fun updateJobStatus(
        @Header("Authorization") token: String,
        @Path("id") jobId: String,
        @Body request: UpdateStatusRequest
    ): JobStatusUpdateResponse

    // =========================
    // ✅✅✅ CHAT ROUTES
    // =========================
    @GET("api/chat/thread/{jobId}")
    suspend fun getChatThread(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): ChatThreadResponse

    @GET("api/chat/messages/{jobId}")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): ChatMessagesResponse

    @POST("api/chat/send/{jobId}")
    suspend fun sendChatMessage(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String,
        @Body request: ChatSendRequest
    ): ChatSendResponse

    // =========================
    // ✅✅✅ SUPPORT ROUTES
    // =========================
    @GET("api/support/tickets")
    suspend fun getSupportTickets(
        @Header("Authorization") token: String
    ): SupportTicketsResponse

    @POST("api/support/tickets")
    suspend fun createSupportTicket(
        @Header("Authorization") token: String,
        @Body request: CreateSupportTicketRequest
    ): CreateSupportTicketResponse

    @GET("api/support/tickets/{id}")
    suspend fun getSupportTicketById(
        @Header("Authorization") token: String,
        @Path("id") ticketId: String
    ): SupportTicketResponse

    @POST("api/support/tickets/{id}/reply")
    suspend fun replyToSupportTicket(
        @Header("Authorization") token: String,
        @Path("id") ticketId: String,
        @Body request: SupportReplyRequest
    ): SupportTicketResponse

    // =========================
    // ✅✅✅ INSURANCE (PUBLIC) — REQUIRED TO REJECT INVALID CODES
    // =========================
    @GET("api/insurance/partners")
    suspend fun getInsurancePartners(
        // some backends use countryCode, some use country
        @Query("countryCode") countryCode: String? = null,
        @Query("country") country: String? = null,

        // some backends rely only on header
        @Header("X-COUNTRY-CODE") xCountryCode: String? = null
    ): InsurancePartnersResponse

    @POST("api/insurance/validate-code")
    suspend fun validateInsuranceCode(
        @Header("X-COUNTRY-CODE") xCountryCode: String? = null,
        @Body request: ValidateInsuranceCodeRequest
    ): ValidateInsuranceCodeResponse
}

// ✅ Keep this in the same file (safe + easy)
data class ProviderLocationRequest(
    val lat: Double,
    val lng: Double
)

// ✅ Models for /api/countries
data class CountriesResponse(
    val countries: List<CountryDto>? = emptyList()
)

data class CountryDto(
    val _id: String? = null,
    val code: String? = null,
    val name: String? = null,
    val currency: String? = null,
    val dialCode: String? = null,
    val dialingCode: String? = null,
    val defaultLanguage: String? = "en",
    val supportedLanguages: List<String>? = listOf("en"),
    val isActive: Boolean? = true
)

// ✅✅✅ Models for phone existence check
data class CheckPhoneExistsRequest(
    val phone: String,
    val countryCode: String
)

data class CheckPhoneExistsResponse(
    val exists: Boolean = false,
    val role: String? = null
)

// =========================
// ✅✅✅ INSURANCE DTOs
// =========================
data class InsurancePartnersResponse(
    val partners: List<InsurancePartnerDto>? = emptyList(),
    val countryCode: String? = null
)

data class InsurancePartnerDto(
    val _id: String? = null,
    val name: String? = null,
    val partnerCode: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    val countryCodes: List<String>? = emptyList(),
    val isActive: Boolean? = true
)

data class ValidateInsuranceCodeRequest(
    val partnerId: String,
    val code: String,
    val countryCode: String,
    val phone: String? = null,
    val email: String? = null
)

data class ValidateInsuranceCodeResponse(
    val ok: Boolean? = false,
    val message: String? = null,
    val countryCode: String? = null,
    val code: Any? = null
)