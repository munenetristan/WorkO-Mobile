// app/src/main/java/com/towmech/app/TowMechApp.kt
package com.towmech.app

import android.app.Application
import co.paystack.android.PaystackSdk
import com.towmech.app.api.ApiClient

class TowMechApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ IMPORTANT: initialize ApiClient so it can read token/country/language for headers
        ApiClient.init(this)

        // ✅ Initialize Paystack
        PaystackSdk.initialize(applicationContext)

        // ✅ Set your Paystack public key
        PaystackSdk.setPublicKey("pk_test_79a5a763fe2a9626907bca857be9ccf9240a4176")
    }
}