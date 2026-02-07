package com.towmech.app.config

object PaymentConfig {

    // ✅ Paystack
    const val PAYSTACK_PUBLIC_KEY = "pk_test_xxxxxxxxxxxxxxxxxxxxx"

    // ✅ iKhokha
    const val IKHOKHA_BASE_URL = "https://dev.ikhokha.com"
    const val IKHOKHA_CALLBACK_SUCCESS = "towmech://payment-success"
    const val IKHOKHA_CALLBACK_FAILED = "towmech://payment-failed"
}