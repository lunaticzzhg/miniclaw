package com.lunatic.miniclaw.domain.chat.model

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val requestId: String?,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val createdAt: Long,
    val updatedAt: Long
)
