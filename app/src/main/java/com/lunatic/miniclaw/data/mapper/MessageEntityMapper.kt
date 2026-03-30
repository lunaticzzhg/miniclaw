package com.lunatic.miniclaw.data.mapper

import com.lunatic.miniclaw.data.local.entity.MessageEntity
import com.lunatic.miniclaw.domain.chat.model.ChatMessage
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.domain.chat.model.MessageStatus

class MessageEntityMapper {
    fun toDomain(entity: MessageEntity): ChatMessage {
        return ChatMessage(
            id = entity.id,
            sessionId = entity.sessionId,
            requestId = entity.requestId,
            role = MessageRole.valueOf(entity.role),
            content = entity.content,
            status = MessageStatus.valueOf(entity.status),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
