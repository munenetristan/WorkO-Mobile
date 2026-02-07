// app/src/main/java/com/towmech/app/screens/CountryVerifyOtpScreen.kt
package com.towmech.app.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towmech.app.api.ApiClient
import com.towmech.app.api.VerifyCountryOtpRequest
import com.towmech.app.api.readErrorMessage
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun CountryVerifyOtpScreen(
    phone: String,
    onVerified: (verifiedPhone: String) -> Unit, // ✅ return phone back to NavGraph
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ✅ IMPORTANT: decode phone coming from nav route (keeps + intact if encoded)
    val decodedPhone = remember(phone) { Uri.decode(phone).trim() }

    // ✅ IMPORTANT: must match what was used when sending OTP
    val countryCode = remember {
        TokenManager.getCountryCode(context)?.trim()?.uppercase().orEmpty()
    }

    val green = Color(0xFF007A3D)
    val blue = Color(0xFF0033A0)

    var otp by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verify Country OTP", fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text("OTP sent to: $decodedPhone", fontSize = 14.sp)

        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it.filter { ch -> ch.isDigit() }.take(6) },
            label = { Text("Enter OTP") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !loading
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                if (loading) return@Button
                val cleanOtp = otp.trim()

                if (decodedPhone.isBlank()) {
                    toast("Phone number missing. Go back and try again.")
                    return@Button
                }

                if (countryCode.isBlank()) {
                    toast("Country code missing. Please go back and select country again.")
                    return@Button
                }

                if (cleanOtp.length < 4) {
                    toast("Enter a valid OTP")
                    return@Button
                }

                loading = true
                scope.launch {
                    try {
                        // ✅ send countryCode as backend key expects phone + countryCode
                        ApiClient.apiService.verifyCountryOtp(
                            VerifyCountryOtpRequest(
                                phone = decodedPhone,
                                otp = cleanOtp,
                                countryCode = countryCode
                            )
                        )

                        toast("Country confirmed ✅")

                        // ✅ return verified phone to NavGraph
                        onVerified(decodedPhone)

                    } catch (e: HttpException) {
                        val backendMsg = e.readErrorMessage()
                        toast(backendMsg ?: "OTP verification failed.")
                    } catch (_: Exception) {
                        toast("Network error. Please try again.")
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !loading,
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = green)
        ) {
            Text(if (loading) "Verifying..." else "Verify OTP", color = Color.White, fontSize = 18.sp)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !loading,
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = blue)
        ) {
            Text("Back", color = Color.White, fontSize = 18.sp)
        }
    }
}