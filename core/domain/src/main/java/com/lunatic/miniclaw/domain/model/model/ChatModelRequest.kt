package com.lunatic.miniclaw.domain.model.model

data class ChatModelRequest(
    val sessionId: String,
    val requestId: String,
    val messages: List<ChatHistoryMessage>,
    val stream: Boolean = true
)
