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
fun CustomerJobHistoryScreen(
    onJobSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var jobs by remember { mutableStateOf<List<JobResponse>>(emptyList()) }

    fun loadHistory() {
        scope.launch {
            try {
                loading = true
                errorMessage = ""

                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = "Session expired. Login again."
                    loading = false
                    return@launch
                }

                val historyRes = ApiClient.apiService.getCustomerJobHistory("Bearer $token")
                jobs = historyRes.jobs ?: emptyList()

                loading = false
                errorMessage = ""

            } catch (e: Exception) {
                loading = false
                errorMessage = "Failed to load history: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) { loadHistory() }

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

            val showLogo = false // set true on screens where you want it

            if (showLogo) {
                Image(
                    painter = painterResource(id = R.drawable.towmech_logo),
                    contentDescription = "TowMech Logo",
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Job History",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (loading) {
                CircularProgressIndicator(color = darkBlue)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Loading history...", color = darkBlue, fontSize = 16.sp)
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            }

            if (!loading && jobs.isEmpty() && errorMessage.isEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "No previous jobs found ✅",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = green,
                    textAlign = TextAlign.Center
                )
            }

            if (!loading && jobs.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(jobs) { job ->

                        val statusColor = when (job.status) {
                            "COMPLETED" -> green
                            "CANCELLED" -> Color.Red
                            else -> darkBlue
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJobSelected(job._id) },
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

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Status: ${job.status ?: "UNKNOWN"}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Pickup: ${job.pickupAddressText ?: "Not provided"}",
                                    fontSize = 15.sp,
                                    color = darkBlue
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { loadHistory() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = green)
            ) {
                Text("Refresh", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}