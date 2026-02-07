// CustomerJobTrackingScreen.kt
package com.towmech.app.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.layout.*
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.towmech.app.R
import com.towmech.app.api.ApiClient
import com.towmech.app.api.JobResponse
import com.towmech.app.data.ChatMessageDto
import com.towmech.app.data.TokenManager
import com.towmech.app.realtime.JobChatController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CustomerJobTrackingScreen(
    jobId: String,
    onBack: () -> Unit,
    onJobCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val darkBlue = Color(0xFF0033A0)
    val green = Color(0xFF007A3D)
    val red = Color(0xFFB00020)

    var job by remember { mutableStateOf<JobResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    var showRatingPopup by remember { mutableStateOf(false) }
    var ratingShownOnce by remember { mutableStateOf(false) }

    // Cancel dialog state
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelBusy by remember { mutableStateOf(false) }

    // Internal resolved id
    var resolvedJobId by remember { mutableStateOf<String?>(null) }

    // Local resolved coordinates
    var pickupLatLng by remember { mutableStateOf<LatLng?>(null) }
    var dropoffLatLng by remember { mutableStateOf<LatLng?>(null) }

    val cameraState = rememberCameraPositionState()

    // ==========================
    // ✅ CHAT UI STATE (POPUP)
    // ==========================
    var showChatPopup by remember { mutableStateOf(false) }

    // ==========================================================
    // ✅ 1s ticker + local lock start fallback
    // ==========================================================
    var tickerNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var localChatLockStartMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            tickerNowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    fun normalizeStatus(raw: String?): String {
        return (raw ?: "UNKNOWN")
            .trim()
            .uppercase()
            .replace(" ", "_")
            .replace("-", "_")
    }

    fun isInvalidJobId(id: String): Boolean {
        val v = id.trim().lowercase()
        return v.isBlank() || v == "none" || v == "active" || v == "null"
    }

    fun hasAssignedProvider(j: JobResponse): Boolean {
        val a = j.assignedTo
        return a != null && !a._id.isNullOrBlank()
    }

    // Start/reset local timer as soon as job becomes eligible for chat lock
    val currentJobSnapshot = job
    val statusSnap = normalizeStatus(currentJobSnapshot?.status)
    val providerIdSnap = currentJobSnapshot?.assignedTo?._id

    LaunchedEffect(statusSnap, providerIdSnap) {
        val j = currentJobSnapshot
        val eligible =
            j != null &&
                    hasAssignedProvider(j) &&
                    (normalizeStatus(j.status) == "ASSIGNED" || normalizeStatus(j.status) == "IN_PROGRESS")

        if (eligible) {
            if (localChatLockStartMs == null) {
                localChatLockStartMs = System.currentTimeMillis()
            }
        } else {
            localChatLockStartMs = null
            showChatPopup = false
        }
    }

    // ==========================================================
    // ✅ Robust ISO parsing + timestamp fallback
    // ==========================================================
    fun parseIsoToMillis(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null

        try { return Instant.parse(s).toEpochMilli() } catch (_: Exception) {}
        try { return OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (_: Exception) {}

        try {
            val cleaned = s.removeSuffix("Z").split(".")[0]
            val dt = LocalDateTime.parse(cleaned)
            return dt.toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {}

        return null
    }

    fun readStringFieldViaReflection(j: JobResponse?, fieldName: String): String? {
        if (j == null) return null

        // getter
        try {
            val getter = "get" + fieldName.replaceFirstChar { it.uppercase() }
            val m = j.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
            val v = m?.invoke(j) as? String
            if (!v.isNullOrBlank()) return v
        } catch (_: Exception) {}

        // field
        try {
            val f = j.javaClass.declaredFields.firstOrNull { it.name == fieldName }
            if (f != null) {
                f.isAccessible = true
                val v = f.get(j) as? String
                if (!v.isNullOrBlank()) return v
            }
        } catch (_: Exception) {}

        return null
    }

    fun getChatLockStartMillis(j: JobResponse?): Long? {
        val candidates = listOf("lockedAt", "assignedAt", "acceptedAt", "updatedAt", "createdAt")
        for (name in candidates) {
            val raw = readStringFieldViaReflection(j, name)
            val ms = parseIsoToMillis(raw)
            if (ms != null) return ms
        }
        return localChatLockStartMs
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
        if (!hasAssignedProvider(j)) return false
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

    fun scaledMarkerIcon(resId: Int, widthDp: Int = 44, heightDp: Int = 44): BitmapDescriptor {
        val density = context.resources.displayMetrics.density
        val wPx = (widthDp * density).toInt()
        val hPx = (heightDp * density).toInt()
        val bitmap = BitmapFactory.decodeResource(context.resources, resId)
        val scaled = Bitmap.createScaledBitmap(bitmap, wPx, hPx, true)
        return BitmapDescriptorFactory.fromBitmap(scaled)
    }

    fun providerLatLng(j: JobResponse): LatLng? {
        fun valid(lat: Double?, lng: Double?): LatLng? {
            if (lat == null || lng == null) return null
            if (lat == 0.0 && lng == 0.0) return null
            if (lat !in -90.0..90.0) return null
            if (lng !in -180.0..180.0) return null
            return LatLng(lat, lng)
        }

        val p = j.assignedTo ?: return null
        val lat = p.lat ?: p.location?.lat
        val lng = p.lng ?: p.location?.lng
        return valid(lat, lng)
    }

    fun pickTargetLatLng(j: JobResponse): LatLng? {
        val st = normalizeStatus(j.status)
        val pickupBackend =
            if (j.pickupLat != null && j.pickupLng != null) LatLng(j.pickupLat, j.pickupLng) else null
        val dropoffBackend =
            if (j.dropoffLat != null && j.dropoffLng != null) LatLng(j.dropoffLat, j.dropoffLng) else null

        val pickup = pickupBackend ?: pickupLatLng
        val dropoff = dropoffBackend ?: dropoffLatLng
        return if (st == "IN_PROGRESS") dropoff ?: pickup else pickup ?: dropoff
    }

    fun estimateEtaMinutes(from: LatLng, to: LatLng): Int? {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        val meters = results[0]
        if (meters <= 0f) return null
        val speedMps = 35_000.0 / 3600.0
        val seconds = meters / speedMps
        val minutes = (seconds / 60.0).roundToInt()
        return minutes.coerceAtLeast(1)
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

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // ✅ Friendly error message mapper (fixes ugly “HTTP 500”)
    fun friendlyHttpMessage(code: Int, rawBody: String?): String {
        return when (code) {
            401 -> "Session expired. Please login again."
            403 -> "You are not allowed to perform this action."
            404 -> "Job not found. Please refresh."
            409 -> "This job was already updated. Please refresh."
            500 -> "We couldn’t cancel the job right now. Please try again in a moment."
            else -> {
                if (!rawBody.isNullOrBlank()) rawBody.trim()
                else "Request failed (HTTP $code). Please try again."
            }
        }
    }

    // ==========================
    // ✅ GLOBAL CHAT (JobChatController)
    // ==========================
    val effectiveJobId = remember(resolvedJobId, jobId) { (resolvedJobId ?: jobId).trim() }

    val globalMessages: List<ChatMessageDto> =
        if (effectiveJobId.isNotBlank())
            JobChatController.observeMessages(effectiveJobId).collectAsState(initial = emptyList()).value
        else emptyList()

    val socketReady by JobChatController.observeConnected().collectAsState(initial = false)
    val socketError by JobChatController.observeLastError().collectAsState(initial = null)

    val unreadFlow = remember(effectiveJobId) {
        if (effectiveJobId.isBlank()) MutableStateFlow(0)
        else JobChatController.observeUnreadCount(effectiveJobId)
    }
    val unreadCount by unreadFlow.collectAsState(initial = 0)

    fun ensureGlobalChatConnectedAndJoined(jobIdToJoin: String, syncHistory: Boolean = false) {
        scope.launch {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) return@launch

            JobChatController.setActiveJob(jobIdToJoin)
            JobChatController.ensureConnected(baseUrl = "https://towmech-main-1.onrender.com/", token = token)
            JobChatController.ensureJoined(jobIdToJoin)

            if (syncHistory) {
                JobChatController.syncHistory(jobIdToJoin, token)
            }
        }
    }

    LaunchedEffect(effectiveJobId, statusSnap, providerIdSnap) {
        val j = job
        if (effectiveJobId.isBlank() || j == null) return@LaunchedEffect

        val st = normalizeStatus(j.status)
        val eligible = hasAssignedProvider(j) && (st == "ASSIGNED" || st == "IN_PROGRESS")
        if (eligible) {
            ensureGlobalChatConnectedAndJoined(effectiveJobId, syncHistory = true)
        }
    }

    fun performCancelOrDeleteDraft() {
        val id = resolvedJobId ?: jobId
        val current = job ?: run {
            toast("Job not loaded yet")
            return
        }

        val st = normalizeStatus(current.status)

        scope.launch {
            try {
                cancelBusy = true
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Login again.")
                    cancelBusy = false
                    return@launch
                }

                val auth = "Bearer $token"

                // ✅ Draft job must be deleted (backend expects this)
                if (st == "CREATED") {
                    ApiClient.apiService.deleteDraftJob(token = auth, jobId = id)
                    toast("Draft job deleted ✅")
                    showCancelDialog = false
                    onBack()
                    return@launch
                }

                // ✅ Normal cancel for all other statuses
                ApiClient.apiService.cancelJobCustomer(
                    token = auth,
                    jobId = id,
                    request = mapOf("reason" to "Cancelled by customer")
                )

                toast("Job cancelled ✅")
                showCancelDialog = false
                onBack()

            } catch (e: HttpException) {
                val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                val msg = friendlyHttpMessage(e.code(), raw)
                errorMessage = msg
                toast(msg)
            } catch (e: Exception) {
                val msg = "Cancel failed. Please try again."
                errorMessage = msg
                toast(msg)
            } finally {
                cancelBusy = false
            }
        }
    }

    // Resolve jobId if invalid
    LaunchedEffect(jobId) {
        loading = true
        errorMessage = ""
        job = null
        resolvedJobId = null
        pickupLatLng = null
        dropoffLatLng = null
        localChatLockStartMs = null

        try {
            val token = TokenManager.getToken(context)
            if (token.isNullOrBlank()) {
                errorMessage = "Session expired. Login again."
                loading = false
                return@LaunchedEffect
            }

            val auth = "Bearer $token"

            if (!isInvalidJobId(jobId)) {
                resolvedJobId = jobId
                return@LaunchedEffect
            }

            val activeRes = ApiClient.apiService.getCustomerActiveJobs(auth)
            val first = activeRes.jobs?.firstOrNull()

            if (first?._id.isNullOrBlank()) {
                errorMessage = "No active job found."
                loading = false
                resolvedJobId = null
                return@LaunchedEffect
            }

            resolvedJobId = first!!._id

        } catch (e: Exception) {
            loading = false
            errorMessage = "Failed to resolve active job: ${e.message}"
        }
    }

    // Poll job details
    LaunchedEffect(resolvedJobId) {
        val id = resolvedJobId ?: return@LaunchedEffect

        while (true) {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    errorMessage = "Session expired. Login again."
                    loading = false
                    break
                }

                val res = ApiClient.apiService.getJobById(
                    token = "Bearer $token",
                    jobId = id
                )

                val fresh = res.job
                if (fresh == null) {
                    loading = false
                    errorMessage = "Job not found (empty response)"
                } else {
                    job = fresh
                    loading = false
                    errorMessage = ""

                    if (pickupLatLng == null && (fresh.pickupLat == null || fresh.pickupLng == null)) {
                        val txt = fresh.pickupAddressText ?: ""
                        if (txt.isNotBlank()) pickupLatLng = geocodeAddressToLatLng(txt)
                    }
                    if (dropoffLatLng == null && (fresh.dropoffLat == null || fresh.dropoffLng == null)) {
                        val txt = fresh.dropoffAddressText ?: ""
                        if (txt.isNotBlank()) dropoffLatLng = geocodeAddressToLatLng(txt)
                    }

                    val st = normalizeStatus(fresh.status)
                    if (st == "COMPLETED" && !ratingShownOnce) {
                        ratingShownOnce = true
                        showRatingPopup = true
                        break
                    }
                }

            } catch (ce: CancellationException) {
                break
            } catch (e: Exception) {
                loading = false
                errorMessage = "Error loading job. Please check your network."
            }

            delay(1500)
        }
    }

    val currentJob = job
    val providerPos = currentJob?.let { providerLatLng(it) }
    val targetPos = currentJob?.let { pickTargetLatLng(it) }

    LaunchedEffect(providerPos, targetPos) {
        val p = providerPos ?: return@LaunchedEffect
        val t = targetPos ?: p

        val results = FloatArray(1)
        Location.distanceBetween(p.latitude, p.longitude, t.latitude, t.longitude, results)
        val meters = results[0].coerceAtLeast(1f)

        val zoom = when {
            meters > 20_000 -> 10.5f
            meters > 10_000 -> 11.3f
            meters > 5_000 -> 12.2f
            meters > 2_000 -> 13.2f
            meters > 1_000 -> 14.0f
            meters > 500 -> 14.8f
            meters > 250 -> 15.5f
            meters > 100 -> 16.2f
            else -> 17.0f
        }

        try {
            cameraState.animate(
                update = CameraUpdateFactory.newLatLngZoom(p, zoom),
                durationMs = 900
            )
        } catch (_: CancellationException) {
        } catch (_: Exception) {
        }
    }

    val assigned = currentJob?.let { hasAssignedProvider(it) } == true
    val statusUpper = normalizeStatus(currentJob?.status)
    val etaMin = if (providerPos != null && targetPos != null) estimateEtaMinutes(providerPos, targetPos) else null

    val statusLabel = when {
        !assigned -> "SEARCHING"
        statusUpper == "IN_PROGRESS" || statusUpper == "INPROGRESS" || statusUpper == "STARTED" -> "IN PROGRESS"
        statusUpper == "COMPLETED" -> "COMPLETED"
        else -> statusUpper.replace("_", " ")
    }

    val canShowCancel = currentJob != null &&
            statusUpper != "COMPLETED" &&
            statusUpper != "CANCELLED"

    val cancelDialogTitle = if (statusUpper == "CREATED") "Delete Draft Job?" else "Cancel Job?"
    val cancelConfirmLabel = if (statusUpper == "CREATED") "YES, DELETE" else "YES, CANCEL"

    // ✅ KEEP THESE CAUTION MESSAGES EXACTLY AS YOU PROVIDED
    val cancelRulesText = when (statusUpper) {
        "CREATED" -> """
This job is still a DRAFT (unpaid).
Deleting it removes the job completely.

Do you want to delete this draft job?
        """.trim()

        "ASSIGNED" -> """
Cancel rules:
• If you cancel within 3 minutes after job is assigned → Booking fee refunded ✅
• If you cancel after 3 minutes → Booking fee NOT refunded ❌
• If provider has not arrived within 45 minutes after assignment → You can cancel and get refund ✅

Do you want to cancel this job?
        """.trim()

        "BROADCASTED" -> """
Cancel rules:
• Job is still being broadcasted to providers.
• If you cancel now, the job will stop searching for a provider.

Do you want to cancel this job?
        """.trim()

        "IN_PROGRESS" -> """
Cancel rules:
• Job has already started.
• If you cancel now → Booking fee NOT refunded ❌

Do you want to cancel this job?
        """.trim()

        else -> """
Do you want to cancel this job?
        """.trim()
    }

    // ==========================
    // CHAT UI visibility + lock
    // ==========================
    val showChatButton = chatButtonVisible(currentJob)
    val chatAllowed = isChatAllowedNow(currentJob)
    val remainingMs = chatLockRemainingMs(currentJob)

    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            )
        ) {
            providerPos?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Driver",
                    icon = scaledMarkerIcon(R.drawable.car_marker, widthDp = 44, heightDp = 44)
                )
            }

            targetPos?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = if (statusUpper == "IN_PROGRESS") "Dropoff" else "Pickup"
                )
            }
        }

        if (loading) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
                color = Color.White.copy(alpha = 0.88f),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = darkBlue,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Loading…", color = darkBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
                color = Color.White.copy(alpha = 0.9f),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = errorMessage,
                    color = red,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Bottom panel
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            color = Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Job Status: $statusLabel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkBlue
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (assigned && etaMin != null && statusUpper != "COMPLETED") {
                            Text(
                                text = "ETA: ~ $etaMin min",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = green
                            )
                        } else {
                            Text(
                                text = "ETA: —",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onBack,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = darkBlue),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Back", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // CHAT BUTTON
                if (showChatButton && !showRatingPopup) {
                    Spacer(modifier = Modifier.height(10.dp))

                    val lockedLabel = if (remainingMs == null) {
                        "Chat Locked"
                    } else if (remainingMs > 0) {
                        "Chat Locked (${formatRemaining(remainingMs)})"
                    } else {
                        "Chat with Provider"
                    }

                    Button(
                        onClick = {
                            if (!chatAllowed) {
                                toast("Chat unlocks 3 minutes after assignment.")
                                return@Button
                            }
                            if (effectiveJobId.isNotBlank()) {
                                JobChatController.setChatOpen(effectiveJobId, true)
                                ensureGlobalChatConnectedAndJoined(effectiveJobId, syncHistory = true)
                            }
                            showChatPopup = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (chatAllowed) "Chat with Provider" else lockedLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
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

                    if (socketError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠️ ${socketError ?: ""}",
                            color = red,
                            fontSize = 12.sp
                        )
                    }
                }

                // Cancel / Delete Draft button
                if (canShowCancel && !showRatingPopup) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showCancelDialog = true },
                        enabled = !cancelBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = red)
                    ) {
                        Text(
                            text = if (statusUpper == "CREATED") {
                                if (cancelBusy) "Please wait..." else "Delete Draft Job"
                            } else {
                                if (cancelBusy) "Please wait..." else "Cancel Job"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // Cancel dialog (text unchanged)
        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { if (!cancelBusy) showCancelDialog = false },
                title = { Text(cancelDialogTitle, fontWeight = FontWeight.Bold) },
                text = { Text(cancelRulesText, fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = { performCancelOrDeleteDraft() },
                        enabled = !cancelBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = red)
                    ) {
                        Text(
                            if (cancelBusy) "Processing..." else cancelConfirmLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showCancelDialog = false },
                        enabled = !cancelBusy
                    ) {
                        Text("NO", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showRatingPopup) {
            CustomerProviderRatingPopupOverlayScreen(
                jobId = resolvedJobId ?: jobId,
                onDismiss = {
                    showRatingPopup = false
                    onJobCompleted()
                },
                onSubmitted = {
                    showRatingPopup = false
                    onJobCompleted()
                }
            )
        }

        JobChatDialog(
            show = showChatPopup,
            title = "Job Chat",
            myUserId = job?.customer?._id,
            messages = globalMessages,
            canSend = chatAllowed && socketReady && effectiveJobId.isNotBlank(),
            sending = false,
            onDismiss = {
                showChatPopup = false
                if (effectiveJobId.isNotBlank()) {
                    JobChatController.setChatOpen(effectiveJobId, false)
                }
            },
            onSend = { text ->
                if (effectiveJobId.isBlank()) return@JobChatDialog
                JobChatController.sendMessage(effectiveJobId, text)
            }
        )
    }
}