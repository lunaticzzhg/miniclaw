package com.lunatic.miniclaw.feature.chat.presentation

import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.domain.chat.model.MessageStatus

data class ChatUiState(
    val title: String = "新会话",
    val messages: List<ChatMessageItemUiModel> = emptyList(),
    val inputText: String = "",
    val canSend: Boolean = false,
    val canStop: Boolean = false,
    val activeRequestId: String? = null
)

sealed interface ChatIntent {
    data class InputChanged(val text: String) : ChatIntent
    data object SendClicked : ChatIntent
    data object StopClicked : ChatIntent
    data class RetryUserMessageClicked(val messageId: String) : ChatIntent
    data class RetryAssistantMessageClicked(val messageId: String) : ChatIntent
}

sealed interface ChatEffect

data class ChatMessageItemUiModel(
    val id: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val statusText: String?,
    val showRetry: Boolean
)
