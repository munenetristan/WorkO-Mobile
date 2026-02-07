package com.towmech.app.screens

import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.towmech.app.api.ApiClient
import com.towmech.app.api.JobResponse
import com.towmech.app.api.ProviderLocationRequest
import com.towmech.app.api.readErrorMessage
import com.towmech.app.data.ChatMessageDto
import com.towmech.app.data.TokenManager
import com.towmech.app.realtime.JobChatController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

@Composable
fun ProviderJobTrackingScreen(
    jobId: String,
    onBack: () -> Unit,
    onJobCompleted: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF2E7D32)
    val red = Color(0xFFB00020)
    val black = Color(0xFF111111)

    var job by remember { mutableStateOf<JobResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var statusUpdating by remember { mutableStateOf(false) }
    var cancelUpdating by remember { mutableStateOf(false) }

    var showRatingPopup by remember { mutableStateOf(false) }
    var ratingShownOnce by remember { mutableStateOf(false) }

    var gpsOn by remember { mutableStateOf(false) }
    var currentLoc by remember { mutableStateOf<Location?>(null) }

    var pickupLatLng by remember { mutableStateOf<LatLng?>(null) }
    var dropoffLatLng by remember { mutableStateOf<LatLng?>(null) }

    var navTarget by remember { mutableStateOf("PICKUP") }

    var lastSentLat by remember { mutableStateOf<Double?>(null) }
    var lastSentLng by remember { mutableStateOf<Double?>(null) }

    val cameraState = rememberCameraPositionState()

    // ==========================
    // ✅ CHAT UI STATE (POPUP)
    // ==========================
    var showChatPopup by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }

    // ✅ Provider ID (used to detect "my" messages correctly)
    var myProviderId by remember { mutableStateOf<String?>(null) }

    // ✅ 1-second ticker so countdown updates in UI
    var tickerNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tickerNowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun friendlyMessage(e: Throwable): String {
        return when (e) {
            is HttpException -> e.readErrorMessage()
                ?: when (e.code()) {
                    401, 403 -> "Session expired. Please login again."
                    404 -> "Not found."
                    429 -> "Too many requests. Try again shortly."
                    in 500..599 -> "Server error. Please try again shortly."
                    else -> "Request failed (HTTP ${e.code()}). Please try again."
                }

            is IOException -> "Network error. Check your internet connection."
            else -> "Something went wrong. Please try again."
        }
    }

    fun normalizeStatus(raw: String?): String {
        return (raw ?: "UNKNOWN")
            .trim()
            .uppercase()
            .replace(" ", "_")
            .replace("-", "_")
    }

    fun hasAssignedCustomer(j: JobResponse?): Boolean {
        if (j == null) return false
        val c = j.customer
        return c != null && !c._id.isNullOrBlank()
    }

    fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    fun isValidCoord(lat: Double, lng: Double): Boolean {
        if (lat == 0.0 && lng == 0.0) return false
        if (lat !in -90.0..90.0) return false
        if (lng !in -180.0..180.0) return false
        return true
    }

    suspend fun geocodeAddressToLatLng(address: String): LatLng? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val results = geocoder.getFromLocationName(address, 1)
                val a = results?.firstOrNull() ?: return@withContext null
                LatLng(a.latitude, a.longitude)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun openGoogleNavigationTo(latLng: LatLng) {
        val uri = Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    suspend fun fitCameraBetween(from: LatLng, to: LatLng) {
        val bounds = LatLngBounds.Builder().include(from).include(to).build()
        cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 180))
    }

    // ==========================================================
    // ✅ robust ISO parsing + lock start
    // ==========================================================
    fun parseIsoToMillis(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null

        try {
            return Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
        }

        try {
            return OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }

        return try {
            val cleaned = s.removeSuffix("Z").split(".")[0]
            val dt = LocalDateTime.parse(cleaned)
            dt.toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    fun readStringFieldViaReflection(j: JobResponse?, fieldName: String): String? {
        if (j == null) return null

        try {
            val getter = "get" + fieldName.replaceFirstChar { it.uppercase() }
            val m = j.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
            val v = m?.invoke(j) as? String
            if (!v.isNullOrBlank()) return v
        } catch (_: Exception) {
        }

        try {
            val f = j.javaClass.declaredFields.firstOrNull { it.name == fieldName }
            if (f != null) {
                f.isAccessible = true
                val v = f.get(j) as? String
                if (!v.isNullOrBlank()) return v
            }
        } catch (_: Exception) {
        }

        return null
    }

    fun getChatLockStartMillis(j: JobResponse?): Long? {
        val candidates = listOf("lockedAt", "assignedAt", "acceptedAt", "updatedAt", "createdAt")
        for (name in candidates) {
            val raw = readStringFieldViaReflection(j, name)
            val ms = parseIsoToMillis(raw)
            if (ms != null) return ms
        }
        return null
    }

    fun chatLockRemainingMs(j: JobResponse?): Long? {
        val start = getChatLockStartMillis(j) ?: return null
        val unlockAt = start + (3 * 60 * 1000L)
        return (unlockAt - tickerNowMs).coerceAtLeast(0L)
    }

    fun isChatAllowedNow(j: JobResponse?): Boolean {
        if (j == null) return false
        val st = normalizeStatus(j.status)

        if (st != "ASSIGNED" && st != "IN_PROGRESS") return false
        if (!hasAssignedCustomer(j)) return false

        val remaining = chatLockRemainingMs(j) ?: return false
        return remaining <= 0L
    }

    fun chatButtonVisible(j: JobResponse?): Boolean {
        if (j == null) return false
        val st = normalizeStatus(j.status)
        return st == "ASSIGNED" || st == "IN_PROGRESS"
    }

    fun formatRemaining(ms: Long): String {
        val totalSec = (ms / 1000L).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }

    // ==========================
    // ✅ GLOBAL CHAT (JobChatController)
    // ==========================
    val baseUrl = "https://towmech-main-1.onrender.com/"

    val connected by JobChatController.observeConnected().collectAsState(initial = false)
    val socketError by JobChatController.observeLastError().collectAsState(initial = null)

    val globalMessages: List<ChatMessageDto> by remember(jobId) {
        JobChatController.observeMessages(jobId)
    }.collectAsState(initial = emptyList())

    // ✅ NEW: Unread count for this job
    val unreadCount by JobChatController.observeUnreadCount(jobId).collectAsState(initial = 0)

    // ✅ Connect & join room for THIS job (global, NOT popup-only)
    LaunchedEffect(jobId) {
        val token = TokenManager.getToken(context)
        if (!token.isNullOrBlank()) {
            JobChatController.setActiveJob(jobId)
            JobChatController.ensureConnected(baseUrl = baseUrl, token = token)
            JobChatController.ensureJoined(jobId)
        }
    }

    // ✅ Track chat open/close so unread works
    LaunchedEffect(showChatPopup, jobId) {
        JobChatController.setChatOpen(jobId, showChatPopup)
    }

    // ✅ Load provider id once (used to mark "mine" in chat)
    LaunchedEffect(Unit) {
        fun readIdViaReflection(anyObj: Any?, fieldName: String): String? {
            if (anyObj == null) return null

            try {
                val getter = "get" + fieldName.replaceFirstChar { it.uppercase() }
                val m = anyObj.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
                val v = m?.invoke(anyObj) as? String
                if (!v.isNullOrBlank()) return v
            } catch (_: Exception) {
            }

            try {
                val f = anyObj.javaClass.declaredFields.firstOrNull { it.name == fieldName }
                if (f != null) {
                    f.isAccessible = true
                    val v = f.get(anyObj) as? String
                    if (!v.isNullOrBlank()) return v
                }
            } catch (_: Exception) {
            }

            return null
        }

        try {
            val token = TokenManager.getToken(context)
            if (!token.isNullOrBlank()) {
                val me = ApiClient.apiService.getProviderMe("Bearer $token")

                val possibleObjects = listOf(
                    try {
                        me.javaClass.methods.firstOrNull { it.name == "getProvider" }?.invoke(me)
                    } catch (_: Exception) {
                        null
                    },
                    try {
                        me.javaClass.methods.firstOrNull { it.name == "getUser" }?.invoke(me)
                    } catch (_: Exception) {
                        null
                    },
                    try {
                        me.javaClass.methods.firstOrNull { it.name == "getProfile" }?.invoke(me)
                    } catch (_: Exception) {
                        null
                    },
                    me
                )

                var found: String? = null
                for (obj in possibleObjects) {
                    found = readIdViaReflection(obj, "_id")
                        ?: readIdViaReflection(obj, "id")
                                ?: readIdViaReflection(obj, "providerId")
                                ?: readIdViaReflection(obj, "userId")

                    if (!found.isNullOrBlank()) break
                }

                myProviderId = found
            }
        } catch (_: Exception) {
            myProviderId = null
        }
    }

    suspend fun loadJob() {
        loading = true
        errorMessage = ""

        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "Session expired. Login again."
                loading = false
                return
            }

            val fetched = ApiClient.apiService.getProviderJobById(
                token = "Bearer $token",
                jobId = jobId
            )

            job = fetched
            loading = false

            val st = (fetched.status ?: "").uppercase()

            navTarget = when (st) {
                "ASSIGNED" -> "PICKUP"
                "IN_PROGRESS" -> "DROPOFF"
                else -> navTarget
            }

            val pickupText = fetched.pickupAddressText ?: ""
            val dropoffText = fetched.dropoffAddressText ?: ""

            if (pickupLatLng == null && pickupText.isNotBlank()) {
                pickupLatLng = geocodeAddressToLatLng(pickupText)
            }
            if (dropoffLatLng == null && dropoffText.isNotBlank()) {
                dropoffLatLng = geocodeAddressToLatLng(dropoffText)
            }

            if (st == "COMPLETED" && !ratingShownOnce) {
                ratingShownOnce = true
                showRatingPopup = true
            }

        } catch (e: Exception) {
            loading = false
            errorMessage = friendlyMessage(e)
        }
    }

    fun updateStatus(newStatus: String) {
        scope.launch {
            try {
                statusUpdating = true
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Please login again.")
                    return@launch
                }

                ApiClient.apiService.updateJobStatus(
                    token = "Bearer $token",
                    jobId = jobId,
                    request = com.towmech.app.api.UpdateStatusRequest(status = newStatus)
                )

                toast("Updated: $newStatus ✅")
                loadJob()

                if (newStatus.uppercase() == "COMPLETED" && !ratingShownOnce) {
                    ratingShownOnce = true
                    showRatingPopup = true
                }

            } catch (e: Exception) {
                val msg = friendlyMessage(e)
                errorMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                statusUpdating = false
            }
        }
    }

    fun cancelJobAsProvider() {
        scope.launch {
            try {
                cancelUpdating = true
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Please login again.")
                    return@launch
                }

                ApiClient.apiService.cancelJob(
                    token = "Bearer $token",
                    jobId = jobId,
                    request = mapOf("reason" to "Cancelled by provider")
                )

                toast("Job cancelled ✅ Re-broadcasting...")
                onBack()

            } catch (e: Exception) {
                val msg = friendlyMessage(e)
                errorMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                cancelUpdating = false
            }
        }
    }

    // Poll job
    LaunchedEffect(jobId) {
        while (true) {
            loadJob()
            delay(3000)
        }
    }

    // Get provider GPS
    LaunchedEffect(Unit) {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        while (true) {
            gpsOn = isGpsEnabled()
            if (gpsOn) {
                try {
                    fused.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) currentLoc = loc
                    }
                } catch (_: Exception) {
                }
            }
            delay(1500)
        }
    }

    // Push provider live location
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val st = (job?.status ?: "").uppercase()
                val shouldSend = st == "ASSIGNED" || st == "IN_PROGRESS"
                val loc = currentLoc

                if (shouldSend && gpsOn && loc != null) {
                    val lat = loc.latitude
                    val lng = loc.longitude

                    if (isValidCoord(lat, lng)) {
                        val changed =
                            (lastSentLat == null || lastSentLng == null) ||
                                    (abs((lastSentLat ?: 0.0) - lat) > 0.00005) ||
                                    (abs((lastSentLng ?: 0.0) - lng) > 0.00005)

                        if (changed) {
                            val token = TokenManager.getToken(context)
                            if (!token.isNullOrBlank()) {
                                ApiClient.apiService.updateProviderLocation(
                                    token = "Bearer $token",
                                    request = ProviderLocationRequest(lat = lat, lng = lng)
                                )
                                lastSentLat = lat
                                lastSentLng = lng
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GPS_PUSH", "updateProviderLocation FAILED", e)
                withContext(Dispatchers.Main) {
                    // ✅ keep it short + friendly (no HTTP dump)
                    Toast.makeText(context, "Unable to update location right now.", Toast.LENGTH_SHORT).show()
                }
            }

            delay(3000)
        }
    }

    val destination: LatLng? = remember(navTarget, pickupLatLng, dropoffLatLng) {
        if (navTarget == "PICKUP") pickupLatLng else dropoffLatLng
    }

    LaunchedEffect(currentLoc, destination) {
        val loc = currentLoc ?: return@LaunchedEffect
        val dest = destination ?: return@LaunchedEffect
        val from = LatLng(loc.latitude, loc.longitude)
        fitCameraBetween(from, dest)
    }

    val status = (job?.status ?: "").uppercase()
    val canCancel = status == "ASSIGNED"

    val primaryLeftText = when (status) {
        "IN_PROGRESS" -> "Complete Job"
        else -> "Start Job"
    }
    val primaryLeftAction: (() -> Unit)? = when (status) {
        "ASSIGNED" -> ({ updateStatus("IN_PROGRESS") })
        "IN_PROGRESS" -> ({ updateStatus("COMPLETED") })
        else -> null
    }

    val showChatButton = chatButtonVisible(job)
    val chatAllowed = isChatAllowedNow(job)
    val remainingMs = chatLockRemainingMs(job)

    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = gpsOn),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = true
            )
        ) {
            destination?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = if (navTarget == "PICKUP") "Pickup (B)" else "Dropoff (C)"
                )
            }
        }

        // Status card
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.92f),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Status: ${if (status.isBlank()) "..." else status}",
                        fontWeight = FontWeight.Bold,
                        color = black
                    )

                    val stageText = when (status) {
                        "ASSIGNED" -> "Navigation: A → B (Pickup)"
                        "IN_PROGRESS" -> "Navigation: B → C (Dropoff)"
                        "COMPLETED" -> "Job Completed ✅"
                        else -> "Waiting…"
                    }
                    Text(stageText, color = black)

                    if (!gpsOn) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("GPS is OFF", color = red, fontWeight = FontWeight.Bold)
                    }

                    if (destination == null && (status == "ASSIGNED" || status == "IN_PROGRESS")) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Loading destination…", color = black)
                    }

                    if (errorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errorMessage, color = red)
                    }
                }
            }
        }

        // Bottom buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            val buttonHeight = 48.dp
            val shape = RoundedCornerShape(18.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { primaryLeftAction?.invoke() },
                    enabled = primaryLeftAction != null && !statusUpdating && !cancelUpdating && !showRatingPopup,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = green),
                    shape = shape
                ) {
                    Text(primaryLeftText, color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val dest = destination
                        if (dest == null) {
                            Toast.makeText(context, "Destination still loading...", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        openGoogleNavigationTo(dest)
                    },
                    enabled = destination != null && !showRatingPopup,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    shape = shape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = darkBlue)
                ) {
                    Text("Open Google Map", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // CHAT button row (only active jobs)
            if (showChatButton && !showRatingPopup) {
                val lockedLabel = if (remainingMs == null) {
                    "Chat Locked"
                } else if (remainingMs > 0) {
                    "Chat Locked (${formatRemaining(remainingMs)})"
                } else {
                    "Chat with Customer"
                }

                Button(
                    onClick = {
                        if (!chatAllowed) {
                            Toast.makeText(
                                context,
                                "Chat unlocks 3 minutes after assignment.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        JobChatController.setActiveJob(jobId)
                        JobChatController.ensureJoined(jobId)
                        showChatPopup = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (chatAllowed) "Chat with Customer" else lockedLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        // ✅ Unread badge
                        if (chatAllowed && unreadCount > 0) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                color = Color(0xFFE53935),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onBack,
                    enabled = !showRatingPopup,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = darkBlue),
                    shape = shape
                ) {
                    Text("Back", color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { cancelJobAsProvider() },
                    enabled = canCancel && !cancelUpdating && !statusUpdating && !showRatingPopup,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    shape = shape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = red)
                ) {
                    Text(
                        if (cancelUpdating) "Cancelling..." else "Cancel Job",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = darkBlue
            )
        }

        if (showRatingPopup) {
            CustomerProviderRatingPopupOverlayScreen(
                jobId = jobId,
                onDismiss = {
                    showRatingPopup = false
                    onJobCompleted(jobId)
                },
                onSubmitted = {
                    showRatingPopup = false
                    onJobCompleted(jobId)
                }
            )
        }

        // ==========================
        // CHAT POPUP DIALOG (GLOBAL)
        // ==========================
        if (showChatPopup) {
            AlertDialog(
                onDismissRequest = { showChatPopup = false },
                title = { Text("Job Chat", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp)
                    ) {

                        if (!socketError.isNullOrBlank()) {
                            Text("⚠️ Chat is unavailable right now.", color = Color.Red, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                        } else if (!connected) {
                            Text("Connecting chat...", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF1C1F26), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            items(globalMessages) { msg ->
                                val isMe =
                                    !myProviderId.isNullOrBlank() &&
                                            !msg.senderId.isNullOrBlank() &&
                                            msg.senderId == myProviderId

                                val bubbleColor =
                                    if (isMe) Color(0xFF4A67FF) else Color(0xFF2B2F3A)
                                val textColor = Color.White

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        color = bubbleColor,
                                        shape = RoundedCornerShape(14.dp),
                                        shadowElevation = 2.dp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = msg.text ?: "",
                                            color = textColor,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type message...") },
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    val raw = chatInput.trim()
                                    if (raw.isBlank()) return@Button

                                    JobChatController.sendMessage(jobId, raw)
                                    chatInput = ""
                                },
                                enabled = connected,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Send")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "⚠️ Contacts are not allowed. Digits will be altered.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showChatPopup = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111))
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}