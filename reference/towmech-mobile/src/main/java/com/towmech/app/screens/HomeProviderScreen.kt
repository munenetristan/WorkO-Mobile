package com.towmech.app.screens

import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.JobResponse
import com.towmech.app.api.ProviderStatusRequest
import com.towmech.app.data.TokenManager
import com.towmech.app.notifications.ProviderForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale
import kotlin.coroutines.resume

@Composable
fun HomeProviderScreen(
    onOpenTracking: (String) -> Unit = {},
    onOpenChat: (jobId: String, lockStartIso: String?) -> Unit = { _, _ -> },

    // ✅ NEW (optional): unread badge for chat button
    unreadCount: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF1B8F3A) // ONLINE
    val red = Color(0xFFC62828)   // OFFLINE
    val black = Color(0xFF111111)

    var isOnline by remember { mutableStateOf(ProviderStatusPrefs.getIsOnline(context)) }
    val currentToggleColor = if (isOnline) green else red

    var hasLocationPermission by remember { mutableStateOf(false) }
    var isGpsOn by remember { mutableStateOf(isGpsEnabled(context)) }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var addressText by remember { mutableStateOf("Location not available yet") }
    var isResolvingAddress by remember { mutableStateOf(false) }

    var isUpdatingOnline by remember { mutableStateOf(false) }

    // ✅ Active job state
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var activeJobLockedAt by remember { mutableStateOf<String?>(null) }
    var hasActiveJob by remember { mutableStateOf(false) }
    var isTrackingLoading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val fine = res[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fine || coarse
        isGpsOn = isGpsEnabled(context)
    }

    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    suspend fun reverseGeocode(location: Location): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                val a = results?.firstOrNull()
                if (a != null) {
                    listOfNotNull(
                        a.thoroughfare,
                        a.subThoroughfare,
                        a.locality,
                        a.adminArea,
                        a.countryName
                    ).joinToString(", ").ifBlank {
                        a.getAddressLine(0) ?: "Unknown address"
                    }
                } else {
                    "Unknown address"
                }
            } catch (_: Exception) {
                "Unknown address"
            }
        }
    }

    suspend fun fetchFreshLocation(): Location? {
        return try {
            if (!hasLocationPermission) return null
            if (!isGpsEnabled(context)) return null

            val tokenSrc = CancellationTokenSource()

            suspendCancellableCoroutine { cont ->
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    tokenSrc.token
                )
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { cont.resume(null) }

                cont.invokeOnCancellation { tokenSrc.cancel() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun pushOnlineStatusToBackend(newOnline: Boolean, loc: Location?) {
        scope.launch {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    ProviderStatusPrefs.setIsOnline(context, !newOnline)
                    isOnline = !newOnline
                    showToast("Session expired. Please login again.")
                    return@launch
                }

                val req = ProviderStatusRequest(
                    isOnline = newOnline,
                    lat = loc?.latitude,
                    lng = loc?.longitude
                )

                ApiClient.apiService.updateProviderStatus(
                    token = "Bearer $token",
                    request = req
                )

                if (newOnline) ProviderForegroundService.start(context)
                else ProviderForegroundService.stop(context)

            } catch (e: HttpException) {
                ProviderStatusPrefs.setIsOnline(context, !newOnline)
                isOnline = !newOnline

                if (e.code() == 403) showToast("You must be verified before going online.")
                else if (e.code() == 400) showToast("Cannot go online without valid GPS location.")
                else showToast("Failed to update status: HTTP ${e.code()}")

            } catch (e: Exception) {
                ProviderStatusPrefs.setIsOnline(context, !newOnline)
                isOnline = !newOnline
                showToast("Failed to update status: ${e.message}")
            }
        }
    }

    fun refreshLocationOnce() {
        if (!hasLocationPermission) {
            showToast("Location permission required")
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        isGpsOn = isGpsEnabled(context)
        if (!isGpsOn) {
            showToast("GPS is OFF. Turn on GPS first.")
            return
        }

        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    lastLocation = loc
                    scope.launch {
                        isResolvingAddress = true
                        addressText = reverseGeocode(loc)
                        isResolvingAddress = false
                    }
                } else {
                    showToast("Could not fetch location. Try again.")
                }
            }
            .addOnFailureListener {
                showToast("Location error: ${it.message}")
            }
    }

    suspend fun refreshActiveJobState() {
        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                hasActiveJob = false
                activeJobId = null
                activeJobLockedAt = null
                return
            }

            val res = ApiClient.apiService.getProviderActiveJobs("Bearer $token")
            val jobs = res.jobs ?: emptyList()

            val best: JobResponse? =
                jobs.firstOrNull { (it.status ?: "").equals("IN_PROGRESS", true) }
                    ?: jobs.firstOrNull { (it.status ?: "").equals("ASSIGNED", true) }

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
        } catch (_: Exception) {
            hasActiveJob = false
            activeJobId = null
            activeJobLockedAt = null
        }
    }

    fun trackActiveJob() {
        scope.launch {
            if (isTrackingLoading) return@launch
            isTrackingLoading = true
            try {
                if (activeJobId.isNullOrBlank()) {
                    refreshActiveJobState()
                }
                val id = activeJobId
                if (id.isNullOrBlank()) return@launch
                onOpenTracking(id)
            } finally {
                isTrackingLoading = false
            }
        }
    }

    fun openChatForActiveJob() {
        val id = activeJobId ?: return
        onOpenChat(id, activeJobLockedAt)
    }

    LaunchedEffect(Unit) {
        isGpsOn = isGpsEnabled(context)
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshActiveJobState()
            delay(4500)
        }
    }

    DisposableEffect(hasLocationPermission, isGpsOn) {
        if (!hasLocationPermission || !isGpsOn) {
            lastLocation = null
            onDispose { }
            return@DisposableEffect onDispose { }
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L
        ).setMinUpdateDistanceMeters(8f).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                lastLocation = loc
                isGpsOn = isGpsEnabled(context)

                if (!isResolvingAddress) {
                    scope.launch {
                        isResolvingAddress = true
                        addressText = reverseGeocode(loc)
                        isResolvingAddress = false
                    }
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, context.mainLooper)
        onDispose { fusedClient.removeLocationUpdates(callback) }
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            Image(
                painter = painterResource(id = R.drawable.towmech_logo),
                contentDescription = "Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Provider Home",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = darkBlue
            )

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {

                    Text(
                        text = "Status",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isOnline) "ONLINE" else "OFFLINE",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOnline) green else red
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "GPS: ${if (isGpsOn) "ON" else "OFF"}",
                                fontSize = 14.sp,
                                color = if (isGpsOn) green else red,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = isOnline,
                            enabled = !isUpdatingOnline,
                            onCheckedChange = { wantedOnline ->
                                isGpsOn = isGpsEnabled(context)

                                if (wantedOnline && !isGpsOn) {
                                    showToast("GPS is OFF. Turn on GPS before going ONLINE.")
                                    return@Switch
                                }

                                if (!wantedOnline) {
                                    ProviderStatusPrefs.setIsOnline(context, false)
                                    isOnline = false
                                    pushOnlineStatusToBackend(false, lastLocation)
                                    showToast("You are OFFLINE ✅")
                                    return@Switch
                                }

                                scope.launch {
                                    isUpdatingOnline = true
                                    try {
                                        var loc = lastLocation

                                        if (loc == null) {
                                            showToast("Getting GPS location...")
                                            loc = fetchFreshLocation()
                                        }

                                        if (loc == null) {
                                            ProviderStatusPrefs.setIsOnline(context, false)
                                            isOnline = false
                                            showToast("Could not get GPS location. Try Refresh Location.")
                                            return@launch
                                        }

                                        lastLocation = loc
                                        isResolvingAddress = true
                                        addressText = reverseGeocode(loc)
                                        isResolvingAddress = false

                                        ProviderStatusPrefs.setIsOnline(context, true)
                                        isOnline = true
                                        pushOnlineStatusToBackend(true, loc)
                                        showToast("You are ONLINE ✅")
                                    } finally {
                                        isUpdatingOnline = false
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = green.copy(alpha = 0.55f),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = red.copy(alpha = 0.45f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Current Location",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = darkBlue
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isResolvingAddress) "Resolving address..." else addressText,
                        fontSize = 15.sp,
                        color = darkBlue
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val acc = lastLocation?.accuracy
                    Text(
                        text = "Accuracy: ${if (acc != null) "${acc.toInt()}m" else "Unknown"}",
                        fontSize = 14.sp,
                        color = darkBlue
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { refreshLocationOnce() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = currentToggleColor),
                        enabled = !isUpdatingOnline && !isTrackingLoading
                    ) {
                        Text("Refresh Location", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (hasActiveJob) {
                Button(
                    onClick = { trackActiveJob() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    enabled = !isTrackingLoading && !isUpdatingOnline
                ) {
                    Text(
                        text = if (isTrackingLoading) "Loading..." else "Track Active Job",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { openChatForActiveJob() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = black)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chat",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        if (unreadCount > 0) {
                            Surface(color = Color.Red, shape = RoundedCornerShape(999.dp)) {
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
                contentDescription = "Hero",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private object ProviderStatusPrefs {
    private const val PREF = "provider_status_pref"
    private const val KEY_ONLINE = "is_online"

    fun getIsOnline(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_ONLINE, false)
    }

    fun setIsOnline(context: Context, value: Boolean) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_ONLINE, value).apply()
    }
}

private fun isGpsEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (_: Exception) {
        false
    }
}