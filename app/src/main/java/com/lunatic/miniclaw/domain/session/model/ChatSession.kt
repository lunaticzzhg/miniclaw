package com.lunatic.miniclaw.domain.session.model

data class ChatSession(
    val id: String,
    val title: String,
    val lastMessagePreview: String?,
    val updatedAt: Long,
    val createdAt: Long,
    val hasStreamingMessage: Boolean
)
