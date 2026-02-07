package com.towmech.app.api

/**
 * ✅ Matches backend SupportTicket model + thread messages
 * Fix: senderId can be String OR Object -> use FlexibleUser (already supported by FlexibleUserAdapter)
 */

data class SupportTicketsResponse(
    val tickets: List<SupportTicket>? = emptyList()
)

data class CreateSupportTicketResponse(
    val message: String? = null,
    val ticket: SupportTicket? = null
)

data class SupportTicketResponse(
    val ticket: SupportTicket? = null,
    val message: String? = null
)

/**
 * ✅ Backend accepts:
 * subject, message, type, priority, jobId?, providerId?
 */
data class CreateSupportTicketRequest(
    val subject: String,
    val message: String,
    val type: String,
    val priority: String,
    val jobId: String? = null,
    val providerId: String? = null
)

data class SupportReplyRequest(
    val message: String
)

data class SupportTicket(
    val _id: String? = null,
    val subject: String? = null,
    val message: String? = null,
    val type: String? = null,
    val priority: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,

    // ✅ NEW THREAD
    val messages: List<SupportMessage>? = emptyList()
)

data class SupportMessage(
    val _id: String? = null,

    // ✅ FIX: can be "6630..." OR { _id, name, email, role }
    val senderId: FlexibleUser? = null,

    val senderRole: String? = null,
    val message: String? = null,
    val createdAt: String? = null
)