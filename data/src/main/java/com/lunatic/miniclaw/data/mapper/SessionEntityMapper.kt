package com.lunatic.miniclaw.data.mapper

import com.lunatic.miniclaw.data.local.entity.SessionEntity
import com.lunatic.miniclaw.domain.session.model.ChatSession

class SessionEntityMapper {
    fun toDomain(entity: SessionEntity, hasStreamingMessage: Boolean = false): ChatSession {
        return ChatSession(
            id = entity.id,
            title = entity.title,
            lastMessagePreview = entity.lastMessagePreview,
            updatedAt = entity.updatedAt,
            createdAt = entity.createdAt,
            hasStreamingMessage = hasStreamingMessage
        )
    }
}
