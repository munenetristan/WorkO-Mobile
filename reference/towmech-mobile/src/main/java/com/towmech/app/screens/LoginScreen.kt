package com.towmech.app.screens

import android.util.Log
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
import com.towmech.app.api.LoginRequest
import com.towmech.app.api.readErrorMessage
import com.towmech.app.data.TokenManager
import com.towmech.app.ui.TowMechTextField
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit, // ✅ returns PHONE (E.164-ish)
    onForgotPassword: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkBlue = Color(0xFF0033A0)

    val countryCode = remember { TokenManager.getCountryCode(context) }
    val dialCode = remember { TokenManager.getDialCode(context) }

    var phoneDigits by remember { mutableStateOf(TokenManager.getLastPhoneDigits(context) ?: "") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun buildE164(dial: String, digitsOnly: String): String {
        val d = dial.trim().ifBlank { "+27" }
        var p = digitsOnly.filter { it.isDigit() }.take(15)
        if (p.startsWith("0")) p = p.drop(1)
        return (if (d.startsWith("+")) d else "+$d") + p
    }

    val phoneFull = remember(phoneDigits, dialCode) { buildE164(dialCode, phoneDigits) }

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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Login",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Country: $countryCode   Dial: $dialCode",
                fontSize = 13.sp,
                color = Color(0xFF111827),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            TowMechTextField(
                value = phoneDigits,
                onValueChange = { input ->
                    phoneDigits = input.filter { it.isDigit() }.take(15)
                    TokenManager.saveLastPhoneDigits(context, phoneDigits)
                },
                label = "Mobile number (without country code)",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Phone
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Will be sent as: $phoneFull",
                fontSize = 12.sp,
                color = Color(0xFF374151)
            )

            Spacer(modifier = Modifier.height(16.dp))

            TowMechTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Password
            )

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = {
                    errorMessage = ""
                    onForgotPassword(phoneFull)
                },
                enabled = !loading
            ) {
                Text(
                    text = "Forgot password?",
                    color = darkBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    if (loading) return@Button

                    val cleanPassword = password.trim()
                    val cleanPhone = phoneFull.trim()

                    if (phoneDigits.trim().isBlank() || cleanPassword.isBlank()) {
                        errorMessage = "Please enter phone number and password"
                        return@Button
                    }

                    loading = true
                    errorMessage = ""

                    scope.launch {
                        try {
                            val res = ApiClient.apiService.login(
                                LoginRequest(
                                    phone = cleanPhone,
                                    password = cleanPassword,
                                    countryCode = countryCode
                                )
                            )

                            Log.d("LOGIN_DEBUG", "Login response: $res")

                            toast("OTP Sent ✅")
                            onLoginSuccess(cleanPhone)

                        } catch (e: HttpException) {
                            val backendMsg = e.readErrorMessage()
                            errorMessage = backendMsg ?: "Login failed. Please try again."
                        } catch (e: Exception) {
                            errorMessage = "Network error. Please try again."
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                enabled = !loading,
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text(
                    text = if (loading) "Loading..." else "Login",
                    fontSize = 20.sp,
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
                    .height(220.dp)
            )
        }
    }
}