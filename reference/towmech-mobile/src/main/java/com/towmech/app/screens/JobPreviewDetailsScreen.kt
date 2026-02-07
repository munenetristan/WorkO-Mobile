// JobPreviewDetailsScreen.kt
package com.towmech.app.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.CreateJobRequest
import com.towmech.app.api.JobPreviewRequest
import com.towmech.app.api.MechanicCategoryPricing
import com.towmech.app.api.TowTruckPricing
import com.towmech.app.api.InsurancePayload
import com.towmech.app.data.TokenManager
import com.towmech.app.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.HttpException

@Composable
fun JobPreviewDetailsScreen(
    navController: NavController,
    roleNeeded: String,
    onBack: () -> Unit,
    onProceedToPayment: (jobId: String, bookingFee: Int, currency: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)
    val red = Color(0xFFD32F2F)

    val isMechanicFlow = roleNeeded.trim().equals("Mechanic", ignoreCase = true)

    val saved = navController.previousBackStackEntry?.savedStateHandle

    val pickupLat = saved?.get<Double>("pickupLat")
    val pickupLng = saved?.get<Double>("pickupLng")
    val pickupText = saved?.get<String>("pickupText") ?: ""

    val dropoffLat = saved?.get<Double>("dropoffLat")
    val dropoffLng = saved?.get<Double>("dropoffLng")
    val dropoffText = saved?.get<String>("dropoffText") ?: ""

    val vehicleType = saved?.get<String>("vehicleType") ?: ""
    val pickupNotes = saved?.get<String>("pickupNotes") ?: ""

    val customerProblemDescription =
        saved?.get<String>("customerProblemDescription") ?: pickupNotes

    // ✅ Insurance (read from REQUEST_SERVICE savedStateHandle)
    val requestEntry = remember {
        runCatching { navController.getBackStackEntry(Routes.REQUEST_SERVICE) }.getOrNull()
    }

    val insuranceEnabledRaw: Boolean =
        requestEntry?.savedStateHandle?.get<Boolean>("insuranceEnabled") ?: false

    val insuranceCodeRaw: String =
        requestEntry?.savedStateHandle?.get<String>("insuranceCode") ?: ""

    // ✅ IMPORTANT: partnerId MUST be passed to backend when insurance is enabled
    // Try common keys to avoid breaking older screens.
    val insurancePartnerIdRaw: String =
        requestEntry?.savedStateHandle?.get<String>("insurancePartnerId")
            ?: requestEntry?.savedStateHandle?.get<String>("partnerId")
            ?: ""

    // ✅ Only treat insurance as enabled if ALL required fields exist
    val insuranceEnabled =
        insuranceEnabledRaw && insuranceCodeRaw.isNotBlank() && insurancePartnerIdRaw.isNotBlank()

    val insuranceCode = insuranceCodeRaw.trim()
    val insurancePartnerId = insurancePartnerIdRaw.trim()

    // ✅ Build insurance payload (or null)
    val insurancePayload: InsurancePayload? =
        if (insuranceEnabled) {
            InsurancePayload(
                enabled = true,
                code = insuranceCode,
                partnerId = insurancePartnerId
            )
        } else {
            null
        }

    if (insuranceEnabledRaw && insuranceCodeRaw.isNotBlank() && insurancePartnerIdRaw.isBlank()) {
        // This explains exactly the error you're seeing, without breaking flow.
        LaunchedEffect(Unit) {
            Toast.makeText(
                context,
                "Insurance partnerId missing (please re-select insurance partner/code)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (pickupLat == null || pickupLng == null) {
        Toast.makeText(context, "Pickup missing ❌", Toast.LENGTH_LONG).show()
        return
    }

    val pickupLatLng = LatLng(pickupLat, pickupLng)
    val dropoffLatLng =
        if (dropoffLat != null && dropoffLng != null) LatLng(dropoffLat, dropoffLng) else null

    var resultsByTowTruckType by remember { mutableStateOf<Map<String, TowTruckPricing>>(emptyMap()) }
    var selectedTowTruckType by remember { mutableStateOf("") }

    var mechanicCategories by remember { mutableStateOf<List<String>>(emptyList()) }
    var mechanicCategoryPricing by remember { mutableStateOf<Map<String, MechanicCategoryPricing>>(emptyMap()) }
    var selectedMechanicCategory by remember { mutableStateOf("") }

    var mechanicBookingFeeFallback by remember { mutableStateOf(200) }
    var currency by remember { mutableStateOf("ZAR") }

    var mechanicOnlineCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loadingCounts by remember { mutableStateOf(false) }

    var mechanicBookingFeesByCategory by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var mechanicCurrencyByCategory by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    var showMechanicDialog by remember { mutableStateOf(false) }

    val cameraState = rememberCameraPositionState()
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    val mechanicDisclaimerText =
        "Final fee is NOT predetermined. The mechanic will diagnose and agree the final price after pairing."

    fun isTowTruckTypeOnline(data: TowTruckPricing): Boolean {
        val count = data.providersCount
        val status = data.status.trim().uppercase()
        return (count > 0) || status == "ONLINE"
    }

    fun computeMechanicBookingFeeFallback(category: String): Int {
        val pricing = mechanicCategoryPricing[category]
        val base = (pricing?.baseFee ?: 0.0)
        val night = (pricing?.nightFee ?: 0.0)
        val weekend = (pricing?.weekendFee ?: 0.0)
        val total = base + night + weekend
        return if (total > 0) total.toInt() else mechanicBookingFeeFallback
    }

    fun getMechanicBookingFee(cat: String): Int {
        val fromPreview = mechanicBookingFeesByCategory[cat]
        if (fromPreview != null && fromPreview > 0) return fromPreview
        return computeMechanicBookingFeeFallback(cat)
    }

    fun getMechanicCurrency(cat: String): String {
        val fromPreview = mechanicCurrencyByCategory[cat]
        if (!fromPreview.isNullOrBlank()) return fromPreview
        return currency
    }

    fun parseHttpException(e: HttpException): String {
        return try {
            val body = e.response()?.errorBody()?.string()
            "HTTP ${e.code()} - ${body ?: "No error body"}"
        } catch (_: Exception) {
            "HTTP ${e.code()} - Failed to read error body"
        }
    }

    suspend fun fetchMechanicCategoryPreviewData(
        token: String,
        categories: List<String>
    ): Triple<Map<String, Int>, Map<String, Int>, Map<String, String>> {
        val counts = mutableMapOf<String, Int>()
        val fees = mutableMapOf<String, Int>()
        val currencies = mutableMapOf<String, String>()

        for (cat in categories) {
            try {
                val previewRes = ApiClient.apiService.previewJob(
                    token = "Bearer $token",
                    request = JobPreviewRequest(
                        title = "TowMech Service",
                        description = customerProblemDescription?.trim(),
                        roleNeeded = roleNeeded,
                        pickupLat = pickupLat,
                        pickupLng = pickupLng,
                        pickupAddressText = pickupText,
                        dropoffLat = null,
                        dropoffLng = null,
                        dropoffAddressText = null,
                        towTruckTypeNeeded = null,
                        vehicleType = null,
                        mechanicCategoryNeeded = cat,
                        mechanicCategory = cat,
                        customerProblemDescription = customerProblemDescription?.trim(),
                        insurance = insurancePayload
                    )
                )

                counts[cat] = previewRes.providerCount

                val feeFromPreview = previewRes.preview?.bookingFee?.toInt() ?: 0
                if (feeFromPreview > 0) fees[cat] = feeFromPreview

                val curFromPreview = previewRes.preview?.currency ?: ""
                if (curFromPreview.isNotBlank()) currencies[cat] = curFromPreview

            } catch (_: Exception) {
                counts[cat] = 0
            }
        }

        return Triple(counts, fees, currencies)
    }

    LaunchedEffect(Unit) {
        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "Login required"
                loading = false
                return@LaunchedEffect
            }

            if (!isMechanicFlow && dropoffLatLng != null) {
                val bounds = LatLngBounds.builder()
                    .include(pickupLatLng)
                    .include(dropoffLatLng)
                    .build()

                cameraState.animate(
                    update = CameraUpdateFactory.newLatLngBounds(bounds, 120),
                    durationMs = 900
                )

                routePoints = getRoutePolyline(
                    apiKey = context.getString(R.string.google_maps_key),
                    origin = pickupLatLng,
                    destination = dropoffLatLng
                )
            } else {
                cameraState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f),
                    durationMs = 700
                )
            }

            val config = ApiClient.apiService.getAppConfig()

            val categoriesFromOld = config.mechanicCategories ?: emptyList()
            val categoriesFromNew = config.pricing?.mechanicCategories ?: emptyList()
            mechanicCategories =
                if (categoriesFromOld.isNotEmpty()) categoriesFromOld else categoriesFromNew

            currency = config.pricing?.currency ?: "ZAR"

            mechanicBookingFeeFallback =
                (config.pricing?.bookingFees?.mechanicFixed ?: 200.0).toInt()

            mechanicCategoryPricing =
                config.pricing?.mechanicCategoryPricing ?: emptyMap()

            if (selectedMechanicCategory.isBlank()) {
                selectedMechanicCategory = mechanicCategories.firstOrNull() ?: ""
            }

            if (isMechanicFlow) {
                loadingCounts = true
                val (counts, fees, currencies) =
                    fetchMechanicCategoryPreviewData(token, mechanicCategories)

                mechanicOnlineCounts = counts
                mechanicBookingFeesByCategory = fees
                mechanicCurrencyByCategory = currencies

                val firstOnline = mechanicCategories.firstOrNull { (mechanicOnlineCounts[it] ?: 0) > 0 }
                if (!firstOnline.isNullOrBlank()) {
                    selectedMechanicCategory = firstOnline
                }

                loadingCounts = false
            } else {
                val previewRes = ApiClient.apiService.previewJob(
                    token = "Bearer $token",
                    request = JobPreviewRequest(
                        title = "TowMech Service",
                        description = pickupNotes.trim(),
                        roleNeeded = roleNeeded,
                        pickupLat = pickupLat,
                        pickupLng = pickupLng,
                        pickupAddressText = pickupText,
                        dropoffLat = dropoffLat,
                        dropoffLng = dropoffLng,
                        dropoffAddressText = dropoffText,
                        towTruckTypeNeeded = null,
                        vehicleType = vehicleType,
                        insurance = insurancePayload
                    )
                )

                val pricingMap = previewRes.preview?.resultsByTowTruckType ?: emptyMap()
                resultsByTowTruckType = pricingMap
                val firstOnline = pricingMap.entries.firstOrNull { isTowTruckTypeOnline(it.value) }
                selectedTowTruckType = firstOnline?.key ?: ""
            }

        } catch (e: HttpException) {
            e.printStackTrace()
            errorMessage = parseHttpException(e)
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Failed to load preview: ${e.localizedMessage ?: e.toString()}"
        }

        loading = false
    }

    if (showMechanicDialog) {
        AlertDialog(
            onDismissRequest = { showMechanicDialog = false },
            title = { Text("Important") },
            text = { Text(mechanicDisclaimerText) },
            confirmButton = {
                Button(
                    onClick = {
                        showMechanicDialog = false
                        scope.launch {
                            try {
                                val token = TokenManager.getToken(context)
                                if (token.isNullOrBlank()) {
                                    Toast.makeText(context, "Login required", Toast.LENGTH_LONG).show()
                                    return@launch
                                }

                                val jobRes = ApiClient.apiService.createJob(
                                    token = "Bearer $token",
                                    request = CreateJobRequest(
                                        title = "TowMech Service",
                                        description = customerProblemDescription?.trim(),
                                        roleNeeded = roleNeeded,
                                        pickupLat = pickupLat,
                                        pickupLng = pickupLng,
                                        pickupAddressText = pickupText,
                                        dropoffLat = null,
                                        dropoffLng = null,
                                        dropoffAddressText = null,
                                        towTruckTypeNeeded = null,
                                        vehicleType = null,
                                        mechanicCategoryNeeded = selectedMechanicCategory,
                                        mechanicCategory = selectedMechanicCategory,
                                        customerProblemDescription = customerProblemDescription?.trim(),
                                        insurance = insurancePayload
                                    )
                                )

                                val jobId = jobRes.job?._id ?: ""

                                val feeFromJob = jobRes.job?.pricing?.bookingFee?.toInt() ?: 0
                                val curFromJob = jobRes.job?.pricing?.currency ?: ""

                                val finalBookingFee =
                                    if (feeFromJob > 0) feeFromJob else getMechanicBookingFee(selectedMechanicCategory)

                                val finalCurrency =
                                    if (curFromJob.isNotBlank()) curFromJob else getMechanicCurrency(selectedMechanicCategory)

                                if (jobId.isNotBlank()) {
                                    // ✅ Insurance: skip payment
                                    if (insuranceEnabled) {
                                        onProceedToPayment(jobId, 0, finalCurrency)
                                    } else {
                                        onProceedToPayment(jobId, finalBookingFee, finalCurrency)
                                    }
                                } else {
                                    Toast.makeText(context, "Job creation failed", Toast.LENGTH_LONG).show()
                                }

                            } catch (e: HttpException) {
                                e.printStackTrace()
                                Toast.makeText(context, parseHttpException(e), Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) { Text("Accept & Continue") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showMechanicDialog = false }) { Text("Cancel") }
            }
        )
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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = darkBlue)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Preview", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = darkBlue)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Pickup Location", fontWeight = FontWeight.Bold, color = darkBlue)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Pickup: $pickupText", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 12.dp)
                    .background(Color(0xFFDFF0D8), RoundedCornerShape(18.dp))
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraState
                ) {
                    Marker(state = MarkerState(position = pickupLatLng), title = "Pickup")
                    if (!isMechanicFlow && dropoffLatLng != null) {
                        Marker(state = MarkerState(position = dropoffLatLng), title = "Drop-off")
                    }
                    if (!isMechanicFlow && routePoints.isNotEmpty()) {
                        Polyline(points = routePoints, color = Color.Blue, width = 10f)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isMechanicFlow) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Important", fontWeight = FontWeight.Bold, color = darkBlue)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(mechanicDisclaimerText, fontSize = 13.sp, color = Color.DarkGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Mechanic Categories", fontWeight = FontWeight.Bold, color = darkBlue)
                        Spacer(modifier = Modifier.height(10.dp))

                        when {
                            loading -> CircularProgressIndicator()
                            errorMessage.isNotEmpty() -> Text(errorMessage, color = Color.Red)
                            mechanicCategories.isEmpty() -> Text("No mechanic categories returned ❌", color = Color.Red)
                            loadingCounts -> CircularProgressIndicator()
                            else -> {
                                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                    items(items = mechanicCategories, key = { it }) { cat ->
                                        val bookingFee = getMechanicBookingFee(cat)
                                        val catCurrency = getMechanicCurrency(cat)

                                        val count = mechanicOnlineCounts[cat] ?: 0
                                        val statusText = if (count > 0) "ONLINE" else "OFFLINE"
                                        val statusColor = if (count > 0) green else red

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .clickable { selectedMechanicCategory = cat },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor =
                                                    if (selectedMechanicCategory == cat) green.copy(alpha = 0.25f)
                                                    else Color.White
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(cat, fontWeight = FontWeight.SemiBold)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        "$statusText ($count)",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusColor
                                                    )
                                                }
                                                Text(
                                                    "$catCurrency $bookingFee",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Provider Types", fontWeight = FontWeight.Bold, color = darkBlue)
                        Spacer(modifier = Modifier.height(10.dp))

                        when {
                            loading -> CircularProgressIndicator()
                            errorMessage.isNotEmpty() -> Text(errorMessage, color = Color.Red)
                            resultsByTowTruckType.isEmpty() -> Text("No provider types returned ❌", color = Color.Red)
                            else -> {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(resultsByTowTruckType.entries.toList(), key = { it.key }) { entry ->
                                        val type = entry.key
                                        val data = entry.value
                                        val price = data.estimatedTotal
                                        val isOnline = isTowTruckTypeOnline(data)

                                        val statusText = if (isOnline) "ONLINE" else "OFFLINE"
                                        val statusColor = if (isOnline) green else red

                                        val cur = if (!data.currency.isNullOrBlank()) data.currency else currency

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .let { if (isOnline) it.clickable { selectedTowTruckType = type } else it },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor =
                                                    if (selectedTowTruckType == type) green.copy(alpha = 0.25f)
                                                    else Color.White
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(type, fontWeight = FontWeight.SemiBold)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        "$statusText (${data.providersCount})",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusColor
                                                    )
                                                }
                                                Text(
                                                    "$cur $price",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    if (isMechanicFlow) {
                        if (selectedMechanicCategory.isBlank()) {
                            Toast.makeText(context, "Select a mechanic category ❌", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val count = mechanicOnlineCounts[selectedMechanicCategory] ?: 0
                        if (count <= 0) {
                            Toast.makeText(context, "No providers online for this category ❌", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        showMechanicDialog = true
                        return@Button
                    }

                    if (selectedTowTruckType.isBlank()) {
                        Toast.makeText(context, "No ONLINE provider type available ❌", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val selected = resultsByTowTruckType[selectedTowTruckType]
                    val isOnline = selected?.let { isTowTruckTypeOnline(it) } ?: false

                    if (!isOnline) {
                        Toast.makeText(context, "Selected type is OFFLINE ❌", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    scope.launch {
                        try {
                            val token = TokenManager.getToken(context)
                            if (token.isNullOrBlank()) {
                                Toast.makeText(context, "Login required", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            val jobRes = ApiClient.apiService.createJob(
                                token = "Bearer $token",
                                request = CreateJobRequest(
                                    title = "TowMech Service",
                                    description = pickupNotes.trim(),
                                    roleNeeded = roleNeeded,
                                    pickupLat = pickupLat,
                                    pickupLng = pickupLng,
                                    pickupAddressText = pickupText,
                                    dropoffLat = dropoffLat,
                                    dropoffLng = dropoffLng,
                                    dropoffAddressText = dropoffText,
                                    towTruckTypeNeeded = selectedTowTruckType,
                                    vehicleType = vehicleType,
                                    insurance = insurancePayload
                                )
                            )

                            val jobId = jobRes.job?._id ?: ""
                            val bookingFee = jobRes.job?.pricing?.bookingFee?.toInt() ?: 0
                            val jobCurrency = jobRes.job?.pricing?.currency ?: currency

                            if (jobId.isNotBlank()) {
                                // ✅ Insurance: skip payment
                                if (insuranceEnabled) {
                                    onProceedToPayment(jobId, 0, jobCurrency)
                                } else {
                                    onProceedToPayment(jobId, bookingFee, jobCurrency)
                                }
                            } else {
                                Toast.makeText(context, "Job creation failed", Toast.LENGTH_LONG).show()
                            }

                        } catch (e: HttpException) {
                            e.printStackTrace()
                            Toast.makeText(context, parseHttpException(e), Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(35.dp),
                colors = ButtonDefaults.buttonColors(containerColor = green)
            ) {
                Text(
                    if (isMechanicFlow) "Select Category & Continue" else "Select & Continue",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(25.dp))
        }
    }
}

suspend fun getRoutePolyline(
    apiKey: String,
    origin: LatLng,
    destination: LatLng
): List<LatLng> = withContext(Dispatchers.IO) {

    val client = OkHttpClient()

    val url =
        "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=driving" +
                "&key=$apiKey"

    val request = Request.Builder().url(url).build()

    val response = client.newCall(request).execute()
    val body = response.body?.string() ?: return@withContext emptyList()

    val json = JSONObject(body)

    val routes = json.optJSONArray("routes") ?: return@withContext emptyList()
    if (routes.length() == 0) return@withContext emptyList()

    val overviewPolyline = routes.getJSONObject(0)
        .getJSONObject("overview_polyline")
        .getString("points")

    return@withContext decodePolyline(overviewPolyline)
}

fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)

        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)

        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val latLng = LatLng(lat / 1E5, lng / 1E5)
        poly.add(latLng)
    }

    return poly
}