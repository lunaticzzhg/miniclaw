package com.lunatic.miniclaw.data.remote.model

data class ChatStreamRequest(
    val sessionId: String,
    val userText: String
)
