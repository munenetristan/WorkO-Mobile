// app/src/main/java/com/towmech/app/navigation/Routes.kt
package com.towmech.app.navigation

object Routes {

    // ========================
    // ✅ COUNTRY ENTRY (NEW)
    // ========================
    const val COUNTRY_START = "country_start"
    const val COUNTRY_VERIFY_OTP = "country_verify_otp"

    // ========================
    // ✅ AUTH FLOW
    // ========================
    const val START = "start"
    const val LOGIN = "login"
    const val VERIFY_OTP = "verify_otp"
    const val ROLE_SELECT = "role_select"

    const val REGISTER_CUSTOMER = "register_customer"
    const val REGISTER_TOWTRUCK = "register_towtruck"
    const val REGISTER_MECHANIC = "register_mechanic"

    const val FORGOT_PASSWORD = "forgot_password"
    const val RESET_PASSWORD = "reset_password"

    // ========================
    // ✅ PROVIDER FLOW
    // ========================
    const val HOME_PROVIDER = "home_provider"
    const val PROVIDER_MAIN = "provider_main"

    const val PROVIDER_HOME_TAB = "provider_home_tab"
    const val PROVIDER_AVAILABLE_JOBS_TAB = "provider_available_jobs_tab"
    const val PROVIDER_INCOMING_REQUEST_TAB = "provider_incoming_request_tab"
    const val PROVIDER_ACTIVE_JOB_TAB = "provider_active_job_tab"
    const val PROVIDER_JOB_HISTORY_TAB = "provider_job_history_tab"
    const val PROVIDER_PROFILE_TAB = "provider_profile_tab"

    const val PROVIDER_JOB_TRACKING = "provider_job_tracking"
    const val PROVIDER_RATE_SERVICE = "provider_rate_service"

    // ========================
    // ✅ CUSTOMER FLOW
    // ========================
    const val CUSTOMER_MAIN = "customer_main"
    const val CUSTOMER_HOME_TAB = "customer_home_tab"
    const val CUSTOMER_JOBS_TAB = "customer_jobs_tab"
    const val CUSTOMER_PROFILE_TAB = "customer_profile_tab"
    const val CUSTOMER_ACTIVE_TAB = "customer_active_tab"
    const val CUSTOMER_HISTORY_TAB = "customer_history_tab"
    const val CUSTOMER_SUPPORT_TAB = "customer_support_tab"

    const val CUSTOMER_SUPPORT_TICKET = "customer_support_ticket"
    const val CUSTOMER_SUPPORT_TICKET_ROUTE = "customer_support_ticket/{ticketId}"

    fun customerSupportTicket(ticketId: String): String {
        return "$CUSTOMER_SUPPORT_TICKET/$ticketId"
    }

    // ========================
    // ✅ REQUEST FLOW
    // ========================
    const val REQUEST_SERVICE = "request_service"

    // ========================
    // ✅ JOB FLOW
    // ========================
    const val JOB_PREVIEW = "job_preview"
    const val JOB_PREVIEW_DETAILS = "job_preview_details"

    const val BOOKING_PAYMENT = "booking_payment"
    const val JOB_SEARCHING = "job_searching"
    const val PICK_LOCATION = "pick_location"

    // ========================
    // ✅ CUSTOMER JOBS + TRACKING
    // ========================
    const val CUSTOMER_JOB_TRACKING = "customer_job_tracking"
    const val CUSTOMER_ACTIVE_JOBS = "customer_active_jobs"

    // ========================
    // ✅ POST COMPLETION
    // ========================
    const val RATE_SERVICE = "rate_service"

    // ========================
    // ✅✅✅ CHAT
    // ========================
    const val JOB_CHAT = "job_chat"
    const val ADMIN_CHAT = "admin_chat"
}