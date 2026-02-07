// JobChatController.kt
package com.towmech.app.realtime

import android.util.Log
import com.towmech.app.api.ApiClient
import com.towmech.app.data.ChatMessageDto
import com.towmech.app.data.ChatUserRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ✅ Global Chat Controller
 *
 * - Keeps ONE socket connection alive
 * - Joins the active job room
 * - Stores messages in StateFlow so any screen/dialog can display them
 *
 * ✅ Added:
 * - unread count per job
 * - chat open state per job (prevents unread growth while user is in chat)
 * - history sync (REST) to fix "first message not displayed"
 *
 * ✅ Added (for bottom-nav badges):
 * - observeTotalUnread()
 */
object JobChatController {

    private const val TAG = "JobChatController"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connected = MutableStateFlow(false)
    fun observeConnected(): StateFlow<Boolean> = _connected.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    fun observeLastError(): StateFlow<String?> = _lastError.asStateFlow()

    private val _activeJobId = MutableStateFlow<String?>(null)
    fun activeJobId(): StateFlow<String?> = _activeJobId.asStateFlow()

    // messagesByJob[jobId] = StateFlow<List<ChatMessageDto>>
    private val messagesByJob = mutableMapOf<String, MutableStateFlow<List<ChatMessageDto>>>()

    // ✅ unreadByJob[jobId] = StateFlow<Int>
    private val unreadByJob = mutableMapOf<String, MutableStateFlow<Int>>()

    // ✅ openByJob[jobId] = whether chat dialog is open
    private val chatOpenByJob = mutableMapOf<String, MutableStateFlow<Boolean>>()

    // ✅ TOTAL unread across all jobs (for bottom nav badge)
    private val _totalUnread = MutableStateFlow(0)
    fun observeTotalUnread(): StateFlow<Int> = _totalUnread.asStateFlow()

    private var lastJoinedJobId: String? = null
    private var hasRegisteredListeners = false

    fun setActiveJob(jobId: String?) {
        _activeJobId.value = jobId?.trim()?.takeIf { it.isNotBlank() }
    }

    fun observeMessages(jobId: String): StateFlow<List<ChatMessageDto>> {
        val key = jobId.trim()
        val flow = messagesByJob.getOrPut(key) { MutableStateFlow(emptyList()) }
        return flow.asStateFlow()
    }

    fun clearJob(jobId: String) {
        val key = jobId.trim()
        messagesByJob[key]?.value = emptyList()
        unreadByJob[key]?.value = 0
        recalcTotalUnread()
    }

    // ==========================
    // ✅ UNREAD SUPPORT
    // ==========================
    fun observeUnreadCount(jobId: String): StateFlow<Int> {
        val key = jobId.trim()
        val flow = unreadByJob.getOrPut(key) { MutableStateFlow(0) }
        return flow.asStateFlow()
    }

    fun clearUnread(jobId: String) {
        val key = jobId.trim()
        unreadByJob.getOrPut(key) { MutableStateFlow(0) }.value = 0
        recalcTotalUnread()
    }

    fun setChatOpen(jobId: String, isOpen: Boolean) {
        val key = jobId.trim()
        chatOpenByJob.getOrPut(key) { MutableStateFlow(false) }.value = isOpen
        if (isOpen) clearUnread(key)
    }

    private fun isChatOpen(jobId: String): Boolean {
        val key = jobId.trim()
        return chatOpenByJob[key]?.value == true
    }

    private fun incUnread(jobId: String) {
        val key = jobId.trim()
        val uf = unreadByJob.getOrPut(key) { MutableStateFlow(0) }
        uf.value = (uf.value + 1).coerceAtMost(999)
        recalcTotalUnread()
    }

    private fun recalcTotalUnread() {
        _totalUnread.value = unreadByJob.values.sumOf { it.value }.coerceAtMost(9999)
    }

    // ==========================
    // ✅ SOCKET CONNECT
    // ==========================
    fun ensureConnected(baseUrl: String, token: String) {
        if (!hasRegisteredListeners) {
            hasRegisteredListeners = true

            ChatSocketManager.setOnErrorListener { msg ->
                _lastError.value = msg
                _connected.value = ChatSocketManager.isConnected()
                Log.e(TAG, "socket error: $msg")
            }

            ChatSocketManager.setOnJoinedListener { jobId, _ ->
                lastJoinedJobId = jobId
                _connected.value = true
                _lastError.value = null
                Log.d(TAG, "joined room for jobId=$jobId")
            }

            ChatSocketManager.setOnMessageListener { json ->
                try {
                    val incomingJobId = json.optString("jobId").trim()
                    val targetJobId = if (incomingJobId.isNotBlank()) incomingJobId else _activeJobId.value
                    if (targetJobId.isNullOrBlank()) return@setOnMessageListener

                    val msg = jsonToChatMessage(json)

                    val flow = messagesByJob.getOrPut(targetJobId) { MutableStateFlow(emptyList()) }
                    val current = flow.value

                    // ✅ avoid duplicates by msg.id (if present)
                    val mid = msg.id
                    val already = !mid.isNullOrBlank() && current.any { it.id == mid }

                    if (!already) {
                        flow.value = current + msg
                    }

                    // ✅ unread logic:
                    // if chat is NOT open for that job, increment unread
                    if (!isChatOpen(targetJobId)) {
                        incUnread(targetJobId)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing incoming message", e)
                }
            }
        }

        if (!ChatSocketManager.isConnected()) {
            ChatSocketManager.connect(token = token)
        }

        _connected.value = ChatSocketManager.isConnected()
    }

    fun ensureJoined(jobId: String) {
        val key = jobId.trim()
        if (key.isBlank()) return

        if (lastJoinedJobId != key) {
            ChatSocketManager.joinJob(key)
        }
    }

    fun sendMessage(jobId: String, text: String) {
        val key = jobId.trim()
        if (key.isBlank()) return
        ChatSocketManager.sendMessage(key, text)
    }

    // ==========================
    // ✅ REST HISTORY SYNC
    // Fixes: "first message not displayed" when receiver joins after first emit
    // ==========================
    fun syncHistory(jobId: String, token: String) {
        val key = jobId.trim()
        if (key.isBlank()) return

        scope.launch {
            try {
                val auth = if (token.startsWith("Bearer ", true)) token else "Bearer $token"
                val res = ApiClient.apiService.getChatMessages(auth, key)

                // NOTE: Your project currently uses res.items
                val incoming = res.items ?: emptyList()

                val flow = messagesByJob.getOrPut(key) { MutableStateFlow(emptyList()) }
                val current = flow.value

                val merged = (current + incoming).distinctBy { m ->
                    m.id ?: "${m.senderId}|${m.text}|${m.createdAt}"
                }

                flow.value = merged
            } catch (e: Exception) {
                Log.e(TAG, "syncHistory failed: ${e.message}", e)
            }
        }
    }

    // --------------------------
    // Helpers
    // --------------------------
    private fun jsonToChatMessage(json: JSONObject): ChatMessageDto {
        val id = json.optString("_id", "").ifBlank { null }
        val threadId = json.optString("threadId", "").ifBlank { null }
        val jobId = json.optString("jobId", "").ifBlank { null }

        val senderId =
            json.optString("senderId", "").ifBlank {
                json.optString("sender", "").ifBlank { null }
            }

        val senderRole = json.optString("senderRole", "").ifBlank { null }
        val text = json.optString("text", "").ifBlank { null }
        val createdAt = json.optString("createdAt", "").ifBlank { null }
        val updatedAt = json.optString("updatedAt", "").ifBlank { null }

        val senderObj = json.opt("sender")
        val senderRef: ChatUserRef? = try {
            if (senderObj is JSONObject) {
                ChatUserRef(
                    id = senderObj.optString("_id", "").ifBlank { null },
                    name = senderObj.optString("name", "").ifBlank { null },
                    role = senderObj.optString("role", "").ifBlank { null }
                )
            } else null
        } catch (_: Exception) {
            null
        }

        return ChatMessageDto(
            id = id,
            threadId = threadId,
            jobId = jobId,
            senderId = senderId,
            senderRole = senderRole,
            sender = senderRef,
            text = text,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}