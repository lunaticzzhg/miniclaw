package com.lunatic.miniclaw.domain.model.model

import com.lunatic.miniclaw.domain.chat.model.MessageRole

data class ChatHistoryMessage(
    val role: MessageRole,
    val content: String
)
