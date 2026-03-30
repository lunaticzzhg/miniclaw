package com.lunatic.miniclaw.data.remote.model.minimax.mapper

import com.lunatic.miniclaw.data.remote.model.minimax.dto.MiniMaxChatRequestDto
import com.lunatic.miniclaw.data.remote.model.minimax.dto.MiniMaxMessageDto
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.domain.model.model.ChatModelRequest
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig

class MiniMaxRequestMapper {
    fun toRequestDto(
        config: ModelProviderConfig,
        request: ChatModelRequest
    ): MiniMaxChatRequestDto {
        return MiniMaxChatRequestDto(
            model = config.modelName,
            messages = request.messages.map { message ->
                MiniMaxMessageDto(
                    role = message.role.toMiniMaxRole(),
                    content = message.content
                )
            },
            stream = request.stream
        )
    }

    private fun MessageRole.toMiniMaxRole(): String {
        return when (this) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        }
    }
}
