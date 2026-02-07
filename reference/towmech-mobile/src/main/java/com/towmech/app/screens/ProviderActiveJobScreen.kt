package com.towmech.app.screens

import android.widget.Toast
import androidx.compose.foundation.Image
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
import com.towmech.app.api.UpdateStatusRequest
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.launch

@Composable
fun ProviderActiveJobScreen(
    onBack: () -> Unit,
    onJobCompleted: (String) -> Unit,
    onOpenTracking: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF2E7D32)
    val red = Color(0xFFB00020)

    var activeJobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    var refreshTick by remember { mutableStateOf(0) }

    fun loadActiveJobs() {
        scope.launch {
            loading = true
            errorMessage = ""

            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = "Session expired. Login again."
                    loading = false
                    return@launch
                }

                val response = ApiClient.apiService.getProviderActiveJobs("Bearer $token")
                activeJobs = response.jobs ?: emptyList()
                loading = false
            } catch (e: Exception) {
                loading = false
                errorMessage = "Failed to load active jobs: ${e.message ?: "Unknown error"}"
            }
        }
    }

    LaunchedEffect(refreshTick) { loadActiveJobs() }

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

            Spacer(modifier = Modifier.height(15.dp))

            Text(
                text = "My Active Job",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { refreshTick++ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(40.dp)
            ) { Text("Refresh now") }

            Spacer(modifier = Modifier.height(12.dp))

            if (loading) {
                CircularProgressIndicator(color = darkBlue)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Loading...", color = darkBlue, fontSize = 16.sp)
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage, color = red, fontSize = 16.sp, textAlign = TextAlign.Center)
            }

            if (!loading && activeJobs.isNotEmpty()) {
                val job = activeJobs.first()
                val jobId = job._id
                val status = (job.status ?: "").uppercase()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.93f))
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
                            text = "Status: ${job.status ?: "UNKNOWN"}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status == "COMPLETED") green else darkBlue
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Pickup: ${job.pickupAddressText ?: "Not provided"}", fontSize = 16.sp, color = darkBlue)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Dropoff: ${job.dropoffAddressText ?: "Not provided"}", fontSize = 16.sp, color = darkBlue)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ✅ STRICT FLOW: NO CANCEL after accept
                when (status) {
                    "ASSIGNED" -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val token = TokenManager.getToken(context) ?: return@launch

                                        ApiClient.apiService.updateJobStatus(
                                            token = "Bearer $token",
                                            jobId = jobId,
                                            request = UpdateStatusRequest(status = "IN_PROGRESS")
                                        )

                                        Toast.makeText(context, "Job started ✅", Toast.LENGTH_SHORT).show()

                                        // ✅ Open tracking (now should route B -> C)
                                        onOpenTracking(jobId)

                                        refreshTick++
                                    } catch (e: Exception) {
                                        errorMessage = "Failed to start job: ${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = green),
                            shape = RoundedCornerShape(40.dp)
                        ) {
                            Text("Start Job", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    "IN_PROGRESS" -> {
                        // ✅ Only ONE button desired: Complete Job
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val token = TokenManager.getToken(context) ?: return@launch
                                        ApiClient.apiService.updateJobStatus(
                                            token = "Bearer $token",
                                            jobId = jobId,
                                            request = UpdateStatusRequest(status = "COMPLETED")
                                        )
                                        Toast.makeText(context, "Job completed ✅", Toast.LENGTH_SHORT).show()
                                        refreshTick++
                                        onJobCompleted(jobId)
                                    } catch (e: Exception) {
                                        errorMessage = "Failed to complete job: ${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = red),
                            shape = RoundedCornerShape(40.dp)
                        ) {
                            Text("Complete Job", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    else -> {
                        Button(
                            onClick = { onOpenTracking(jobId) },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = darkBlue),
                            shape = RoundedCornerShape(40.dp)
                        ) {
                            Text("Open Tracking", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(15.dp))
            }

            if (!loading && activeJobs.isEmpty()) {
                Text(
                    text = "No active jobs right now.",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = darkBlue,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(15.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text("Back", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = R.drawable.towmech_hero),
                contentDescription = "Hero",
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}