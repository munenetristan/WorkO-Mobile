package com.towmech.app.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.JobResponse
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeCustomerScreen(
    onGoRequestService: () -> Unit,
    onOpenTracking: (String) -> Unit,
    onOpenChat: (jobId: String, lockStartIso: String?) -> Unit = { _, _ -> },

    // ✅ NEW (optional): show unread badge on the home chat button
    unreadCount: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val green = Color(0xFF007A3D)
    val black = Color(0xFF111111)

    var loadingTrack by remember { mutableStateOf(false) }

    // ✅ ongoing job state for buttons visibility
    var hasActiveJob by remember { mutableStateOf(false) }
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var activeJobLockedAt by remember { mutableStateOf<String?>(null) }

    // ✅ customer location display
    var locationText by remember { mutableStateOf("Location not available yet") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // ✅ Permission check (no launcher used here to avoid dependency issues)
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    // ✅ Fused Location Client (live updates)
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // ✅ Callback updates UI (NOW shows address instead of lat/lng)
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return

                try {
                    val geocoder = Geocoder(context)
                    val list = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    val addr = list?.firstOrNull()

                    locationText = if (addr != null) {
                        addr.getAddressLine(0)
                            ?: listOfNotNull(
                                addr.featureName,
                                addr.subLocality,
                                addr.locality,
                                addr.subAdminArea,
                                addr.adminArea,
                                addr.countryName
                            ).joinToString(", ")
                    } else {
                        "Location detected"
                    }
                } catch (_: Exception) {
                    locationText = "Location detected"
                }
            }
        }
    }

    // ✅ Start/stop live updates
    DisposableEffect(Unit) {
        if (hasLocationPermission()) {
            try {
                startLiveLocationUpdates(
                    fusedClient = fusedClient,
                    callback = locationCallback
                )
            } catch (_: SecurityException) {
                locationText = "Location permission not granted"
            }
        } else {
            locationText = "Location permission not granted"
        }

        onDispose {
            try {
                fusedClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {
            }
        }
    }

    fun findTrackableJobId(active: List<JobResponse>, history: List<JobResponse>): String? {
        active.firstOrNull()?.let { job ->
            val id = job._id
            if (!id.isNullOrBlank()) return id
        }

        val fallback = history.firstOrNull { job ->
            val s = (job.status ?: "").uppercase()
            s != "COMPLETED" && s != "CANCELLED"
        }

        return fallback?._id
    }

    // ✅ poll active job (controls Track/Chat buttons)
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    hasActiveJob = false
                    activeJobId = null
                    activeJobLockedAt = null
                } else {
                    val activeRes = ApiClient.apiService.getCustomerActiveJobs("Bearer $token")
                    val active = activeRes.jobs ?: emptyList()

                    val best = active.firstOrNull { (it.status ?: "").equals("IN_PROGRESS", true) }
                        ?: active.firstOrNull { (it.status ?: "").equals("ASSIGNED", true) }

                    if (best?._id.isNullOrBlank()) {
                        hasActiveJob = false
                        activeJobId = null
                        activeJobLockedAt = null
                    } else {
                        hasActiveJob = true
                        activeJobId = best._id
                        activeJobLockedAt =
                            best.lockedAt ?: best.assignedAt ?: best.acceptedAt ?: best.updatedAt ?: best.createdAt
                    }
                }
            } catch (_: Exception) {
                hasActiveJob = false
                activeJobId = null
                activeJobLockedAt = null
            }

            delay(5000)
        }
    }

    fun onTrackPressed() {
        scope.launch {
            try {
                loadingTrack = true

                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Please login again.")
                    loadingTrack = false
                    return@launch
                }

                val activeRes = ApiClient.apiService.getCustomerActiveJobs("Bearer $token")
                val historyRes = ApiClient.apiService.getCustomerJobHistory("Bearer $token")

                val active = activeRes.jobs ?: emptyList()
                val history = historyRes.jobs ?: emptyList()

                val jobId = findTrackableJobId(active, history)

                if (jobId.isNullOrBlank()) {
                    toast("No active job found.")
                    loadingTrack = false
                    return@launch
                }

                loadingTrack = false
                onOpenTracking(jobId)

            } catch (e: Exception) {
                loadingTrack = false
                toast("Failed to open tracking: ${e.message}")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.towmech_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(15.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(35.dp))

            Button(
                onClick = onGoRequestService,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = green)
            ) {
                Text(
                    text = "Request Service",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Current Location",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0033A0)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = locationText,
                        fontSize = 14.sp,
                        color = Color(0xFF0033A0)
                    )
                }
            }

            if (hasActiveJob && !activeJobId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { if (!loadingTrack) onTrackPressed() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    enabled = !loadingTrack
                ) {
                    Text(
                        text = if (loadingTrack) "Loading..." else "Track Current Job",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onOpenChat(activeJobId!!, activeJobLockedAt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = black)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chat Provider",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        if (unreadCount > 0) {
                            Surface(
                                color = Color.Red,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = R.drawable.towmech_hero),
                contentDescription = "Hero Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun startLiveLocationUpdates(
    fusedClient: FusedLocationProviderClient,
    callback: LocationCallback
) {
    // ✅ Old-style request (most compatible)
    val request = LocationRequest.create().apply {
        interval = 5000L
        fastestInterval = 2000L
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    fusedClient.requestLocationUpdates(
        request,
        callback,
        Looper.getMainLooper()
    )
}