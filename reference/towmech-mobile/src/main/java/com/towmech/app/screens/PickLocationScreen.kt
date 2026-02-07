package com.towmech.app.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.towmech.app.R
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun PickLocationScreen(
    type: String,
    onBack: () -> Unit,
    onLocationPicked: (Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)

    // ✅ Current selected location state
    var selectedLatLng by remember { mutableStateOf(LatLng(-23.9045, 29.4689)) }
    var selectedAddress by remember { mutableStateOf("Move map to choose location") }

    var isLoading by remember { mutableStateOf(true) }

    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // ✅ Camera State
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLatLng, 16f)
    }

    // ✅ Permission launcher
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (!granted) {
                Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            } else {
                fetchCurrentLocation(fusedClient) { latLng ->
                    selectedLatLng = latLng
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 16f)
                }
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
    fun setToCurrentLocation() {
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

        fetchCurrentLocation(fusedClient) { latLng ->
            selectedLatLng = latLng
            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 16f)
        }
    }

    fun reverseGeocode(latLng: LatLng) {
        scope.launch {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)

                selectedAddress = address ?: "Unknown location"
            } catch (e: Exception) {
                selectedAddress = "Unable to fetch address"
            }
        }
    }

    LaunchedEffect(Unit) {
        setToCurrentLocation()
        isLoading = false
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            selectedLatLng = cameraPositionState.position.target
            reverseGeocode(selectedLatLng)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ✅ Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            )
        )

        // ✅ TOP CARD (Bolt style)
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {

                // ✅ CLOSE (X)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = darkBlue)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (type == "pickup") "Pickup location" else "Dropoff location",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = selectedAddress,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                )
            }
        }

        // ✅ CENTER PIN ICON
        Image(
            painter = painterResource(id = R.drawable.map_pin),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(52.dp)
        )

        // ✅ CURRENT LOCATION BUTTON
        FloatingActionButton(
            onClick = { setToCurrentLocation() },
            containerColor = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .offset(y = (-110).dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null, tint = darkBlue)
        }

        // ✅ SEARCH BUTTON (still placeholder)
        FloatingActionButton(
            onClick = {
                Toast.makeText(context, "Search coming next step ✅", Toast.LENGTH_SHORT).show()
            },
            containerColor = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .offset(y = (-175).dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = darkBlue)
        }

        // ✅ BOTTOM CONFIRM CARD
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Button(
                    onClick = {
                        onLocationPicked(
                            selectedLatLng.latitude,
                            selectedLatLng.longitude,
                            selectedAddress
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green)
                ) {
                    Text(
                        text = "Confirm destination",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // ✅ Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/**
 * ✅ Helper: Fetch user location safely
 */
@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
    fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocation: (LatLng) -> Unit
) {
    fusedClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            onLocation(LatLng(location.latitude, location.longitude))
        }
    }
}