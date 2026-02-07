package com.towmech.app.data

import com.google.gson.annotations.SerializedName

// ✅ Basic user ref for chat messages (minimal)
data class ChatUserRef(
    @SerializedName("_id") val id: String? = null,
    val name: String? = null,
    val role: String? = null
)

// ✅ A single chat message
data class ChatMessageDto(
    @SerializedName("_id") val id: String? = null,
    val threadId: String? = null,
    val jobId: String? = null,

    val senderId: String? = null,
    val senderRole: String? = null,
    val sender: ChatUserRef? = null,

    val text: String? = null,

    val createdAt: String? = null,
    val updatedAt: String? = null
)

// ✅ Chat thread between customer and provider for a job
data class ChatThreadDto(
    @SerializedName("_id") val id: String? = null,
    val jobId: String? = null,

    val customerId: String? = null,
    val providerId: String? = null,

    val lastMessage: ChatMessageDto? = null,

    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * ============================
 * ✅ API REQUESTS / RESPONSES
 * ============================
 */

// Create / get thread
data class ChatThreadResponse(
    val thread: ChatThreadDto? = null
)

// Send message
data class ChatSendRequest(
    val text: String
)

data class ChatSendResponse(
    val message: ChatMessageDto? = null
)

// List messages
data class ChatMessagesResponse(
    val items: List<ChatMessageDto>? = null
)