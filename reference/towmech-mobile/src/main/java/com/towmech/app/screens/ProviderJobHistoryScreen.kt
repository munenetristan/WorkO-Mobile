package com.towmech.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.JobResponse
import com.towmech.app.data.TokenManager
import java.util.Locale

@Composable
fun ProviderJobHistoryScreen(
    onBack: () -> Unit, // kept for compatibility even though button removed
    onOpenJob: (String) -> Unit
) {
    val context = LocalContext.current

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    var jobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    fun calcProviderPayout(job: JobResponse): Double {
        val providerDue = job.pricing?.providerAmountDue
        if (providerDue != null) return providerDue

        val total = job.pricing?.estimatedTotal ?: 0.0
        val commission = job.pricing?.commissionAmount ?: 0.0
        return (total - commission).coerceAtLeast(0.0)
    }

    LaunchedEffect(Unit) {
        try {
            val token = TokenManager.getToken(context)

            if (token.isNullOrBlank()) {
                errorMessage = "Session expired. Login again."
                loading = false
                return@LaunchedEffect
            }

            val response = ApiClient.apiService.getProviderJobHistory("Bearer $token")
            jobs = response.jobs ?: emptyList()

            loading = false
            errorMessage = ""
        } catch (e: Exception) {
            loading = false
            val msg = e.message ?: "Unknown error"
            errorMessage =
                if (msg.contains("404")) {
                    "HTTP 404: Endpoint not found.\n" +
                            "✅ Confirm backend route exists:\n" +
                            "GET /api/providers/jobs/history"
                } else {
                    "Error loading job history: $msg"
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // ✅ reduce padding so list can have more space
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ✅ Header (fixed)
            Spacer(modifier = Modifier.height(10.dp))

            val showLogo = false // set true on screens where you want it

            if (showLogo) {
                Image(
                    painter = painterResource(id = R.drawable.towmech_logo),
                    contentDescription = "TowMech Logo",
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Provider Job History",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ✅ Content Area (this becomes the ONLY scrollable space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // ✅ gives maximum scroll space between header and hero
            ) {
                when {
                    loading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = darkBlue)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading job history...",
                                color = darkBlue,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    errorMessage.isNotEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = errorMessage,
                                color = Color.Red,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    jobs.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No job history found.",
                                color = darkBlue,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(jobs) { job ->
                                val currency = job.pricing?.currency ?: "ZAR"
                                val payout = calcProviderPayout(job)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable { onOpenJob(job._id) },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White.copy(alpha = 0.92f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {

                                        Text(
                                            text = job.title ?: "TowMech Job",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = darkBlue
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "Status: ${job.status ?: "—"}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (job.status.equals("COMPLETED", true)) green else darkBlue
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "Pickup: ${job.pickupAddressText ?: "Not provided"}",
                                            fontSize = 14.sp,
                                            color = darkBlue
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "Drop Off: ${job.dropoffAddressText ?: "Not provided"}",
                                            fontSize = 14.sp,
                                            color = darkBlue
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "Amount: $currency ${
                                                String.format(
                                                    Locale.getDefault(),
                                                    "%.2f",
                                                    payout
                                                )
                                            }",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = darkBlue
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "Job ID: ${job._id}",
                                            fontSize = 13.sp,
                                            color = darkBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ✅ Bottom image stays fixed (not inside the list)
            Spacer(modifier = Modifier.height(8.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_hero),
                contentDescription = "Hero",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}