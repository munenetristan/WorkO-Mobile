// app/src/main/java/com/towmech/app/screens/RequestServiceScreen.kt
package com.towmech.app.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.ValidateInsuranceCodeRequest
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.launch
import retrofit2.HttpException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestServiceScreen(
    onBack: () -> Unit,
    onTowTruckSelected: () -> Unit,
    onMechanicSelected: () -> Unit,

    /**
     * ✅ include partnerId so backend can validate strict partner match
     */
    onTowTruckSelectedWithInsurance: ((insuranceEnabled: Boolean, insuranceCode: String, insurancePartnerId: String) -> Unit)? = null,
    onMechanicSelectedWithInsurance: ((insuranceEnabled: Boolean, insuranceCode: String, insurancePartnerId: String) -> Unit)? = null,

    // ✅ backward compatibility
    towingEnabled: Boolean = true,
    mechanicEnabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // ✅ FIX: create once at composable scope

    var loadingConfig by remember { mutableStateOf(true) }

    // ✅ Final flags actually used by UI
    var towingEnabledFinal by remember { mutableStateOf(towingEnabled) }
    var mechanicEnabledFinal by remember { mutableStateOf(mechanicEnabled) }

    // ✅ INSURANCE UI STATE
    var insuranceChecked by remember { mutableStateOf(false) }
    var insuranceCode by remember { mutableStateOf("") }

    // ✅ Insurance partners
    var loadingPartners by remember { mutableStateOf(false) }
    var partnersError by remember { mutableStateOf<String?>(null) }
    var partners by remember { mutableStateOf<List<InsurancePartnerUi>>(emptyList()) }
    var selectedPartnerId by remember { mutableStateOf("") }

    // ✅ Validation state
    var validatingCode by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf<String?>(null) }

    // ✅ Insurance availability (hide checkbox if disabled for country)
    var insuranceAvailable by remember { mutableStateOf(true) }

    val countryCode = remember {
        (TokenManager.getCountryCode(context) ?: "ZA").trim().uppercase()
    }

    // ✅ Load config (dashboard flags) — keep intact, no insurance flag references (avoids build errors)
    LaunchedEffect(Unit) {
        loadingConfig = true
        try {
            val config = ApiClient.apiService.getAppConfig()
            val svc = config.services
            val nested = svc?.services

            towingEnabledFinal =
                nested?.towingEnabled
                    ?: svc?.towingEnabled
                            ?: svc?.towing
                            ?: towingEnabled

            mechanicEnabledFinal =
                nested?.mechanicEnabled
                    ?: svc?.mechanicEnabled
                            ?: svc?.mechanic
                            ?: mechanicEnabled
        } catch (_: Exception) {
            towingEnabledFinal = towingEnabled
            mechanicEnabledFinal = mechanicEnabled
        } finally {
            loadingConfig = false
        }
    }

    /**
     * ✅ Pre-check insurance availability so we can HIDE insurance if disabled for the country.
     * If server responds 403/404 -> treat as disabled.
     * If general failure -> keep visible (so we don't hide due to network issues).
     */
    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.apiService.getInsurancePartners(
                countryCode = countryCode,
                xCountryCode = countryCode
            )
            val list = (res.partners ?: emptyList()).filter { it.isActive != false }
            insuranceAvailable = list.isNotEmpty()
        } catch (e: Exception) {
            insuranceAvailable = when (e) {
                is HttpException -> e.code() != 403 && e.code() != 404
                else -> true
            }
        }
    }

    /**
     * ✅ Load insurance partners
     */
    suspend fun fetchInsurancePartners() {
        loadingPartners = true
        partnersError = null
        try {
            val res = ApiClient.apiService.getInsurancePartners(
                countryCode = countryCode,
                xCountryCode = countryCode
            )

            val list = (res.partners ?: emptyList())
                .filter { it.isActive != false }
                .map {
                    InsurancePartnerUi(
                        id = it._id ?: "",
                        name = it.name ?: "Partner",
                        partnerCode = it.partnerCode ?: ""
                    )
                }
                .filter { it.id.isNotBlank() }
                .sortedBy { it.name.lowercase() }

            partners = list

            if (selectedPartnerId.isBlank() && list.isNotEmpty()) {
                selectedPartnerId = list.first().id
            }

            if (list.isEmpty()) {
                partnersError = "No insurance partners available for your country."
            }
        } catch (e: Exception) {
            partners = emptyList()
            partnersError = "Unable to load insurance partners right now. Please try again."
        } finally {
            loadingPartners = false
        }
    }

    // ✅ Only try loading partners when checkbox is enabled
    LaunchedEffect(insuranceChecked) {
        codeError = null
        if (insuranceChecked) {
            fetchInsurancePartners()
        } else {
            partnersError = null
            partners = emptyList()
            selectedPartnerId = ""
            insuranceCode = ""
        }
    }

    fun friendlyInsuranceError(raw: String?): String {
        if (raw.isNullOrBlank()) return "Insurance code is not valid. Please try again."
        val msg = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
        val cleaned = (msg ?: raw).trim()

        return when {
            cleaned.contains("expired", ignoreCase = true) ->
                "That insurance code has expired. Please request a new one."
            cleaned.contains("invalid", ignoreCase = true) ->
                "That insurance code is invalid. Please check and try again."
            else ->
                "Insurance code could not be verified. Please try again."
        }
    }

    fun basicInsuranceInputsOk(): Boolean {
        if (!insuranceChecked) return true

        val code = insuranceCode.trim()
        if (code.isEmpty()) {
            Toast.makeText(context, "Enter Insurance Code", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedPartnerId.isBlank()) {
            Toast.makeText(context, "Select an Insurance Partner", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    suspend fun validateInsuranceOrShowError(): Boolean {
        if (!insuranceChecked) return true

        validatingCode = true
        codeError = null

        return try {
            val res = ApiClient.apiService.validateInsuranceCode(
                xCountryCode = countryCode,
                request = ValidateInsuranceCodeRequest(
                    partnerId = selectedPartnerId,
                    code = insuranceCode.trim(),
                    countryCode = countryCode
                )
            )

            val ok = res.ok == true
            if (!ok) {
                codeError = friendlyInsuranceError(res.message ?: "Invalid code")
            }
            ok
        } catch (e: Exception) {
            val raw = e.message ?: "Unable to verify"
            codeError = friendlyInsuranceError(raw)
            false
        } finally {
            validatingCode = false
        }
    }

    // ✅ Stronger contrast (black text)
    val textBlack = Color(0xFF111827)
    val hintGrey = Color(0xFF374151)
    val errorRed = Color(0xFFB91C1C)

    // ✅ Button colors
    val towYellow = Color(0xFFFACC15)
    val mechanicRed = Color(0xFFEF4444)
    val backBlue = Color(0xFF2563EB)

    // ✅ Reduce size by 25% (60dp -> 45dp)
    val mainButtonHeight: Dp = 45.dp

    val outlinedColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textBlack,
        unfocusedTextColor = textBlack,
        disabledTextColor = textBlack.copy(alpha = 0.6f),
        focusedLabelColor = textBlack,
        unfocusedLabelColor = textBlack,
        focusedBorderColor = Color(0xFF2563EB),
        unfocusedBorderColor = Color(0xFF9CA3AF),
        cursorColor = Color(0xFF2563EB),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "TowMech Logo",
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Service Type",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textBlack
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (loadingConfig) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Loading services...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = hintGrey
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (insuranceAvailable) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = insuranceChecked,
                                    onCheckedChange = { insuranceChecked = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Insurance",
                                    fontWeight = FontWeight.Bold,
                                    color = textBlack
                                )
                            }

                            if (insuranceChecked) {
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Insurance Partner",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textBlack,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                if (loadingPartners) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Loading partners...", color = hintGrey)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (partners.isNotEmpty()) {
                                    PartnerDropdown(
                                        partners = partners,
                                        selectedId = selectedPartnerId,
                                        onSelect = { selectedPartnerId = it }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = insuranceCode,
                                    onValueChange = {
                                        insuranceCode = it
                                        codeError = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Enter Insurance Code") },
                                    colors = outlinedColors
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "If valid, booking fee will be skipped.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = hintGrey
                                )

                                if (!partnersError.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = partnersError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = errorRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (!codeError.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = codeError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = errorRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (validatingCode) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Verifying insurance code...", color = hintGrey)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ServiceButtonWithDisabledBadge(
                    title = "Tow Truck",
                    enabled = towingEnabledFinal && !validatingCode,
                    height = mainButtonHeight,
                    containerColor = towYellow,
                    contentColor = textBlack,
                    onClick = {
                        if (!basicInsuranceInputsOk()) return@ServiceButtonWithDisabledBadge

                        scope.launch {
                            val ok = validateInsuranceOrShowError()
                            if (!ok) return@launch

                            if (onTowTruckSelectedWithInsurance != null) {
                                onTowTruckSelectedWithInsurance(
                                    insuranceChecked,
                                    insuranceCode.trim(),
                                    selectedPartnerId
                                )
                            } else {
                                onTowTruckSelected()
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                ServiceButtonWithDisabledBadge(
                    title = "Mechanic",
                    enabled = mechanicEnabledFinal && !validatingCode,
                    height = mainButtonHeight,
                    containerColor = mechanicRed,
                    contentColor = Color.White,
                    onClick = {
                        if (!basicInsuranceInputsOk()) return@ServiceButtonWithDisabledBadge

                        scope.launch {
                            val ok = validateInsuranceOrShowError()
                            if (!ok) return@launch

                            if (onMechanicSelectedWithInsurance != null) {
                                onMechanicSelectedWithInsurance(
                                    insuranceChecked,
                                    insuranceCode.trim(),
                                    selectedPartnerId
                                )
                            } else {
                                onMechanicSelected()
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!towingEnabledFinal || !mechanicEnabledFinal) {
                    Text(
                        text = "Some services are disabled for your country.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = hintGrey
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(mainButtonHeight),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = backBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Back", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "Killian Digital Solutions © 2025",
                style = MaterialTheme.typography.bodySmall,
                color = hintGrey,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
    }
}

private data class InsurancePartnerUi(
    val id: String,
    val name: String,
    val partnerCode: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartnerDropdown(
    partners: List<InsurancePartnerUi>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    val textBlack = Color(0xFF111827)
    val borderGrey = Color(0xFF9CA3AF)

    var expanded by remember { mutableStateOf(false) }
    val selected = partners.firstOrNull { it.id == selectedId } ?: partners.firstOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected?.let {
                if (it.partnerCode.isNotBlank()) "${it.name} (${it.partnerCode})" else it.name
            } ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Select Partner", color = textBlack) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textBlack,
                unfocusedTextColor = textBlack,
                focusedLabelColor = textBlack,
                unfocusedLabelColor = textBlack,
                focusedBorderColor = borderGrey,
                unfocusedBorderColor = borderGrey,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            partners.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (p.partnerCode.isNotBlank()) "${p.name} (${p.partnerCode})" else p.name,
                            color = textBlack
                        )
                    },
                    onClick = {
                        onSelect(p.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ServiceButtonWithDisabledBadge(
    title: String,
    enabled: Boolean,
    height: Dp,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!enabled) {
                Text(
                    text = "Disabled",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}