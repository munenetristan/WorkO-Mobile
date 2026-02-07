package com.towmech.app.screens

import android.widget.Toast
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
import kotlinx.coroutines.launch

@Composable
fun CustomerJobsScreen(
    onOpenJobTracking: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    var activeJobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }
    var historyJobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }

    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun loadJobs() {
        scope.launch {
            try {
                loading = true
                errorMessage = ""

                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    loading = false
                    errorMessage = "Session expired. Please login again."
                    return@launch
                }

                val activeRes = ApiClient.apiService.getCustomerActiveJobs("Bearer $token")
                val historyRes = ApiClient.apiService.getCustomerJobHistory("Bearer $token")

                activeJobs = activeRes.jobs ?: emptyList()
                historyJobs = historyRes.jobs ?: emptyList()

                loading = false
                errorMessage = ""

            } catch (e: Exception) {
                loading = false
                errorMessage = "Failed to load jobs: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) { loadJobs() }

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

            Spacer(modifier = Modifier.height(25.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "My Jobs",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ✅ LOADING
            if (loading) {
                CircularProgressIndicator(color = darkBlue)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Loading jobs...", color = darkBlue, fontSize = 16.sp)
            }

            // ✅ ERROR
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ✅ NEW: TRACK ACTIVE JOB BUTTON (manual recovery)
            Button(
                onClick = {
                    if (loading) {
                        showToast("Still loading jobs...")
                        return@Button
                    }

                    // Prefer an active job that is ASSIGNED or IN_PROGRESS if available
                    val best = activeJobs.firstOrNull { it.status == "ASSIGNED" || it.status == "IN_PROGRESS" }
                        ?: activeJobs.firstOrNull()

                    if (best?._id.isNullOrBlank()) {
                        showToast("No active job to track ✅")
                        return@Button
                    }

                    onOpenJobTracking(best._id)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text(
                    text = "Track Active Job",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ✅ JOB LIST
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                item {
                    Text(
                        text = "Active Jobs",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue
                    )
                }

                if (!loading && activeJobs.isEmpty()) {
                    item {
                        Text("No active jobs ✅", color = green, fontSize = 15.sp)
                    }
                } else {
                    items(activeJobs) { job ->
                        JobCard(job = job) {
                            onOpenJobTracking(job._id)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Job History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue
                    )
                }

                if (!loading && historyJobs.isEmpty()) {
                    item {
                        Text("No job history found ✅", color = green, fontSize = 15.sp)
                    }
                } else {
                    items(historyJobs) { job ->
                        JobCard(job = job) {
                            onOpenJobTracking(job._id)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ✅ REFRESH BUTTON
            Button(
                onClick = { loadJobs() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = green)
            ) {
                Text(
                    text = "Refresh",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun JobCard(job: JobResponse, onClick: () -> Unit) {

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    val statusColor = when (job.status) {
        "COMPLETED" -> green
        "CANCELLED" -> Color.Red
        else -> darkBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
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
                text = "Status: ${job.status ?: "UNKNOWN"}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Pickup: ${job.pickupAddressText ?: "Not provided"}",
                fontSize = 14.sp,
                color = darkBlue
            )

            Text(
                text = "Dropoff: ${job.dropoffAddressText ?: "Not provided"}",
                fontSize = 14.sp,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (!job.assignedTo?.name.isNullOrBlank()) {
                Text(
                    text = "Provider: ${job.assignedTo?.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = green
                )
            } else {
                Text(
                    text = "No provider assigned yet",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}