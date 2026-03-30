package com.lunatic.miniclaw.data.remote.model

sealed interface ChatStreamEvent {
    data object Started : ChatStreamEvent
    data class Delta(val textChunk: String) : ChatStreamEvent
    data object Completed : ChatStreamEvent
    data class Failed(val reason: String) : ChatStreamEvent
}
