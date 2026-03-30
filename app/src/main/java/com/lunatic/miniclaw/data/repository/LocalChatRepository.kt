package com.lunatic.miniclaw.data.repository

import androidx.room.withTransaction
import com.lunatic.miniclaw.data.local.dao.MessageDao
import com.lunatic.miniclaw.data.local.dao.SessionDao
import com.lunatic.miniclaw.data.local.db.MiniClawDatabase
import com.lunatic.miniclaw.data.local.entity.MessageEntity
import com.lunatic.miniclaw.data.mapper.MessageEntityMapper
import com.lunatic.miniclaw.domain.chat.model.ChatMessage
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.domain.chat.model.MessageStatus
import com.lunatic.miniclaw.domain.chat.repository.ChatRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalChatRepository(
    private val database: MiniClawDatabase,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val mapper: MessageEntityMapper
) : ChatRepository {

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.observeBySession(sessionId).map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun sendUserMessage(sessionId: String, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) {
            return
        }

        val session = sessionDao.getById(sessionId) ?: return
        val now = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            requestId = requestId,
            role = MessageRole.USER.name,
            content = normalized,
            status = MessageStatus.SENT.name,
            createdAt = now,
            updatedAt = now
        )

        val nextTitle = if (session.title == DEFAULT_SESSION_TITLE) {
            normalized.take(TITLE_MAX_LEN)
        } else {
            session.title
        }

        database.withTransaction {
            messageDao.insert(message)
            sessionDao.updateSummary(
                sessionId = sessionId,
                title = nextTitle,
                preview = normalized,
                updatedAt = now
            )
        }
    }

    private companion object {
        private const val DEFAULT_SESSION_TITLE = "新会话"
        private const val TITLE_MAX_LEN = 20
    }
}
