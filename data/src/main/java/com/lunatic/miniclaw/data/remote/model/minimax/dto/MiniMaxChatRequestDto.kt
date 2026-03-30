package com.lunatic.miniclaw.data.remote.model.minimax.dto

data class MiniMaxChatRequestDto(
    val model: String,
    val messages: List<MiniMaxMessageDto>,
    val stream: Boolean
)
