// JobSearchingScreen.kt
package com.towmech.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.delay

@Composable
fun JobSearchingScreen(
    jobId: String,
    onProviderAssigned: (String) -> Unit
) {
    val context = LocalContext.current
    val darkBlue = Color(0xFF0033A0)

    var status by remember { mutableStateOf("SEARCHING") }
    var assignedProviderName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    fun normalizeStatus(raw: String?): String {
        return (raw ?: "UNKNOWN")
            .trim()
            .uppercase()
            .replace(" ", "_")
            .replace("-", "_")
    }

    fun isProviderFound(statusUpper: String, hasAssignedProvider: Boolean): Boolean {
        if (hasAssignedProvider) return true

        return statusUpper in setOf(
            "ASSIGNED",
            "ACCEPTED",
            "IN_PROGRESS",
            "INPROGRESS",
            "STARTED",
            "ARRIVING",
            "ON_THE_WAY",
            "ONWAY",
            "EN_ROUTE",
            "COMPLETED"
        )
    }

    LaunchedEffect(jobId) {
        while (true) {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    status = "SESSION_EXPIRED"
                    errorMessage = "Session expired. Please login again."
                    break
                }

                val res = ApiClient.apiService.getJobById(
                    token = "Bearer $token",
                    jobId = jobId
                )

                val job = res.job

                // ✅ Null => treat as temporary; keep polling
                if (job == null) {
                    status = "SEARCHING"
                    assignedProviderName = ""
                    // keep error blank so UI doesn't look "broken"
                    errorMessage = ""
                } else {
                    val statusUpper = normalizeStatus(job.status)
                    status = statusUpper

                    val assigned = job.assignedTo
                    val hasAssigned =
                        assigned != null && (!assigned._id.isNullOrBlank() || !assigned.name.isNullOrBlank())

                    assignedProviderName =
                        if (hasAssigned) assigned?.name?.takeIf { it.isNotBlank() } ?: "Provider"
                        else ""

                    if (isProviderFound(statusUpper, hasAssigned)) {
                        onProviderAssigned(jobId)
                        break
                    }

                    errorMessage = ""
                }

            } catch (_: Exception) {
                // Ignore transient failures
                errorMessage = ""
            }

            delay(2500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )

            Spacer(modifier = Modifier.height(25.dp))

            CircularProgressIndicator(color = darkBlue)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Searching for a provider...",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Status: $status",
                fontSize = 16.sp,
                color = darkBlue
            )

            if (assignedProviderName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Assigned: $assignedProviderName",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = darkBlue
                )
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_hero),
                contentDescription = "Hero",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
    }
}