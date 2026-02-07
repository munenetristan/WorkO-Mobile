// ProviderJobDetailScreen.kt
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
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.launch

@Composable
fun ProviderJobDetailsScreen(
    jobId: String,
    onBack: () -> Unit,
    onAccepted: (String) -> Unit,
    // ✅ NEW (safe default so existing calls don't break)
    onOpenTracking: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)
    val red = Color(0xFFD32F2F)

    var job by remember { mutableStateOf<JobResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var cancelUpdating by remember { mutableStateOf(false) }

    /**
     * ✅ Load Job Details
     * - First try provider broadcasted endpoint (correct for Accept/Reject screen)
     * - Fallback to generic wrapper endpoint if your backend uses /api/jobs/:id for providers too
     */
    LaunchedEffect(jobId) {
        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "Session expired. Login again."
                loading = false
                return@LaunchedEffect
            }

            val auth = "Bearer $token"

            // ✅ Primary: broadcasted job details (most correct for this screen)
            val fetched: JobResponse? = try {
                ApiClient.apiService.getBroadcastedJobById(
                    token = auth,
                    jobId = jobId
                )
            } catch (_: Exception) {
                // ✅ Fallback: wrapper endpoint { job: {...} }
                try {
                    val res = ApiClient.apiService.getJobById(
                        token = auth,
                        jobId = jobId
                    )
                    res.job
                } catch (_: Exception) {
                    null
                }
            }

            job = fetched
            loading = false
            errorMessage = if (fetched == null) "Job not found." else ""

        } catch (e: Exception) {
            loading = false
            errorMessage = "Failed to load job: ${e.message}"
        }
    }

    fun cancelAssignedJob() {
        val currentJob = job ?: return
        scope.launch {
            try {
                cancelUpdating = true
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) return@launch

                // ✅ Provider cancel endpoint — allowed only when ASSIGNED (and within backend 2-minute window)
                ApiClient.apiService.cancelJob(
                    token = "Bearer $token",
                    jobId = currentJob._id,
                    request = mapOf("reason" to "Cancelled by provider from details screen")
                )

                Toast.makeText(context, "Job cancelled ✅ (rebroadcasted)", Toast.LENGTH_SHORT).show()
                onBack()

            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Cancel failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                cancelUpdating = false
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
                text = "Job Details",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (loading) {
                CircularProgressIndicator(color = darkBlue)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Loading job details...",
                    color = darkBlue,
                    fontSize = 18.sp
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

            job?.let { currentJob ->

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        Text(
                            text = currentJob.title ?: "TowMech Job",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Status: ${currentJob.status}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = green
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Pickup Address:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkBlue
                        )
                        Text(
                            text = currentJob.pickupAddressText ?: "Not provided",
                            fontSize = 15.sp,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Dropoff Address:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkBlue
                        )
                        Text(
                            text = currentJob.dropoffAddressText ?: "Not provided",
                            fontSize = 15.sp,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Customer:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkBlue
                        )

                        Text(
                            text = currentJob.customer?.name ?: "Unknown",
                            fontSize = 15.sp,
                            color = darkBlue
                        )

                        Text(
                            text = currentJob.customer?.email ?: "",
                            fontSize = 15.sp,
                            color = darkBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val status = (currentJob.status ?: "").uppercase()
                val canRespond = status == "BROADCASTED" && currentJob.assignedTo == null
                val canCancelAssigned = status == "ASSIGNED"
                val canOpenTracking = status == "ASSIGNED" || status == "IN_PROGRESS"

                // ✅ NEW: Open Tracking button (big) when ASSIGNED / IN_PROGRESS
                if (canOpenTracking) {
                    Button(
                        onClick = { onOpenTracking(currentJob._id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
                    ) {
                        Text(
                            text = "Open Tracking",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (canRespond) {
                    // ===== Accept / Reject (unchanged) =====
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val token = TokenManager.getToken(context)
                                    if (token.isNullOrBlank()) return@launch

                                    ApiClient.apiService.acceptJob(
                                        token = "Bearer $token",
                                        jobId = currentJob._id
                                    )

                                    Toast.makeText(context, "Job Accepted ✅", Toast.LENGTH_SHORT).show()
                                    onAccepted(currentJob._id)

                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Accept failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
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
                            text = "Accept Job ✅",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val token = TokenManager.getToken(context)
                                    if (token.isNullOrBlank()) return@launch

                                    ApiClient.apiService.rejectJob(
                                        token = "Bearer $token",
                                        jobId = currentJob._id
                                    )

                                    Toast.makeText(context, "Job Rejected ❌", Toast.LENGTH_SHORT).show()
                                    onBack()

                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Reject failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = red)
                    ) {
                        Text(
                            text = "Reject Job ❌",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                } else {
                    Text(
                        text = "This job is no longer available.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = red,
                        textAlign = TextAlign.Center
                    )

                    // ✅ Cancel button here too if job is ASSIGNED
                    if (canCancelAssigned) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { cancelAssignedJob() },
                            enabled = !cancelUpdating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(40.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = red)
                        ) {
                            Text(
                                if (cancelUpdating) "Cancelling..." else "Cancel Job (ASSIGNED)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
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