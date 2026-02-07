package com.towmech.app.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.towmech.app.api.ApiClient
import com.towmech.app.api.JobResponse
import com.towmech.app.data.ChatMessageDto
import com.towmech.app.data.TokenManager
import com.towmech.app.navigation.Routes
import com.towmech.app.realtime.JobChatController
import com.towmech.app.ui.ProviderBottomNav
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Locale

@Composable
fun ProviderMainScreen(
    notificationOpen: String? = null,
    notificationJobId: String? = null,
    onLoggedOut: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val baseUrl = "https://towmech-main-1.onrender.com/"

    var showPopup by remember { mutableStateOf(false) }
    var popupJob by remember { mutableStateOf<JobResponse?>(null) }
    var lastShownJobId by remember { mutableStateOf<String?>(null) }

    // ✅ global chat dialog state
    var showChatDialog by remember { mutableStateOf(false) }
    var myProviderId by remember { mutableStateOf<String?>(null) }
    var activeChatJobId by remember { mutableStateOf<String?>(null) }

    // ✅ unread badge uses provider active job (assigned)
    var activeJobIdForBadge by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // ✅ load provider user id once
    LaunchedEffect(Unit) {
        try {
            val token = TokenManager.getToken(context)
            if (!token.isNullOrBlank()) {
                val profile = ApiClient.apiService.getProfile("Bearer $token")
                myProviderId = profile.user?._id
            }
        } catch (_: Exception) {
            myProviderId = null
        }
    }

    // ✅ poll provider active job for badge
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val token = TokenManager.getToken(context)
                if (!token.isNullOrBlank()) {
                    val res = ApiClient.apiService.getProviderActiveJobs("Bearer $token")
                    val jid = res.jobs?.firstOrNull()?._id?.trim()
                    activeJobIdForBadge = jid?.takeIf { it.isNotBlank() }
                } else {
                    activeJobIdForBadge = null
                }
            } catch (_: Exception) {
                activeJobIdForBadge = null
            }
            delay(5000)
        }
    }

    // ✅ Messages for the currently-open chat job
    val chatMessages: List<ChatMessageDto> =
        activeChatJobId?.let { jid ->
            JobChatController.observeMessages(jid).collectAsState(initial = emptyList()).value
        } ?: emptyList()

    val socketReady by JobChatController.observeConnected().collectAsState(initial = false)
    val socketErr by JobChatController.observeLastError().collectAsState(initial = null)

    // ✅ FIX: unread flow must always be a Flow/StateFlow
    val badgeUnreadFlow = remember(activeJobIdForBadge) {
        val jid = activeJobIdForBadge?.trim().orEmpty()
        if (jid.isBlank()) MutableStateFlow(0)
        else JobChatController.observeUnreadCount(jid)
    }
    val badgeUnreadCount by badgeUnreadFlow.collectAsState(initial = 0)

    fun openChatForJob(jobId: String) {
        scope.launch {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Please login again.")
                    return@launch
                }

                val jid = jobId.trim()
                if (jid.isBlank()) {
                    toast("Invalid job id")
                    return@launch
                }

                activeChatJobId = jid
                JobChatController.setActiveJob(jid)

                JobChatController.ensureConnected(baseUrl = baseUrl, token = token)
                JobChatController.ensureJoined(jid)

                // ✅ history sync fixes "first message not displayed"
                JobChatController.syncHistory(jid, token)

                JobChatController.setChatOpen(jid, true)

                showChatDialog = true
            } catch (e: Exception) {
                toast("Failed to open chat: ${e.message}")
            }
        }
    }

    fun openChatIfActiveJob() {
        scope.launch {
            try {
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Please login again.")
                    return@launch
                }
                val res = ApiClient.apiService.getProviderActiveJobs("Bearer $token")
                val jid = res.jobs?.firstOrNull()?._id?.trim().orEmpty()
                if (jid.isBlank()) {
                    toast("No active job found.")
                    return@launch
                }
                openChatForJob(jid)
            } catch (e: Exception) {
                toast("Failed to open chat: ${e.message}")
            }
        }
    }

    // =========================
    // 🔔 OPEN FROM NOTIFICATION
    // =========================
    LaunchedEffect(notificationOpen, notificationJobId) {

        if (notificationOpen.equals("job_requests", ignoreCase = true)) {
            if (!notificationJobId.isNullOrBlank()) {
                navController.navigate("${Routes.PROVIDER_INCOMING_REQUEST_TAB}/$notificationJobId") {
                    launchSingleTop = true
                }
            } else {
                navController.navigate(Routes.PROVIDER_INCOMING_REQUEST_TAB) {
                    launchSingleTop = true
                }
            }
        }

        if (!notificationJobId.isNullOrBlank()) {
            try {
                val token = TokenManager.getToken(context) ?: return@LaunchedEffect

                val res = ApiClient.apiService.getJobById("Bearer $token", notificationJobId)
                val job = res.job

                if (job == null) {
                    Toast.makeText(context, "Job not found", Toast.LENGTH_LONG).show()
                    return@LaunchedEffect
                }

                if (job.status.equals("COMPLETED", ignoreCase = true)) {
                    Toast.makeText(context, "Job Completed ✅", Toast.LENGTH_LONG).show()
                    return@LaunchedEffect
                }

                popupJob = job
                lastShownJobId = job._id
                showPopup = true

            } catch (e: Exception) {

                if (e is HttpException) {
                    if (e.code() == 404 || e.code() == 409 || e.code() == 403) {
                        Toast.makeText(
                            context,
                            "Job not available for you",
                            Toast.LENGTH_LONG
                        ).show()
                        return@LaunchedEffect
                    }
                }

                Log.e("PROVIDER_POPUP", "Failed to load job from notification", e)
                Toast.makeText(context, "Failed to open job", Toast.LENGTH_LONG).show()
            }
        }
    }

    // =========================
    // 🔁 POLL BROADCASTED JOBS (popup)
    // =========================
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val token = TokenManager.getToken(context) ?: break
                val response = ApiClient.apiService.getBroadcastedJobs("Bearer $token")
                val newestJob: JobResponse? = response.jobs?.firstOrNull()

                if (
                    newestJob != null &&
                    newestJob._id.isNotBlank() &&
                    newestJob._id != lastShownJobId
                ) {
                    popupJob = newestJob
                    lastShownJobId = newestJob._id
                    showPopup = true
                }
            } catch (e: Exception) {
                Log.e("PROVIDER_POPUP", "Polling error", e)
            }

            delay(4000)
        }
    }

    Scaffold(
        bottomBar = {
            // badgeUnreadCount is computed; we'll wire it into ProviderBottomNav in ProviderBottomNav.kt
            ProviderBottomNav(navController = navController)
        }
    ) { paddingValues ->

        NavHost(
            navController = navController,
            startDestination = Routes.PROVIDER_HOME_TAB,
            modifier = Modifier.padding(paddingValues) // ✅ FIXED (import added)
        ) {

            composable(Routes.PROVIDER_HOME_TAB) {
                HomeProviderScreen(
                    onOpenTracking = { jobId ->
                        navController.navigate("${Routes.PROVIDER_JOB_TRACKING}/$jobId") {
                            launchSingleTop = true
                        }
                    },

                    // ✅ FIXED: HomeProviderScreen expects (jobId, lockStartIso)
                    onOpenChat = { jobId, _ ->
                        if (jobId.isNotBlank()) openChatForJob(jobId) else openChatIfActiveJob()
                    }

                    // ❌ REMOVED: unreadCount param (your pasted HomeProviderScreen has no unreadCount param)
                )
            }

            composable(Routes.PROVIDER_AVAILABLE_JOBS_TAB) {
                AvailableJobsScreen(
                    onBack = {},
                    onOpenJob = { jobId ->
                        navController.navigate("${Routes.PROVIDER_INCOMING_REQUEST_TAB}/$jobId")
                    }
                )
            }

            composable(Routes.PROVIDER_INCOMING_REQUEST_TAB) {
                IncomingJobRequestScreen(
                    jobId = "none",
                    onBack = {},
                    onJobAccepted = { navController.navigate(Routes.PROVIDER_ACTIVE_JOB_TAB) }
                )
            }

            composable(
                route = "${Routes.PROVIDER_INCOMING_REQUEST_TAB}/{jobId}",
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) {
                val jobId = it.arguments?.getString("jobId") ?: ""
                IncomingJobRequestScreen(
                    jobId = jobId,
                    onBack = { navController.popBackStack() },
                    onJobAccepted = { navController.navigate(Routes.PROVIDER_ACTIVE_JOB_TAB) }
                )
            }

            composable(Routes.PROVIDER_ACTIVE_JOB_TAB) {
                ProviderActiveJobScreen(
                    onBack = {},
                    onJobCompleted = { completedJobId ->
                        navController.navigate("${Routes.PROVIDER_RATE_SERVICE}/$completedJobId") {
                            launchSingleTop = true
                        }
                    },
                    onOpenTracking = { jobId ->
                        navController.navigate("${Routes.PROVIDER_JOB_TRACKING}/$jobId") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "${Routes.PROVIDER_JOB_TRACKING}/{jobId}",
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""

                ProviderJobTrackingScreen(
                    jobId = jobId,
                    onBack = { navController.popBackStack() },
                    onJobCompleted = { completedJobId ->
                        navController.navigate("${Routes.PROVIDER_RATE_SERVICE}/$completedJobId") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "${Routes.PROVIDER_RATE_SERVICE}/{jobId}",
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""

                RateServiceScreen(
                    jobId = jobId,
                    onBack = {
                        navController.navigate(Routes.PROVIDER_JOB_HISTORY_TAB) {
                            launchSingleTop = true
                        }
                    },
                    onSubmitRating = {
                        navController.navigate(Routes.PROVIDER_JOB_HISTORY_TAB) {
                            popUpTo(Routes.PROVIDER_ACTIVE_JOB_TAB) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.PROVIDER_JOB_HISTORY_TAB) {
                ProviderJobHistoryScreen(
                    onBack = {},
                    onOpenJob = {}
                )
            }

            composable(Routes.PROVIDER_PROFILE_TAB) {
                ProviderProfileScreen(
                    onBack = {},
                    onLoggedOut = onLoggedOut
                )
            }
        }

        // ✅ GLOBAL CHAT DIALOG
        JobChatDialog(
            show = showChatDialog,
            title = "Job Chat",
            myUserId = myProviderId,
            messages = chatMessages,
            canSend = socketReady && !activeChatJobId.isNullOrBlank(),
            sending = false,
            onDismiss = {
                showChatDialog = false
                val jid = activeChatJobId
                if (!jid.isNullOrBlank()) {
                    JobChatController.setChatOpen(jid, false)
                }
            },
            onSend = { text ->
                val jid = activeChatJobId ?: return@JobChatDialog
                JobChatController.sendMessage(jid, text)
            }
        )

        @Suppress("UNUSED_VARIABLE")
        val _ignore = socketErr

        // keep badge value used (until we wire into ProviderBottomNav)
        @Suppress("UNUSED_VARIABLE")
        val _ignore2 = badgeUnreadCount

        // Popup overlay left as-is
        if (showPopup && popupJob != null) {
            val job = popupJob!!
            val currency = job.pricing?.currency ?: "ZAR"

            val payout: Double =
                job.pricing?.providerAmountDue
                    ?: ((job.pricing?.estimatedTotal ?: 0.0) - (job.pricing?.bookingFee ?: 0.0))

            val safePayout = if (payout < 0) 0.0 else payout

            val payoutText =
                "Your Payout: $currency ${String.format(Locale.getDefault(), "%.2f", safePayout)}"

            val distanceKm = job.pricing?.estimatedDistanceKm
            val distanceText = distanceKm?.let {
                "Estimated Distance: ${String.format(Locale.getDefault(), "%.1f", it)} km"
            }

            JobRequestPopupOverlay(
                title = job.title ?: "New Job Request",
                pickup = job.pickupAddressText ?: "Pickup not provided",
                dropoff = job.dropoffAddressText ?: "Dropoff not provided",
                providerPayoutText = payoutText,
                estimatedDistanceText = distanceText,
                onAccept = {
                    showPopup = false

                    scope.launch {
                        try {
                            val token = TokenManager.getToken(context) ?: return@launch

                            ApiClient.apiService.acceptJob(
                                token = "Bearer $token",
                                jobId = job._id
                            )

                            navController.navigate("${Routes.PROVIDER_JOB_TRACKING}/${job._id}") {
                                launchSingleTop = true
                            }

                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Accept failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onReject = {
                    showPopup = false

                    scope.launch {
                        try {
                            val token = TokenManager.getToken(context) ?: return@launch
                            ApiClient.apiService.rejectJob("Bearer $token", job._id)
                        } catch (e: Exception) {
                            Log.e("PROVIDER_POPUP", "Reject failed", e)
                        }
                    }
                }
            )
        }
    }
}