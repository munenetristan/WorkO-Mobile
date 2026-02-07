// app/src/main/java/com/towmech/app/screens/RegisterMechanicScreen.kt
package com.towmech.app.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.towmech.app.api.RegisterRequest
import com.towmech.app.api.readErrorMessage
import com.towmech.app.data.TokenManager
import com.towmech.app.ui.TowMechTextField
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMechanicScreen(
    onBack: () -> Unit,
    onOtpRequested: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkBlue = Color(0xFF0033A0)

    // ✅ Always read latest values saved by CountryStartScreen
    val countryCodeRaw = TokenManager.getCountryCode(context)
    val dialCodeRaw = TokenManager.getDialCode(context)
    val countryNameRaw = TokenManager.getCountryName(context)

    val countryCode = remember(countryCodeRaw) { countryCodeRaw.trim().uppercase() }
    val dialCode = remember(dialCodeRaw) { dialCodeRaw.trim().ifBlank { "+27" } }
    val countryName = remember(countryNameRaw) { (countryNameRaw ?: "").trim() }

    fun flagEmoji(iso: String?): String {
        val cc = iso?.trim()?.uppercase().orEmpty()
        if (cc.length != 2) return "🏳️"
        val first = Character.codePointAt(cc, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(cc, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    val countryFlag = remember(countryCode) { flagEmoji(countryCode) }
    val countryDisplay = remember(countryName, countryCode, countryFlag) {
        val name = countryName.takeIf { it.isNotBlank() } ?: countryCode
        "$countryFlag  $name  ($countryCode)"
    }

    // ✅ Backend-required country string (name preferred, fallback to ISO)
    val countryForBackend = remember(countryName, countryCode) {
        (countryName.ifBlank { countryCode }).trim()
    }

    // ✅ Fields
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    // ✅ Digits only (from CountryStartScreen)
    var phoneDigits by remember { mutableStateOf(TokenManager.getLastPhoneDigits(context) ?: "") }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }

    // ✅ Manual ID/Passport field
    var idOrPassportNumber by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // ✅ Dropdown palette (unchanged)
    val dropdownTextColor = darkBlue
    val dropdownLabelColor = darkBlue
    val dropdownContainer = Color.White.copy(alpha = 0.92f)
    val dropdownColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = dropdownTextColor,
        unfocusedTextColor = dropdownTextColor,
        disabledTextColor = dropdownTextColor.copy(alpha = 0.6f),

        focusedLabelColor = dropdownLabelColor,
        unfocusedLabelColor = dropdownLabelColor,
        disabledLabelColor = dropdownLabelColor.copy(alpha = 0.6f),

        focusedContainerColor = dropdownContainer,
        unfocusedContainerColor = dropdownContainer,
        disabledContainerColor = dropdownContainer,

        focusedBorderColor = darkBlue,
        unfocusedBorderColor = darkBlue.copy(alpha = 0.6f),
        disabledBorderColor = darkBlue.copy(alpha = 0.3f),

        cursorColor = darkBlue
    )

    val mechanicCategoryOptions = remember {
        listOf(
            "General Mechanic",
            "Engine Mechanic",
            "Gearbox Mechanic",
            "Suspension & Alignment",
            "Tyre and rims",
            "Car wiring and Diagnosis"
        )
    }
    val selectedMechanicCategories = remember { mutableStateListOf<String>() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun buildE164(dial: String, digitsOnly: String): String {
        val d = dial.trim().ifBlank { "+27" }
        var p = digitsOnly.filter { it.isDigit() }.take(15)
        if (p.startsWith("0")) p = p.drop(1)
        return (if (d.startsWith("+")) d else "+$d") + p
    }

    val phoneFull = remember(phoneDigits, dialCode) { buildE164(dialCode, phoneDigits) }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d -> birthday = "%04d-%02d-%02d".format(y, (m + 1), d) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun toggleCategory(cat: String) {
        if (selectedMechanicCategories.contains(cat)) selectedMechanicCategories.remove(cat)
        else selectedMechanicCategories.add(cat)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(35.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Mechanic Registration",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Country — greyed out
            OutlinedTextField(
                value = countryDisplay,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Country") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = Color(0xFF111827),
                    disabledLabelColor = Color(0xFF374151),
                    disabledBorderColor = Color(0xFF9CA3AF),
                    disabledContainerColor = Color(0xFFE5E7EB)
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ✅ Manual ID/Passport field
            TowMechTextField(
                idOrPassportNumber,
                { idOrPassportNumber = it },
                "ID/Passport Number",
                Modifier.fillMaxWidth(),
                KeyboardType.Text
            )

            Spacer(modifier = Modifier.height(14.dp))

            TowMechTextField(firstName, { firstName = it }, "First Name", Modifier.fillMaxWidth(), KeyboardType.Text)
            Spacer(modifier = Modifier.height(14.dp))

            TowMechTextField(lastName, { lastName = it }, "Last Name", Modifier.fillMaxWidth(), KeyboardType.Text)
            Spacer(modifier = Modifier.height(14.dp))

            // ✅ Phone — greyed out
            OutlinedTextField(
                value = phoneDigits.filter { it.isDigit() }.take(15),
                onValueChange = {},
                enabled = false,
                readOnly = true,
                label = { Text("Mobile number (without country code)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = Color(0xFF6B7280),
                    disabledLabelColor = Color(0xFF6B7280),
                    disabledBorderColor = darkBlue.copy(alpha = 0.25f),
                    disabledContainerColor = Color.White.copy(alpha = 0.75f)
                )
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Will be sent as: $phoneFull",
                fontSize = 12.sp,
                color = Color(0xFF374151)
            )

            Spacer(modifier = Modifier.height(10.dp))

            TowMechTextField(email, { email = it }, "Email", Modifier.fillMaxWidth(), KeyboardType.Email)
            Spacer(modifier = Modifier.height(14.dp))

            TowMechTextField(password, { password = it }, "Password", Modifier.fillMaxWidth(), KeyboardType.Password)
            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { openDatePicker() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text(
                    if (birthday.isBlank()) "Select Birthday" else "Birthday: $birthday",
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "Select Mechanic Categories (choose at least 1)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    mechanicCategoryOptions.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggleCategory(cat) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedMechanicCategories.contains(cat),
                                onCheckedChange = { toggleCategory(cat) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat, fontSize = 14.sp, color = dropdownTextColor)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "You will be required to SUBMIT a Clear Profile Picture and Huru Criminal record check",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Color.Red, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    if (
                        idOrPassportNumber.isBlank() ||
                        firstName.isBlank() || lastName.isBlank() || phoneDigits.isBlank() ||
                        email.isBlank() || password.isBlank() || birthday.isBlank()
                    ) {
                        errorMessage = "Please fill in all fields"
                        return@Button
                    }

                    if (selectedMechanicCategories.isEmpty()) {
                        errorMessage = "Please select at least 1 mechanic category"
                        return@Button
                    }

                    loading = true
                    errorMessage = ""

                    scope.launch {
                        try {
                            val cleanPassword = password.trim()
                            val fullPhone = phoneFull.trim()

                            ApiClient.apiService.register(
                                RegisterRequest(
                                    firstName = firstName.trim(),
                                    lastName = lastName.trim(),
                                    phone = fullPhone,
                                    email = email.trim(),
                                    password = cleanPassword,
                                    birthday = birthday,

                                    // ✅ satisfy backend + non-null Kotlin types
                                    nationalityType = "ForeignNational",
                                    saIdNumber = "",
                                    country = countryForBackend,
                                    passportNumber = idOrPassportNumber.trim(),

                                    role = "Mechanic",
                                    towTruckTypes = null,
                                    mechanicCategories = selectedMechanicCategories.toList(),
                                    countryCode = countryCode
                                )
                            )

                            ApiClient.apiService.login(
                                LoginRequest(
                                    phone = fullPhone,
                                    password = cleanPassword,
                                    countryCode = countryCode
                                )
                            )

                            loading = false
                            toast("Mechanic Registered ✅ OTP Sent to phone")
                            onOtpRequested(fullPhone)

                        } catch (e: HttpException) {
                            loading = false
                            val backendMsg = e.readErrorMessage()
                            errorMessage = backendMsg ?: "Registration failed. Please try again."
                        } catch (_: Exception) {
                            loading = false
                            errorMessage = "Registration failed. Please check your connection."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007A3D))
            ) {
                Text(
                    if (loading) "Loading..." else "Register",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(15.dp))

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = darkBlue)
            ) {
                Text("Back", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(25.dp))
        }
    }
}