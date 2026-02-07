// BookingFeePaymentScreen.kt
package com.towmech.app.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.CreatePaymentRequest
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun BookingFeePaymentScreen(
    jobId: String,
    bookingFee: Int,
    currency: String,
    onBack: () -> Unit,
    onPaymentConfirmed: () -> Unit,
    // ✅ NEW (default keeps all existing calls working)
    showMechanicDisclaimer: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // ✅ WebView usage ACTIVE
    var showWebView by remember { mutableStateOf(false) }
    var paymentUrl by remember { mutableStateOf("") }

    // ✅ CALLBACK URL (PayFast return_url will redirect here)
    val callbackUrl = "https://towmech.com/payment-success"

    val mechanicDisclaimerText =
        "Final fee is NOT predetermined. The mechanic will diagnose and agree the final price after pairing."

    /**
     * ✅ IMPORTANT FIX:
     * Use backend as source-of-truth if bookingFee/currency passed in are 0/blank.
     */
    var resolvedBookingFee by remember { mutableStateOf(bookingFee) }
    var resolvedCurrency by remember { mutableStateOf(currency) }
    var fetchingJob by remember { mutableStateOf(false) }

    fun parseHttpException(e: HttpException): String {
        return try {
            val body = e.response()?.errorBody()?.string()
            "HTTP ${e.code()} - ${body ?: "No error body"}"
        } catch (ex: Exception) {
            "HTTP ${e.code()} - Failed to read error body"
        }
    }

    // ✅ Fetch job pricing if needed (when passed fee is 0)
    LaunchedEffect(jobId) {
        val needsFetch = (resolvedBookingFee <= 0) || resolvedCurrency.isBlank()

        if (!needsFetch) return@LaunchedEffect

        fetchingJob = true
        errorMessage = ""

        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                fetchingJob = false
                errorMessage = "Login required"
                return@LaunchedEffect
            }

            // ✅ GET JOB and read pricing.bookingFee + pricing.currency
            val jobRes = ApiClient.apiService.getJobById(
                token = "Bearer $token",
                jobId = jobId
            )

            val feeFromBackend = jobRes.job?.pricing?.bookingFee?.toInt() ?: 0
            val currencyFromBackend = jobRes.job?.pricing?.currency ?: ""

            if (feeFromBackend > 0) {
                resolvedBookingFee = feeFromBackend
            }

            if (currencyFromBackend.isNotBlank()) {
                resolvedCurrency = currencyFromBackend
            }

            if (resolvedBookingFee <= 0) {
                errorMessage =
                    "Booking fee is 0 from backend ❌ Please set booking fee in dashboard/pricing."
            }

        } catch (e: HttpException) {
            errorMessage = parseHttpException(e)
        } catch (e: Exception) {
            errorMessage = "Failed to fetch job pricing: ${e.message}"
        }

        fetchingJob = false
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize()
        )

        // ✅ REAL PAYMENT WEBVIEW
        if (showWebView && paymentUrl.isNotBlank()) {
            PaymentWebViewScreen(
                paymentUrl = paymentUrl,
                callbackUrl = callbackUrl,
                onClose = {
                    showWebView = false
                    paymentUrl = ""
                },
                onPaymentDone = {
                    showWebView = false
                    paymentUrl = ""

                    Toast.makeText(context, "Payment Completed ✅", Toast.LENGTH_LONG).show()
                    onPaymentConfirmed()
                }
            )
        } else {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(40.dp))

                Image(
                    painter = painterResource(id = R.drawable.towmech_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )

                Spacer(modifier = Modifier.height(25.dp))

                Text(
                    text = "Booking Fee Payment",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkBlue
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {

                        if (fetchingJob) {
                            Text(
                                "Loading fee...",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // ✅ Mechanic: DO NOT show "Total Amount"
                        if (!showMechanicDisclaimer) {
                            Text(
                                "Total Amount: ${resolvedCurrency.ifBlank { "ZAR" }} $resolvedBookingFee",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = darkBlue
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            "Booking Fee: ${resolvedCurrency.ifBlank { "ZAR" }} $resolvedBookingFee",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = green
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (showMechanicDisclaimer) {
                            Text(
                                text = mechanicDisclaimerText,
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Text(
                            text = "✅ After payment, your request will be broadcasted automatically.",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(25.dp))

                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = Color.Red)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                /**
                 * ✅ REAL PAYMENT BUTTON
                 * Calls backend /api/payments/create and opens WebView
                 */
                Button(
                    enabled = !loading && resolvedBookingFee > 0 && !fetchingJob,
                    onClick = {
                        loading = true
                        errorMessage = ""

                        Toast.makeText(context, "Initializing payment...", Toast.LENGTH_SHORT).show()

                        scope.launch {
                            try {
                                val token = TokenManager.getToken(context)

                                if (token.isNullOrBlank()) {
                                    loading = false
                                    errorMessage = "Login required"
                                    return@launch
                                }

                                val res = ApiClient.apiService.createBookingPayment(
                                    token = "Bearer $token",
                                    request = CreatePaymentRequest(jobId = jobId)
                                )

                                val url =
                                    res.initResponse?.paymentUrl
                                        ?: res.ikhokha?.paymentUrl
                                        ?: res.paystack?.authorization_url
                                        ?: ""

                                if (url.isBlank()) {
                                    loading = false
                                    errorMessage = "Payment URL missing from backend ❌"
                                    return@launch
                                }

                                loading = false
                                paymentUrl = url
                                showWebView = true

                            } catch (e: HttpException) {
                                loading = false
                                errorMessage = parseHttpException(e)
                            } catch (e: Exception) {
                                loading = false
                                errorMessage = "Payment init failed: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green)
                ) {
                    Text(
                        text = when {
                            fetchingJob -> "Loading..."
                            loading -> "Processing..."
                            else -> "Pay Booking Fee"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
                ) {
                    Text(
                        "Back",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * ✅ WEBVIEW PAYMENT SCREEN (Generic, not only iKhokha)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentWebViewScreen(
    paymentUrl: String,
    callbackUrl: String,
    onClose: () -> Unit,
    onPaymentDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Complete Payment", fontWeight = FontWeight.Bold)

            Button(onClick = onClose) {
                Text("Close")
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            url: String?
                        ): Boolean {
                            if (url == null) return false

                            if (url.startsWith(callbackUrl, ignoreCase = true)) {
                                onPaymentDone()
                                return true
                            }
                            return false
                        }
                    }

                    loadUrl(paymentUrl)
                }
            }
        )
    }
}