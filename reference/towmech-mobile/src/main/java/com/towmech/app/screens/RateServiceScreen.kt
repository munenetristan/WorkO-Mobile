package com.towmech.app.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.RatingRequest
import com.towmech.app.api.readErrorMessage
import com.towmech.app.data.TokenManager
import com.towmech.app.ui.TowMechTextField
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun RateServiceScreen(
    jobId: String,
    onBack: () -> Unit,
    onSubmitRating: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // ✅ Dynamic display: Rate (Name)
    var targetName by remember { mutableStateOf("User") }
    var targetRoleLabel by remember { mutableStateOf("provider/customer") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // ✅ Load job + decide who to rate (wrapper-safe, NO reflection)
    LaunchedEffect(jobId) {
        try {
            val token = TokenManager.getToken(context) ?: return@LaunchedEffect
            val auth = "Bearer $token"

            val profile = ApiClient.apiService.getProfile(auth)
            val myRole = (profile.user?.role ?: "").trim().lowercase()

            val res = ApiClient.apiService.getJobById(auth, jobId)
            val job = res.job

            val providerName = job?.assignedTo?.name?.takeIf { it.isNotBlank() } ?: "Provider"
            val customerName = job?.customer?.name?.takeIf { it.isNotBlank() } ?: "Customer"

            if (myRole == "customer") {
                targetName = providerName
                targetRoleLabel = "provider"
            } else {
                targetName = customerName
                targetRoleLabel = "customer"
            }
        } catch (_: Exception) {
            targetName = "Remember to rate"
            targetRoleLabel = "provider/customer"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    painter = painterResource(id = R.drawable.towmech_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Thank you for using our service,\nplease rate $targetRoleLabel",
                    fontSize = 16.sp,
                    color = darkBlue,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Rate $targetName",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkBlue,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 1..5) {
                        IconButton(
                            enabled = !loading,
                            onClick = { rating = i }
                        ) {
                            Icon(
                                imageVector = if (rating >= i) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Star $i",
                                tint = if (rating >= i) Color(0xFFFFC107) else darkBlue,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TowMechTextField(
                    value = comment,
                    onValueChange = { newValue ->
                        comment = if (newValue.length <= 200) newValue else newValue.take(200)
                    },
                    label = "Comment / Complaint (max 200)",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Text
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${comment.length}/200",
                    fontSize = 12.sp,
                    color = Color(0xFF444444),
                    modifier = Modifier.align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    enabled = !loading,
                    onClick = {
                        if (rating !in 1..5) {
                            errorMessage = "Please select a rating (1 to 5 stars)"
                            return@Button
                        }

                        loading = true
                        errorMessage = ""

                        scope.launch {
                            try {
                                val token = TokenManager.getToken(context)
                                if (token.isNullOrBlank()) {
                                    errorMessage = "Session expired. Please login again."
                                    return@launch
                                }

                                ApiClient.apiService.submitRating(
                                    token = "Bearer $token",
                                    request = RatingRequest(
                                        jobId = jobId,
                                        rating = rating,
                                        comment = comment.trim()
                                    )
                                )

                                toast("Rating submitted ✅")
                                onSubmitRating()

                            } catch (e: HttpException) {
                                val backendMsg = e.readErrorMessage()
                                errorMessage = backendMsg ?: "Failed to submit rating. Please try again."
                            } catch (_: Exception) {
                                errorMessage = "Network error. Please try again."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green)
                ) {
                    Text(
                        text = if (loading) "Submitting..." else "Submit",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    enabled = !loading,
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(40.dp)
                ) {
                    Text("Back", fontWeight = FontWeight.Bold, color = darkBlue)
                }
            }
        }
    }
}