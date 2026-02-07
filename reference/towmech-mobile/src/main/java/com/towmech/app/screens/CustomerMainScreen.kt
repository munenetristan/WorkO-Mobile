package com.towmech.app.screens

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.towmech.app.api.ApiClient
import com.towmech.app.data.ChatMessageDto
import com.towmech.app.data.TokenManager
import com.towmech.app.navigation.Routes
import com.towmech.app.realtime.JobChatController
import com.towmech.app.ui.CustomerBottomNav
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun CustomerMainScreen(
    onGoRequestService: () -> Unit,
    onOpenTracking: (String) -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val baseUrl = "https://towmech-main-1.onrender.com/"

    var showChatDialog by remember { mutableStateOf(false) }
    var myCustomerId by remember { mutableStateOf<String?>(null) }

    var activeChatJobId by remember { mutableStateOf<String?>(null) }
    var activeJobIdForBadge by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        try {
            val token = TokenManager.getToken(context)
            if (!token.isNullOrBlank()) {
                val profile = ApiClient.apiService.getProfile("Bearer $token")
                myCustomerId = profile.user?._id
            }
        } catch (_: Exception) {
            myCustomerId = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val token = TokenManager.getToken(context)
                if (!token.isNullOrBlank()) {
                    val res = ApiClient.apiService.getCustomerActiveJobs("Bearer $token")
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

    val chatMessages: List<ChatMessageDto> =
        activeChatJobId?.let { jid ->
            JobChatController.observeMessages(jid).collectAsState(initial = emptyList()).value
        } ?: emptyList()

    val socketReady by JobChatController.observeConnected().collectAsState(initial = false)
    val socketErr by JobChatController.observeLastError().collectAsState(initial = null)

    val badgeUnreadFlow = remember(activeJobIdForBadge) {
        val jid = activeJobIdForBadge?.trim().orEmpty()
        if (jid.isBlank()) MutableStateFlow(0) else JobChatController.observeUnreadCount(jid)
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
                val res = ApiClient.apiService.getCustomerActiveJobs("Bearer $token")
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

    Scaffold(
        bottomBar = {
            CustomerBottomNav(
                navController = navController,
                unreadCount = badgeUnreadCount
            )
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = Routes.CUSTOMER_HOME_TAB,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.CUSTOMER_HOME_TAB) {
                HomeCustomerScreen(
                    onGoRequestService = onGoRequestService,
                    onOpenTracking = onOpenTracking,
                    onOpenChat = { jobId, _ ->
                        if (jobId.isNotBlank()) openChatForJob(jobId) else openChatIfActiveJob()
                    }
                )
            }

            composable(Routes.CUSTOMER_ACTIVE_TAB) {
                CustomerActiveJobsScreen(
                    onOpenTracking = { jid -> onOpenTracking(jid) }
                )
            }

            composable(Routes.CUSTOMER_HISTORY_TAB) {
                CustomerJobHistoryScreen(
                    onJobSelected = { jid -> onOpenTracking(jid) }
                )
            }

            // ✅ SUPPORT LIST
            composable(Routes.CUSTOMER_SUPPORT_TAB) {
                CustomerSupportScreen(navController = navController)
            }

            // ✅ SUPPORT THREAD DETAILS
            composable(
                route = "${Routes.CUSTOMER_SUPPORT_TICKET}/{ticketId}",
                arguments = listOf(navArgument("ticketId") { type = NavType.StringType })
            ) { backStack ->
                val ticketId = backStack.arguments?.getString("ticketId").orEmpty()
                CustomerSupportTicketScreen(
                    ticketId = ticketId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CUSTOMER_PROFILE_TAB) {
                CustomerProfileScreen(onLogout = onLogout)
            }
        }

        JobChatDialog(
            show = showChatDialog,
            title = "Job Chat",
            myUserId = myCustomerId,
            messages = chatMessages,
            canSend = socketReady && !activeChatJobId.isNullOrBlank(),
            sending = false,
            onDismiss = {
                showChatDialog = false
                val jid = activeChatJobId?.trim()
                if (!jid.isNullOrBlank()) {
                    JobChatController.setChatOpen(jid, false)
                }
            },
            onSend = { text ->
                val jid = activeChatJobId?.trim()
                if (jid.isNullOrBlank()) return@JobChatDialog
                JobChatController.sendMessage(jid, text)
            }
        )

        @Suppress("UNUSED_VARIABLE")
        val _ignore = socketErr
    }
}