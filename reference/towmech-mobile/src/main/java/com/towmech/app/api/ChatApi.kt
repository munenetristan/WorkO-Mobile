package com.towmech.app.api

import com.towmech.app.data.ChatMessagesResponse
import com.towmech.app.data.ChatSendRequest
import com.towmech.app.data.ChatSendResponse
import com.towmech.app.data.ChatThreadResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ChatApi {

    /**
     * ✅ Get or create a thread for a job
     * Backend: POST /api/chat/thread/:jobId
     */
    @POST("/api/chat/thread/{jobId}")
    suspend fun getOrCreateThread(
        @Header("Authorization") token: String,
        @Path("jobId") jobId: String
    ): ChatThreadResponse

    /**
     * ✅ List messages
     * Backend: GET /api/chat/thread/:threadId/messages
     */
    @GET("/api/chat/thread/{threadId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("threadId") threadId: String
    ): ChatMessagesResponse

    /**
     * ✅ Send message
     * Backend: POST /api/chat/thread/:threadId/send
     */
    @POST("/api/chat/thread/{threadId}/send")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Path("threadId") threadId: String,
        @Body body: ChatSendRequest
    ): ChatSendResponse
}