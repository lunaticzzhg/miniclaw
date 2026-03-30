package com.lunatic.miniclaw.data.remote.model.minimax.dto

data class MiniMaxStreamChunkDto(
    val type: String? = null,
    val delta: String? = null,
    val error: MiniMaxErrorDto? = null
)
