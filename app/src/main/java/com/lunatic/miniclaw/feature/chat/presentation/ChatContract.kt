package com.lunatic.miniclaw.feature.chat.presentation

import com.lunatic.miniclaw.domain.chat.model.MessageRole

data class ChatUiState(
    val title: String = "新会话",
    val messages: List<ChatMessageItemUiModel> = emptyList(),
    val inputText: String = "",
    val canSend: Boolean = false
)

data class ChatMessageItemUiModel(
    val id: String,
    val role: MessageRole,
    val content: String
)
