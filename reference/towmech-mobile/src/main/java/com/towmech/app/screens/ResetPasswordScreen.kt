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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.ResetPasswordRequest
import com.towmech.app.api.readErrorMessage
import com.towmech.app.ui.TowMechTextField
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun ResetPasswordScreen(
    phone: String,
    onResetSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkBlue = Color(0xFF0033A0)

    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Reset Password",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Phone: $phone",
                fontSize = 13.sp,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            TowMechTextField(
                value = otp,
                onValueChange = { input ->
                    otp = input.filter { it.isDigit() }.take(6)
                },
                label = "OTP Code",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(14.dp))

            TowMechTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = "New Password",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Password
            )

            Spacer(modifier = Modifier.height(14.dp))

            TowMechTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm Password",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Password
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                onClick = {
                    if (loading) return@Button

                    val cleanOtp = otp.trim()
                    val cleanNew = newPassword.trim()
                    val cleanConfirm = confirmPassword.trim()

                    if (cleanOtp.length != 6) {
                        errorMessage = "Enter a valid 6-digit OTP"
                        return@Button
                    }
                    if (cleanNew.isBlank() || cleanNew.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }
                    if (cleanNew != cleanConfirm) {
                        errorMessage = "Passwords do not match"
                        return@Button
                    }

                    loading = true
                    errorMessage = ""

                    scope.launch {
                        try {
                            ApiClient.apiService.resetPassword(
                                ResetPasswordRequest(
                                    phone = phone,
                                    otp = cleanOtp,
                                    newPassword = cleanNew
                                )
                            )

                            toast("Password reset ✅")
                            onResetSuccess()

                        } catch (e: HttpException) {
                            val backendMsg = e.readErrorMessage()
                            errorMessage = backendMsg ?: "Reset failed. Try again."
                        } catch (e: Exception) {
                            errorMessage = "Network error. Please try again."
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                enabled = !loading,
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text(
                    text = if (loading) "Resetting..." else "Reset Password",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onBack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(40.dp)
            ) {
                Text("Back", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.weight(1f))

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
