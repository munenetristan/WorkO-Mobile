package com.towmech.app.realtime

import android.util.Log
import com.towmech.app.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

/**
 * ✅ Socket Manager
 */
object ChatSocketManager {

    private const val TAG = "ChatSocketManager"

    private var socket: Socket? = null
    private var connected = false

    private var lastBaseUrl: String? = null
    private var lastRawToken: String? = null

    private var onMessage: ((JSONObject) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onJoined: ((jobId: String, threadId: String?) -> Unit)? = null

    /** Returns raw token (no "Bearer ") */
    private fun normalizeRawToken(raw: String): String {
        val t = raw.trim()
        if (t.isBlank()) return t
        return if (t.startsWith("Bearer ", ignoreCase = true)) {
            t.substringAfter("Bearer", "").trim()
        } else {
            t
        }
    }

    /** Returns "Bearer xxx" */
    private fun normalizeBearerToken(raw: String): String {
        val t = raw.trim()
        if (t.isBlank()) return t
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    private fun socketBaseUrl(): String {
        // BuildConfig.BASE_URL is like https://towmech-main-1.onrender.com/
        // Socket wants no trailing "/"
        return BuildConfig.BASE_URL.trim().removeSuffix("/")
    }

    /**
     * ✅ Connect using BuildConfig.BASE_URL (staging/prod automatically)
     */
    fun connect(token: String) {
        try {
            val url = socketBaseUrl()
            val rawToken = normalizeRawToken(token)
            val bearerToken = normalizeBearerToken(token)

            val mustRebuild =
                socket == null || lastBaseUrl != url || lastRawToken != rawToken

            if (!mustRebuild && connected) return

            if (mustRebuild) {
                try {
                    socket?.off()
                    socket?.disconnect()
                    socket?.close()
                } catch (_: Exception) {}
                socket = null
                connected = false
            }

            lastBaseUrl = url
            lastRawToken = rawToken

            val opts = IO.Options().apply {
                forceNew = true

                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 8000
                timeout = 20000

                // ✅ Prefer websocket, allow fallback
                transports = arrayOf("websocket", "polling")

                // ✅ Most Socket.IO servers reliably read auth payload
                auth = mapOf(
                    "token" to rawToken,
                    "bearer" to bearerToken
                )

                // ✅ Backup: some servers use query token
                query = "token=$rawToken"

                // ✅ Backup header
                extraHeaders = mapOf(
                    "Authorization" to listOf(bearerToken)
                )
            }

            socket = IO.socket(url, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                connected = true
                Log.d(TAG, "✅ socket connected")
            }

            socket?.on(Socket.EVENT_DISCONNECT) { args ->
                connected = false
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                Log.d(TAG, "⚠️ socket disconnected: $reason")
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                connected = false
                val msg = args.joinToString { it?.toString() ?: "null" }
                Log.e(TAG, "❌ connect_error: $msg")
                onError?.invoke("Socket connect error: $msg")
            }

            socket?.on("chat:error") { args ->
                val msg = args.firstOrNull()?.toString() ?: "Unknown socket error"
                Log.e(TAG, "❌ chat:error: $msg")
                onError?.invoke(msg)
            }

            socket?.on("chat:new_message") { args ->
                val payload = args.firstOrNull()
                try {
                    val json = when (payload) {
                        is JSONObject -> payload
                        null -> null
                        else -> JSONObject(payload.toString())
                    }
                    if (json != null) onMessage?.invoke(json)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse new_message: ${e.message}")
                }
            }

            socket?.connect()

        } catch (e: Exception) {
            connected = false
            onError?.invoke("Socket connect failed: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            socket?.disconnect()
            socket?.off()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        connected = false
        lastBaseUrl = null
        lastRawToken = null
    }

    /** ✅ Keep this FUNCTION (do not create a property with same name) */
    fun isConnected(): Boolean = connected

    fun setOnMessageListener(listener: (JSONObject) -> Unit) {
        onMessage = listener
    }

    fun setOnErrorListener(listener: (String) -> Unit) {
        onError = listener
    }

    fun setOnJoinedListener(listener: (jobId: String, threadId: String?) -> Unit) {
        onJoined = listener
    }

    fun joinJob(jobId: String) {
        try {
            val obj = JSONObject().apply { put("jobId", jobId) }

            socket?.emit("chat:join", obj, SocketAck { ackArgs ->
                try {
                    val first = ackArgs.firstOrNull()
                    val json = when (first) {
                        is JSONObject -> first
                        null -> null
                        else -> JSONObject(first.toString())
                    }

                    val ok = json?.optBoolean("ok", false) ?: false
                    val threadId = json?.optString("threadId", null)

                    if (ok) {
                        onJoined?.invoke(jobId, threadId?.takeIf { it.isNotBlank() })
                    } else {
                        val msg = json?.optString("message", "Join failed") ?: "Join failed"
                        onError?.invoke(msg)
                    }
                } catch (e: Exception) {
                    onError?.invoke("Join parse failed: ${e.message}")
                }
            })
        } catch (e: Exception) {
            onError?.invoke("Join failed: ${e.message}")
        }
    }

    fun sendMessage(jobId: String, text: String) {
        try {
            val obj = JSONObject().apply {
                put("jobId", jobId)
                put("text", text)
            }

            socket?.emit("chat:send", obj, SocketAck { ackArgs ->
                try {
                    val first = ackArgs.firstOrNull()
                    val json = when (first) {
                        is JSONObject -> first
                        null -> null
                        else -> JSONObject(first.toString())
                    }

                    val ok = json?.optBoolean("ok", true) ?: true
                    if (!ok) {
                        val msg = json?.optString("message", "Send failed") ?: "Send failed"
                        onError?.invoke(msg)
                    }
                } catch (e: Exception) {
                    onError?.invoke("Send ack parse failed: ${e.message}")
                }
            })
        } catch (e: Exception) {
            onError?.invoke("Send failed: ${e.message}")
        }
    }

    private class SocketAck(private val block: (Array<Any>) -> Unit) : (Array<Any>) -> Unit {
        override fun invoke(args: Array<Any>) = block(args)
    }
}