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
import com.towmech.app.api.VerifyOtpRequest
import com.towmech.app.api.readErrorMessage
import com.towmech.app.data.TokenManager
import com.towmech.app.notifications.FcmTokenSync
import kotlinx.coroutines.launch
import retrofit2.HttpException

@Composable
fun VerifyOtpScreen(
    phone: String,
    onVerified: (role: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ✅ Country values set on CountryStartScreen
    val countryCode = remember { TokenManager.getCountryCode(context) }
    val dialCode = remember { TokenManager.getDialCode(context) }

    // Decode navigation arg (could include %2B for "+")
    val decodedPhoneRaw = remember(phone) { Uri.decode(phone).trim() }

    val green = Color(0xFF007A3D)
    val blue = Color(0xFF0033A0)

    var otp by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    fun buildE164(dial: String, input: String): String {
        val d = dial.trim().ifBlank { "+27" }

        // keep digits only
        var digits = input.trim().filter { it.isDigit() }.take(15)

        // drop leading 0 if user typed local format
        if (digits.startsWith("0")) digits = digits.drop(1)

        val dialFixed = if (d.startsWith("+")) d else "+$d"
        return (dialFixed + digits).replace(" ", "")
    }

    // ✅ Final phone used for OTP verification (guaranteed E.164)
    val phoneE164 = remember(decodedPhoneRaw, dialCode) {
        val clean = decodedPhoneRaw.replace(" ", "")
        if (clean.startsWith("+")) clean
        else buildE164(dialCode, clean)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Verify OTP", fontSize = 22.sp)

        Spacer(Modifier.height(6.dp))

        // ✅ Country badge (helps debugging / confirms correct routing)
        Text(
            text = "Country: $countryCode   Dial: $dialCode",
            fontSize = 13.sp,
            color = Color(0xFF111827)
        )

        Spacer(Modifier.height(8.dp))

        Text("OTP sent to: $phoneE164", fontSize = 14.sp)

        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = otp,
            onValueChange = { input ->
                otp = input.filter { it.isDigit() }.take(6)
            },
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

                if (phoneE164.isBlank()) {
                    toast("Phone number missing. Go back and try again.")
                    return@Button
                }

                if (cleanOtp.length < 4) {
                    toast("Enter a valid OTP")
                    return@Button
                }

                loading = true

                scope.launch {
                    try {
                        val res = ApiClient.apiService.verifyOtp(
                            VerifyOtpRequest(
                                phone = phoneE164,
                                otp = cleanOtp
                            )
                        )

                        var token = res.token?.trim().orEmpty()
                        val role = res.user?.role?.trim().orEmpty()

                        if (token.startsWith("Bearer ", ignoreCase = true)) {
                            token = token.removePrefix("Bearer ").trim()
                        }

                        if (token.isBlank()) {
                            toast("OTP verified but token missing from response ❌")
                            loading = false
                            return@launch
                        }

                        // ✅ Save JWT
                        TokenManager.saveToken(context, token)

                        // ✅ upload FCM token immediately after login
                        FcmTokenSync.syncWithJwt(context, token)

                        toast("OTP Verified ✅")
                        onVerified(role)

                    } catch (e: HttpException) {
                        val backendMsg = e.readErrorMessage()
                        toast(backendMsg ?: "OTP verification failed. Please try again.")
                    } catch (e: Exception) {
                        toast("Verify failed. Please check your connection.")
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
            Text(
                text = if (loading) "Verifying..." else "Verify OTP",
                color = Color.White,
                fontSize = 18.sp
            )
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
            Text(
                text = "Back",
                color = Color.White,
                fontSize = 18.sp
            )
        }
    }
}