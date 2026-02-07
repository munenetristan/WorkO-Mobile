// CustomerActiveJobsScreen.kt  ✅ FIXED (removed onBack)
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
import kotlinx.coroutines.launch

@Composable
fun CustomerActiveJobsScreen(
    onOpenTracking: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    var jobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = "Session expired. Login again."
                    loading = false
                    return@launch
                }

                val response = ApiClient.apiService.getCustomerActiveJobs("Bearer $token")
                jobs = response.jobs ?: emptyList()
                loading = false
                errorMessage = ""

            } catch (e: Exception) {
                loading = false
                errorMessage = "Failed to load jobs: ${e.message}"
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

            Spacer(modifier = Modifier.height(15.dp))

            Text(
                text = "Active Jobs",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (loading) {
                CircularProgressIndicator(color = darkBlue)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading jobs...", color = darkBlue, fontSize = 18.sp)
            }

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (!loading && jobs.isEmpty() && errorMessage.isEmpty()) {
                Text(
                    text = "No active jobs found ✅",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = darkBlue
                )
            }

            if (jobs.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.size(100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(jobs) { job ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    job._id?.let { onOpenTracking(it) }
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.92f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {

                                Text(
                                    text = job.title ?: "TowMech Job",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = darkBlue
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Status: ${job.status}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (job.status == "COMPLETED") green else darkBlue
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Pickup: ${job.pickupAddressText ?: "Not provided"}",
                                    fontSize = 15.sp,
                                    color = darkBlue
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Provider: ${job.assignedTo?.name ?: "Searching..."}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (job.assignedTo != null) green else darkBlue
                                )
                            }
                        }
                    }
                }
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