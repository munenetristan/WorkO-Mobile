package com.towmech.app.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.ValidateInsuranceCodeRequest
import com.towmech.app.data.TokenManager
import com.towmech.app.navigation.Routes
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobPreviewScreen(
    navController: NavController,
    roleNeeded: String,
    onBack: () -> Unit,
    onProceedToPayment: (jobId: String, bookingFee: Int, currency: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    var vehicleTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedVehicleType by remember { mutableStateOf("") }
    var configLoading by remember { mutableStateOf(true) }

    var showVehicleSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val fallbackVehicles = listOf(
        "Sedan",
        "SUV",
        "Hatchback",
        "Bakkie",
        "Truck",
        "Bus"
    )

    // ✅ Insurance context passed from RequestServiceScreen via savedStateHandle
    val insuranceEnabledFromPrev: Boolean =
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.get<Boolean>("insuranceEnabled") ?: false

    val insuranceCodeFromPrev: String =
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.get<String>("insuranceCode")?.trim().orEmpty()

    // ✅ Partner ID is required for strict validation
    // If you haven't added it yet in AppNavGraph/RequestServiceScreen, this will be blank and we will block insurance usage.
    val insurancePartnerIdFromPrev: String =
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.get<String>("insurancePartnerId")?.trim().orEmpty()

    val countryCode: String = remember {
        (TokenManager.getCountryCode(context) ?: "ZA").trim().uppercase()
    }

    var insuranceValidating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.apiService.getAppConfig()
            Log.d("CONFIG_DEBUG", "✅ Config response: $res")

            // ✅ BACKWARD COMPAT: keep using vehicleTypes if present
            vehicleTypes = res.vehicleTypes

            if (vehicleTypes.isEmpty()) {
                vehicleTypes = fallbackVehicles
            }

            selectedVehicleType = vehicleTypes.first()

        } catch (e: Exception) {
            Log.e("CONFIG_DEBUG", "❌ Config fetch failed: ${e.message}")

            Toast.makeText(context, "Config failed. Using fallback types.", Toast.LENGTH_SHORT).show()

            vehicleTypes = fallbackVehicles
            selectedVehicleType = fallbackVehicles.first()
        }

        configLoading = false
    }

    var pickupText by remember { mutableStateOf("") }
    var pickupLat by remember { mutableStateOf<Double?>(null) }
    var pickupLng by remember { mutableStateOf<Double?>(null) }
    var pickupPredictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

    var dropoffText by remember { mutableStateOf("") }
    var dropoffLat by remember { mutableStateOf<Double?>(null) }
    var dropoffLng by remember { mutableStateOf<Double?>(null) }
    var dropoffPredictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }

    var pickupNotes by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val placesClient: PlacesClient = remember {
        if (!Places.isInitialized()) {
            Places.initialize(
                context,
                context.getString(R.string.google_maps_key),
                Locale.getDefault()
            )
        }
        Places.createClient(context)
    }

    suspend fun fetchPredictions(text: String, isPickup: Boolean) {
        if (text.isBlank()) {
            if (isPickup) pickupPredictions = emptyList()
            else dropoffPredictions = emptyList()
            return
        }

        try {
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(text)
                .build()

            val response = placesClient.findAutocompletePredictions(request).await()

            if (isPickup) pickupPredictions = response.autocompletePredictions
            else dropoffPredictions = response.autocompletePredictions

        } catch (_: Exception) {
            if (isPickup) pickupPredictions = emptyList()
            else dropoffPredictions = emptyList()
        }
    }

    suspend fun fetchPlaceDetails(placeId: String, isPickup: Boolean) {
        try {
            val fields = listOf(Place.Field.LAT_LNG, Place.Field.ADDRESS)
            val request = FetchPlaceRequest.builder(placeId, fields).build()
            val response = placesClient.fetchPlace(request).await()

            val latLng = response.place.latLng
            val address = response.place.address ?: "Selected location"

            if (latLng != null) {
                if (isPickup) {
                    pickupLat = latLng.latitude
                    pickupLng = latLng.longitude
                    pickupText = address
                    pickupPredictions = emptyList()
                } else {
                    dropoffLat = latLng.latitude
                    dropoffLng = latLng.longitude
                    dropoffText = address
                    dropoffPredictions = emptyList()
                }
            }
        } catch (_: Exception) {}
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (!granted) {
                Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    fun requestLocationPermission() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            requestLocationPermission()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                pickupLat = location.latitude
                pickupLng = location.longitude

                val geocoder = Geocoder(context, Locale.getDefault())
                val addr = try {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()?.getAddressLine(0)
                } catch (_: Exception) {
                    null
                } ?: "Current Location"

                pickupText = addr
                pickupPredictions = emptyList()

                Toast.makeText(context, "Pickup set ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun validateInsuranceIfNeededOrStop(): Boolean {
        if (!insuranceEnabledFromPrev) return true

        if (insuranceCodeFromPrev.isBlank()) {
            errorMessage = "Insurance code is required"
            return false
        }

        if (insurancePartnerIdFromPrev.isBlank()) {
            // This means upstream screen/nav didn't pass partnerId yet
            errorMessage = "Insurance Partner is required"
            Toast.makeText(context, "Select an Insurance Partner", Toast.LENGTH_SHORT).show()
            return false
        }

        insuranceValidating = true
        errorMessage = ""

        return try {
            val res = ApiClient.apiService.validateInsuranceCode(
                xCountryCode = countryCode,
                request = ValidateInsuranceCodeRequest(
                    partnerId = insurancePartnerIdFromPrev,
                    code = insuranceCodeFromPrev,
                    countryCode = countryCode
                )
            )

            val ok = res.ok == true
            if (!ok) {
                val msg = res.message ?: "Invalid insurance code"
                errorMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                false
            } else {
                // ✅ Store validation outcome for later screens (JobPreviewDetails / CreateJob)
                navController.currentBackStackEntry?.savedStateHandle?.set("insuranceValidated", true)
                navController.currentBackStackEntry?.savedStateHandle?.set("insuranceEnabled", true)
                navController.currentBackStackEntry?.savedStateHandle?.set("insuranceCode", insuranceCodeFromPrev)
                navController.currentBackStackEntry?.savedStateHandle?.set("insurancePartnerId", insurancePartnerIdFromPrev)
                true
            }
        } catch (e: Exception) {
            val msg = "Insurance validation failed"
            errorMessage = msg
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            false
        } finally {
            insuranceValidating = false
        }
    }

    if (showVehicleSheet) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showVehicleSheet = false }
        ) {
            Text(
                text = "Select Vehicle Type",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn {
                items(vehicleTypes) { type ->
                    ListItem(
                        headlineContent = { Text(type) },
                        modifier = Modifier.clickable {
                            selectedVehicleType = type
                            showVehicleSheet = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Request Service",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Select Vehicle Type", fontWeight = FontWeight.Bold, color = Color.White)

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {

                OutlinedTextField(
                    value = selectedVehicleType,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select your vehicle type") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(Icons.Default.Map, contentDescription = null, tint = green)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            if (vehicleTypes.isNotEmpty()) showVehicleSheet = true
                        }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = pickupText,
                onValueChange = {
                    pickupText = it
                    scope.launch { fetchPredictions(it, true) }
                },
                placeholder = { Text("Choose pickup location") },
                trailingIcon = {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = "Pick pickup on map",
                        tint = green,
                        modifier = Modifier.clickable {
                            navController.navigate("${Routes.PICK_LOCATION}/pickup")
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            if (pickupPredictions.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(pickupPredictions) { prediction ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { fetchPlaceDetails(prediction.placeId, true) }
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(prediction.getPrimaryText(null).toString(), fontWeight = FontWeight.Bold)
                                Text(prediction.getSecondaryText(null).toString())
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Use Current Location",
                color = green,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { fetchCurrentLocation() }
                    .padding(6.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (roleNeeded == "TowTruck") {
                OutlinedTextField(
                    value = dropoffText,
                    onValueChange = {
                        dropoffText = it
                        scope.launch { fetchPredictions(it, false) }
                    },
                    placeholder = { Text("Choose drop-off location") },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = "Pick dropoff on map",
                            tint = green,
                            modifier = Modifier.clickable {
                                navController.navigate("${Routes.PICK_LOCATION}/dropoff")
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )

                if (dropoffPredictions.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(dropoffPredictions) { prediction ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch { fetchPlaceDetails(prediction.placeId, false) }
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(prediction.getPrimaryText(null).toString(), fontWeight = FontWeight.Bold)
                                    Text(prediction.getSecondaryText(null).toString())
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = pickupNotes,
                onValueChange = { pickupNotes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pickup Notes (Optional)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Color.Red, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    if (selectedVehicleType.isBlank()) {
                        errorMessage = "Vehicle type is required"
                        return@Button
                    }
                    if (pickupLat == null || pickupLng == null) {
                        errorMessage = "Pickup location is required"
                        return@Button
                    }
                    if (roleNeeded == "TowTruck" && (dropoffLat == null || dropoffLng == null)) {
                        errorMessage = "Drop-off location is required"
                        return@Button
                    }

                    // ✅ Validate insurance BEFORE proceeding
                    scope.launch {
                        val ok = validateInsuranceIfNeededOrStop()
                        if (!ok) return@launch

                        navController.currentBackStackEntry?.savedStateHandle?.set("pickupLat", pickupLat!!)
                        navController.currentBackStackEntry?.savedStateHandle?.set("pickupLng", pickupLng!!)
                        navController.currentBackStackEntry?.savedStateHandle?.set("pickupText", pickupText)

                        navController.currentBackStackEntry?.savedStateHandle?.set("dropoffLat", dropoffLat)
                        navController.currentBackStackEntry?.savedStateHandle?.set("dropoffLng", dropoffLng)
                        navController.currentBackStackEntry?.savedStateHandle?.set("dropoffText", dropoffText)

                        navController.currentBackStackEntry?.savedStateHandle?.set("vehicleType", selectedVehicleType)
                        navController.currentBackStackEntry?.savedStateHandle?.set("pickupNotes", pickupNotes)

                        navController.navigate("${Routes.JOB_PREVIEW_DETAILS}/$roleNeeded")
                    }
                },
                enabled = !insuranceValidating && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = green)
            ) {
                if (insuranceValidating) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Validating...", fontWeight = FontWeight.Bold, color = Color.White)
                } else {
                    Text("Continue", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}