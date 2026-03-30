package com.lunatic.miniclaw.data.repository

import androidx.room.withTransaction
import com.lunatic.miniclaw.data.local.dao.MessageDao
import com.lunatic.miniclaw.data.local.dao.SessionDao
import com.lunatic.miniclaw.data.local.db.MiniClawDatabase
import com.lunatic.miniclaw.data.local.entity.MessageEntity
import com.lunatic.miniclaw.data.mapper.MessageEntityMapper
import com.lunatic.miniclaw.data.remote.datasource.ChatRemoteDataSource
import com.lunatic.miniclaw.data.remote.model.ChatStreamEvent
import com.lunatic.miniclaw.data.remote.model.ChatStreamRequest
import com.lunatic.miniclaw.domain.chat.model.ChatMessage
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.domain.chat.model.MessageStatus
import com.lunatic.miniclaw.domain.chat.repository.ChatRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LocalChatRepository(
    private val database: MiniClawDatabase,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val mapper: MessageEntityMapper,
    private val remoteDataSource: ChatRemoteDataSource
) : ChatRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestJobs = ConcurrentHashMap<String, Job>()
    private val activeSessionRequests = ConcurrentHashMap<String, String>()

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.observeBySession(sessionId).map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override suspend fun sendUserMessage(sessionId: String, text: String) {
        if (activeSessionRequests.containsKey(sessionId)) {
            return
        }

        val normalized = text.trim()
        if (normalized.isEmpty()) {
            return
        }

        val session = sessionDao.getById(sessionId) ?: return
        val now = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        val userMessage = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            requestId = requestId,
            role = MessageRole.USER.name,
            content = normalized,
            status = MessageStatus.SENT.name,
            createdAt = now,
            updatedAt = now
        )
        val assistantMessage = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            requestId = requestId,
            role = MessageRole.ASSISTANT.name,
            content = "",
            status = MessageStatus.THINKING.name,
            createdAt = now,
            updatedAt = now
        )

        val nextTitle = if (session.title == DEFAULT_SESSION_TITLE) {
            normalized.take(TITLE_MAX_LEN)
        } else {
            session.title
        }

        database.withTransaction {
            messageDao.insert(userMessage)
            messageDao.insert(assistantMessage)
            sessionDao.updateSummary(
                sessionId = sessionId,
                title = nextTitle,
                preview = normalized,
                updatedAt = now
            )
        }

        startStreaming(
            sessionId = sessionId,
            requestId = requestId,
            assistantMessageId = assistantMessage.id,
            userText = normalized
        )
    }

    override suspend fun stopStreaming(requestId: String) {
        requestJobs[requestId]?.cancel()
    }

    private fun startStreaming(
        sessionId: String,
        requestId: String,
        assistantMessageId: String,
        userText: String
    ) {
        activeSessionRequests[sessionId] = requestId

        val job = repositoryScope.launch {
            try {
                remoteDataSource.streamChat(
                    ChatStreamRequest(sessionId = sessionId, userText = userText)
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Started -> {
                            updateAssistantStatus(
                                assistantMessageId = assistantMessageId,
                                status = MessageStatus.STREAMING
                            )
                        }

                        is ChatStreamEvent.Delta -> {
                            appendAssistantDelta(
                                assistantMessageId = assistantMessageId,
                                textChunk = event.textChunk
                            )
                        }

                        is ChatStreamEvent.Completed -> {
                            completeAssistantMessage(
                                sessionId = sessionId,
                                assistantMessageId = assistantMessageId
                            )
                        }

                        is ChatStreamEvent.Failed -> {
                            failAssistantMessage(
                                assistantMessageId = assistantMessageId
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                stopAssistantMessage(
                    assistantMessageId = assistantMessageId
                )
            } catch (_: Throwable) {
                failAssistantMessage(
                    assistantMessageId = assistantMessageId
                )
            } finally {
                requestJobs.remove(requestId)
                activeSessionRequests.remove(sessionId, requestId)
            }
        }

        requestJobs[requestId] = job
    }

    private suspend fun updateAssistantStatus(
        assistantMessageId: String,
        status: MessageStatus
    ) {
        val entity = messageDao.getById(assistantMessageId) ?: return
        messageDao.update(
            entity.copy(
                status = status.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun appendAssistantDelta(
        assistantMessageId: String,
        textChunk: String
    ) {
        val entity = messageDao.getById(assistantMessageId) ?: return
        messageDao.update(
            entity.copy(
                content = entity.content + textChunk,
                status = MessageStatus.STREAMING.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun completeAssistantMessage(
        sessionId: String,
        assistantMessageId: String
    ) {
        val now = System.currentTimeMillis()
        val entity = messageDao.getById(assistantMessageId) ?: return
        database.withTransaction {
            messageDao.update(
                entity.copy(
                    status = MessageStatus.COMPLETED.name,
                    updatedAt = now
                )
            )
            sessionDao.updateSummary(
                sessionId = sessionId,
                title = sessionDao.getById(sessionId)?.title ?: DEFAULT_SESSION_TITLE,
                preview = entity.content,
                updatedAt = now
            )
        }
    }

    private suspend fun stopAssistantMessage(
        assistantMessageId: String
    ) {
        val entity = messageDao.getById(assistantMessageId) ?: return
        messageDao.update(
            entity.copy(
                status = MessageStatus.STOPPED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun failAssistantMessage(
        assistantMessageId: String
    ) {
        val entity = messageDao.getById(assistantMessageId) ?: return
        messageDao.update(
            entity.copy(
                status = MessageStatus.FAILED.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private companion object {
        private const val DEFAULT_SESSION_TITLE = "新会话"
        private const val TITLE_MAX_LEN = 20
    }
}
