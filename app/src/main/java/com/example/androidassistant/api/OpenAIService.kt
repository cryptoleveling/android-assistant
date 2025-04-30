package com.example.androidassistant.api

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAIService {
    @Headers(
        "Content-Type: application/json",
        "Authorization: Bearer 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"
    )
    @POST("v1/chat/completions")
    suspend fun getChatCompletion(@Body request: ChatRequest): ChatResponse
}

data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
) 