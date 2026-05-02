package com.jadeai.solvertracker.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisRequest(
    val model: String,
    @SerialName("response_format")
    val responseFormat: ResponseFormat,
    val messages: List<Message>
)

@Serializable
data class ChatRequest(
    val model: String,
    @SerialName("stream")
    val stream: Boolean = false,
    val messages: List<Message>
)

@Serializable
data class ResponseFormat(
    val type: String
)

@Serializable
data class Message(
    val role: String,
    val content: String
)
