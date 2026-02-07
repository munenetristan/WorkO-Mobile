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
import com.towmech.app.api.readErrorMessage
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

@Composable
fun IncomingJobRequestScreen(
    jobId: String,
    onBack: () -> Unit,
    onJobAccepted: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)
    val red = Color(0xFFD32F2F)

    var job by remember { mutableStateOf<JobResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var accepting by remember { mutableStateOf(false) }

    fun friendlyMessage(e: Throwable): String {
        return when (e) {
            is HttpException -> e.readErrorMessage()
                ?: when (e.code()) {
                    401, 403 -> "Session expired. Please login again."
                    404 -> "Job not found."
                    500 -> "Server error. Please try again shortly."
                    else -> "Request failed (HTTP ${e.code()}). Please try again."
                }

            is IOException -> "Network error. Check your internet connection and try again."
            else -> "Something went wrong. Please try again."
        }
    }

    // ✅ SAFE HANDLING: Requests Tab route (jobId = "none")
    LaunchedEffect(jobId) {

        // ✅ If provider tapped Requests tab (no job selected)
        if (jobId == "none" || jobId.isBlank()) {
            loading = false
            job = null
            errorMessage = ""
            return@LaunchedEffect
        }

        try {
            val token = TokenManager.getToken(context)

            if (token.isNullOrBlank()) {
                errorMessage = "Session expired. Login again."
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
            errorMessage = if (fetched == null) "Job not found." else ""

        } catch (e: Exception) {
            loading = false
            errorMessage = friendlyMessage(e)
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
                modifier = Modifier.size(100.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Incoming Job Request",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ✅ If user tapped Requests tab (jobId = none)
            if ((jobId == "none" || jobId.isBlank()) && !loading) {
                Text(
                    text = "No incoming job request yet.\n\nTap a job from Jobs tab to view it here.",
                    color = darkBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(30.dp))
            }

            if (loading) {
                CircularProgressIndicator(color = darkBlue)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Loading job request...",
                    color = darkBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Dropoff: ${currentJob.dropoffAddressText ?: "Not provided"}",
                            fontSize = 16.sp,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Status: ${currentJob.status ?: "UNKNOWN"}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = green
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ✅ ACCEPT BUTTON
                Button(
                    enabled = !accepting,
                    onClick = {
                        scope.launch {
                            try {
                                accepting = true

                                val token = TokenManager.getToken(context)
                                if (token.isNullOrBlank()) {
                                    accepting = false
                                    Toast.makeText(
                                        context,
                                        "Session expired. Please login again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }

                                ApiClient.apiService.acceptJob(
                                    token = "Bearer $token",
                                    jobId = currentJob._id
                                )

                                Toast.makeText(context, "Job Accepted ✅", Toast.LENGTH_SHORT).show()
                                onJobAccepted(currentJob._id)

                            } catch (e: Exception) {
                                Toast.makeText(context, friendlyMessage(e), Toast.LENGTH_LONG).show()
                            } finally {
                                accepting = false
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
                        text = if (accepting) "Accepting..." else "Accept Job ✅",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ✅ REJECT BUTTON
                Button(
                    enabled = !accepting,
                    onClick = {
                        scope.launch {
                            try {
                                accepting = true

                                val token = TokenManager.getToken(context)
                                if (token.isNullOrBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Session expired. Please login again.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }

                                ApiClient.apiService.rejectJob(
                                    token = "Bearer $token",
                                    jobId = currentJob._id
                                )

                                Toast.makeText(context, "Job Rejected ❌", Toast.LENGTH_SHORT).show()
                                onBack()

                            } catch (e: Exception) {
                                Toast.makeText(context, friendlyMessage(e), Toast.LENGTH_LONG).show()
                            } finally {
                                accepting = false
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

                Spacer(modifier = Modifier.height(20.dp))
            }

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
        }
    }
}