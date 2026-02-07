// app/src/main/java/com/towmech/app/screens/CountryStartScreen.kt
package com.towmech.app.screens

import android.content.Context
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towmech.app.BuildConfig
import com.towmech.app.api.ApiClient
import com.towmech.app.api.CountryDto
import com.towmech.app.api.SendCountryOtpRequest
import com.towmech.app.api.readErrorMessage
import com.towmech.app.data.TokenManager
import com.towmech.app.ui.TowMechTextField
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryStartScreen(
    onOtpRequested: (fullPhone: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var countries by remember { mutableStateOf<List<CountryDto>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }

    var language by remember { mutableStateOf("en") }
    var phoneDigits by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun dialOf(c: CountryDto): String {
        val d = c.dialCode ?: c.dialingCode ?: ""
        return if (d.startsWith("+")) d else if (d.isNotBlank()) "+$d" else ""
    }

    fun flagEmoji(iso: String?): String {
        val cc = iso?.trim()?.uppercase().orEmpty()
        if (cc.length != 2) return "🏳️"
        val first = Character.codePointAt(cc, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(cc, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun displayCountry(c: CountryDto): String {
        val name = c.name?.trim().orEmpty()
        val code = c.code?.trim()?.uppercase().orEmpty()
        val dial = dialOf(c)
        val flag = flagEmoji(code)
        return "$flag  $name  ($code)  $dial"
    }

    fun getBestCountryIso(ctx: Context): String {
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val net = tm.networkCountryIso?.trim().orEmpty()
            if (net.isNotBlank()) return net.uppercase()

            val sim = tm.simCountryIso?.trim().orEmpty()
            if (sim.isNotBlank()) return sim.uppercase()

            Locale.getDefault().country.trim().uppercase().ifBlank { "ZA" }
        } catch (_: Exception) {
            Locale.getDefault().country.trim().uppercase().ifBlank { "ZA" }
        }
    }

    fun safeCountries(list: List<CountryDto>): List<CountryDto> {
        return list
            .filter { (it.isActive ?: true) && !it.code.isNullOrBlank() && !it.name.isNullOrBlank() }
            .sortedBy { it.name?.lowercase() ?: "" }
    }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val res = ApiClient.apiService.getCountries()
            val list = safeCountries(res.countries ?: emptyList())
            countries = list

            val saved = TokenManager.getCountryCode(context)
            val bestIso = getBestCountryIso(context)

            val idxSaved = list.indexOfFirst { it.code.equals(saved, true) }
            val idxIso = list.indexOfFirst { it.code.equals(bestIso, true) }

            selectedIndex = when {
                idxSaved >= 0 -> idxSaved
                idxIso >= 0 -> idxIso
                else -> 0
            }

            language = TokenManager.getLanguageCode(context) ?: "en"
            phoneDigits = (TokenManager.getLastPhoneDigits(context) ?: "")
                .filter { it.isDigit() }
                .take(15)

            val selected = list.getOrNull(selectedIndex)
            if (TokenManager.getLanguageCode(context).isNullOrBlank() &&
                selected?.defaultLanguage?.isNotBlank() == true
            ) {
                language = selected.defaultLanguage.trim().lowercase()
            }

        } catch (e: HttpException) {
            error = e.readErrorMessage() ?: "Failed to load countries (${e.code()})"
        } catch (e: Exception) {
            error = "Failed to load countries: ${e.message ?: "unknown error"}"
        } finally {
            loading = false
        }
    }

    val selectedCountry = countries.getOrNull(selectedIndex)
    val selectedDial = selectedCountry?.let { dialOf(it) }.orEmpty()
    val selectedCode = selectedCountry?.code?.trim()?.uppercase().orEmpty()
    val selectedFlag = flagEmoji(selectedCode)
    val selectedName = selectedCountry?.name?.trim().orEmpty()

    val supportedLangs =
        selectedCountry?.supportedLanguages?.takeIf { !it.isNullOrEmpty() } ?: listOf("en")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Select country", style = MaterialTheme.typography.headlineSmall)

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (error != null) {
            Text(text = error ?: "", color = MaterialTheme.colorScheme.error)
        }

        if (countries.isEmpty()) {
            Text("No active countries found.")
            return@Column
        }

        if (selectedName.isNotBlank()) {
            Text(
                text = "Selected: $selectedFlag  $selectedName  ($selectedCode)  •  $selectedDial  •  $language",
                fontSize = 13.sp
            )
        }

        // ✅ Country dropdown
        var countryExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = countryExpanded,
            onExpandedChange = { countryExpanded = !countryExpanded }
        ) {
            OutlinedTextField(
                value = selectedCountry?.let { displayCountry(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Country") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = countryExpanded,
                onDismissRequest = { countryExpanded = false }
            ) {
                countries.forEachIndexed { idx, c ->
                    DropdownMenuItem(
                        text = { Text(displayCountry(c)) },
                        onClick = {
                            selectedIndex = idx
                            countryExpanded = false

                            val def = c.defaultLanguage?.trim()?.takeIf { it.isNotBlank() } ?: "en"
                            language = def.lowercase()
                        }
                    )
                }
            }
        }

        // ✅ Language dropdown
        var langExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = langExpanded,
            onExpandedChange = { langExpanded = !langExpanded }
        ) {
            OutlinedTextField(
                value = language,
                onValueChange = {},
                readOnly = true,
                label = { Text("Language") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = langExpanded,
                onDismissRequest = { langExpanded = false }
            ) {
                supportedLangs.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang) },
                        onClick = {
                            language = lang.trim().lowercase()
                            langExpanded = false
                        }
                    )
                }
            }
        }

        // ✅ Mobile number input
        TowMechTextField(
            value = phoneDigits,
            onValueChange = { input ->
                phoneDigits = input.filter { it.isDigit() }.take(15)
            },
            label = "Mobile Number ($selectedDial)",
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (loading) return@Button

                val code = selectedCountry?.code?.trim()?.uppercase().orEmpty()
                val dial = selectedDial.trim()
                val lang = language.trim().lowercase()
                val digitsRaw = phoneDigits.filter { it.isDigit() }.take(15)

                if (code.isBlank() || dial.isBlank()) {
                    toast("Please select a valid country")
                    return@Button
                }

                if (digitsRaw.length < 7) {
                    toast("Enter a valid phone number")
                    return@Button
                }

                // ✅ Normalize for E.164: drop leading 0 after country dial
                val digitsNormalized = digitsRaw.removePrefix("0")
                val fullPhone = (dial + digitsNormalized).replace(" ", "")

                loading = true
                error = null

                scope.launch {
                    try {
                        toast("Server: ${BuildConfig.BASE_URL}")

                        ApiClient.apiService.sendCountryOtp(
                            SendCountryOtpRequest(
                                phone = fullPhone,
                                countryCode = code,
                                language = lang
                            )
                        )

                        // ✅ Save for routing headers on next screens
                        TokenManager.saveCountry(context, code, dial, lang)
                        TokenManager.saveLastPhoneDigits(context, digitsRaw)

                        toast("OTP Sent ✅")
                        onOtpRequested(fullPhone)

                    } catch (e: HttpException) {
                        error = e.readErrorMessage() ?: "Failed to send OTP (${e.code()})"
                    } catch (e: Exception) {
                        error = "Network error: ${e.message ?: "unknown"}"
                    } finally {
                        loading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(if (loading) "Sending..." else "Continue")
        }
    }
}