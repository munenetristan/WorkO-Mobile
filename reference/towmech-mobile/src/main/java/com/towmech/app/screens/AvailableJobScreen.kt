package com.towmech.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay

@Composable
fun AvailableJobsScreen(
    onBack: () -> Unit,
    onOpenJob: (String) -> Unit
) {
    val context = LocalContext.current

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    var jobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    // optional manual refresh trigger
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        errorMessage = ""

        while (true) {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = "Session expired. Login again."
                    loading = false
                    break
                }

                // ✅ Provider available jobs = broadcasted jobs
                // ApiService MUST return ProviderJobListResponse { jobs: [...] }
                val response = ApiClient.apiService.getAvailableJobsForProvider("Bearer $token")

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
                                "GET /api/providers/jobs/broadcasted"
                    } else {
                        "Failed to load jobs: $msg"
                    }
            }

            delay(5000)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(35.dp))

            val showLogo = false // set true on screens where you want it

            if (showLogo) {
                Image(
                    painter = painterResource(id = R.drawable.towmech_logo),
                    contentDescription = "TowMech Logo",
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Available Jobs",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ Manual Refresh Button (fast testing)
            OutlinedButton(
                onClick = { refreshTick++ },
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(40.dp),
            ) {
                Text("Refresh now")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (loading) {
                CircularProgressIndicator(color = darkBlue)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Fetching available jobs...",
                    color = darkBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (!loading && jobs.isEmpty() && errorMessage.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No jobs available yet.",
                    color = darkBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            jobs.forEach { job ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onOpenJob(job._id) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        Text(
                            text = job.title ?: "TowMech Job",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Pickup: ${job.pickupAddressText ?: "Not provided"}",
                            fontSize = 16.sp,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Status: ${job.status ?: "UNKNOWN"}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = green
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text(
                    text = "Back",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = R.drawable.towmech_hero),
                contentDescription = "Hero",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}