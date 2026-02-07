package com.towmech.app.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.towmech.app.api.ApiClient
import com.towmech.app.api.SupportMessage
import com.towmech.app.api.SupportTicket
import com.towmech.app.api.SupportReplyRequest
import com.towmech.app.data.TokenManager
import kotlinx.coroutines.launch

@Composable
fun CustomerSupportTicketScreen(
    ticketId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var ticket by remember { mutableStateOf<SupportTicket?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var reply by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun loadTicket() {
        scope.launch {
            try {
                loading = true
                error = null

                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    error = "Session expired. Please login again."
                    return@launch
                }

                val res = ApiClient.apiService.getSupportTicketById(
                    token = "Bearer $token",
                    ticketId = ticketId
                )
                ticket = res.ticket
            } catch (e: Exception) {
                error = "Failed to load ticket: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun sendReply() {
        val text = reply.trim()
        if (text.isBlank()) return toast("Reply cannot be empty")

        scope.launch {
            try {
                sending = true
                val token = TokenManager.getToken(context)
                if (token.isNullOrBlank()) {
                    toast("Session expired. Please login again.")
                    return@launch
                }

                val res = ApiClient.apiService.replyToSupportTicket(
                    token = "Bearer $token",
                    ticketId = ticketId,
                    request = SupportReplyRequest(message = text)
                )

                ticket = res.ticket
                reply = ""
                toast("Sent ✅")
            } catch (e: Exception) {
                toast("Failed to send reply: ${e.message}")
            } finally {
                sending = false
            }
        }
    }

    LaunchedEffect(ticketId) { loadTicket() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ticket", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))

        if (loading) {
            CircularProgressIndicator()
            return@Column
        }

        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { loadTicket() }) { Text("Retry") }
            return@Column
        }

        val t = ticket
        if (t == null) {
            Text("Ticket not found", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(t.subject ?: "No subject", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Type: ${t.type ?: "UNKNOWN"} | Priority: ${t.priority ?: "UNKNOWN"}")
                Spacer(Modifier.height(4.dp))
                Text("Status: ${t.status ?: "UNKNOWN"}")
                Spacer(Modifier.height(8.dp))
                Text(t.message ?: "")
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Thread", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val thread: List<SupportMessage> = t.messages ?: emptyList()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(thread) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        val senderName = m.senderId?.name ?: m.senderRole ?: "User"
                        Text(senderName, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(m.message ?: "")
                        if (!m.createdAt.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                m.createdAt!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = reply,
            onValueChange = { reply = it },
            label = { Text("Reply") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { sendReply() },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sending) "Sending..." else "Send Reply")
        }
    }
}