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
fun JobDetailsScreen(
    jobId: String,
    onBack: () -> Unit,
    onAccepted: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)
    val red = Color(0xFFD32F2F)

    var job by remember { mutableStateOf<JobResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    // ✅ Load job details (wrapper-safe)
    LaunchedEffect(jobId) {
        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "Session expired. Please login again."
                loading = false
                return@LaunchedEffect
            }

            val res = ApiClient.apiService.getJobById(
                token = "Bearer $token",
                jobId = jobId
            )

            val fetched = res.job
            job = fetched
            loading = false
            errorMessage = if (fetched == null) "Job not found (empty response)." else ""

        } catch (e: Exception) {
            loading = false
            errorMessage = "Failed to load job: ${e.message}"
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

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentScale = ContentScale.Fit
            )

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
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading...", fontSize = 18.sp, color = darkBlue)
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
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
                            text = "Pickup: ${currentJob.pickupAddressText ?: "Not provided"}",
                            fontSize = 16.sp,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Dropoff: ${currentJob.dropoffAddressText ?: "Not provided"}",
                            fontSize = 16.sp,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Status: ${currentJob.status}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = green
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                                Toast.makeText(context, "Accept failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green)
                ) {
                    Text("Accept Job ✅", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                                Toast.makeText(context, "Reject failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = red)
                ) {
                    Text("Reject Job ❌", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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