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
        if (activeSessionRequests.containsKey(sessionId)) return

        val normalized = text.trim()
        if (normalized.isEmpty()) return

        val session = sessionDao.getById(sessionId) ?: return
        val now = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        val userMessage = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            requestId = requestId,
            role = MessageRole.USER.name,
            content = normalized,
            status = MessageStatus.SENDING.name,
            createdAt = now,
            updatedAt = now
        )

        database.withTransaction {
            messageDao.insert(userMessage)
            sessionDao.updateSummary(
                sessionId = sessionId,
                title = resolveNextTitle(session.title, normalized),
                preview = normalized,
                updatedAt = now
            )
        }

        submitAndStartStreaming(
            sessionId = sessionId,
            userMessage = userMessage,
            requestId = requestId,
            userText = normalized,
            assistantMessageId = null
        )
    }

    override suspend fun retryUserMessage(messageId: String) {
        val userMessage = messageDao.getById(messageId) ?: return
        if (userMessage.role != MessageRole.USER.name || userMessage.status != MessageStatus.SEND_FAILED.name) return
        if (activeSessionRequests.containsKey(userMessage.sessionId)) return

        val requestId = UUID.randomUUID().toString()
        val resettingUser = userMessage.copy(
            requestId = requestId,
            status = MessageStatus.SENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        messageDao.update(resettingUser)

        submitAndStartStreaming(
            sessionId = userMessage.sessionId,
            userMessage = resettingUser,
            requestId = requestId,
            userText = userMessage.content,
            assistantMessageId = null
        )
    }

    override suspend fun retryAssistantMessage(messageId: String) {
        val assistant = messageDao.getById(messageId) ?: return
        if (assistant.role != MessageRole.ASSISTANT.name || assistant.status != MessageStatus.FAILED.name) return
        if (activeSessionRequests.containsKey(assistant.sessionId)) return

        val relatedUserMessage = messageDao.getByRequestId(assistant.requestId.orEmpty())
            .firstOrNull { it.role == MessageRole.USER.name }
            ?: return

        val newRequestId = UUID.randomUUID().toString()
        database.withTransaction {
            messageDao.update(
                relatedUserMessage.copy(
                    requestId = newRequestId,
                    status = MessageStatus.SENT.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
            messageDao.update(
                assistant.copy(
                    requestId = newRequestId,
                    content = "",
                    status = MessageStatus.THINKING.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        submitAndStartStreaming(
            sessionId = assistant.sessionId,
            userMessage = relatedUserMessage.copy(requestId = newRequestId),
            requestId = newRequestId,
            userText = relatedUserMessage.content,
            assistantMessageId = assistant.id
        )
    }

    override suspend fun stopStreaming(requestId: String) {
        requestJobs[requestId]?.cancel()
    }

    private suspend fun submitAndStartStreaming(
        sessionId: String,
        userMessage: MessageEntity,
        requestId: String,
        userText: String,
        assistantMessageId: String?
    ) {
        val request = ChatStreamRequest(sessionId = sessionId, userText = userText)

        try {
            remoteDataSource.startChat(request)
        } catch (_: Throwable) {
            messageDao.update(
                userMessage.copy(
                    requestId = requestId,
                    status = MessageStatus.SEND_FAILED.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
            return
        }

        var targetAssistantId = assistantMessageId
        if (targetAssistantId == null) {
            val assistant = MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                requestId = requestId,
                role = MessageRole.ASSISTANT.name,
                content = "",
                status = MessageStatus.THINKING.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.withTransaction {
                messageDao.update(
                    userMessage.copy(
                        requestId = requestId,
                        status = MessageStatus.SENT.name,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                messageDao.insert(assistant)
            }
            targetAssistantId = assistant.id
        } else {
            messageDao.update(
                userMessage.copy(
                    requestId = requestId,
                    status = MessageStatus.SENT.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        val finalAssistantId = targetAssistantId ?: return
        startStreaming(
            sessionId = sessionId,
            requestId = requestId,
            assistantMessageId = finalAssistantId,
            request = request
        )
    }

    private fun startStreaming(
        sessionId: String,
        requestId: String,
        assistantMessageId: String,
        request: ChatStreamRequest
    ) {
        activeSessionRequests[sessionId] = requestId

        val job = repositoryScope.launch {
            try {
                remoteDataSource.streamChat(request).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Started -> {
                            updateAssistantStatus(assistantMessageId, MessageStatus.STREAMING)
                        }

                        is ChatStreamEvent.Delta -> {
                            appendAssistantDelta(assistantMessageId, event.textChunk)
                        }

                        is ChatStreamEvent.Completed -> {
                            completeAssistantMessage(sessionId, assistantMessageId)
                        }

                        is ChatStreamEvent.Failed -> {
                            failAssistantMessage(assistantMessageId)
                        }
                    }
                }
            } catch (_: CancellationException) {
                stopAssistantMessage(assistantMessageId)
            } catch (_: Throwable) {
                failAssistantMessage(assistantMessageId)
            } finally {
                requestJobs.remove(requestId)
                activeSessionRequests.remove(sessionId, requestId)
            }
        }

        requestJobs[requestId] = job
    }

    private suspend fun updateAssistantStatus(assistantMessageId: String, status: MessageStatus) {
        val entity = messageDao.getById(assistantMessageId) ?: return
        messageDao.update(entity.copy(status = status.name, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun appendAssistantDelta(assistantMessageId: String, textChunk: String) {
        val entity = messageDao.getById(assistantMessageId) ?: return
        messageDao.update(
            entity.copy(
                content = entity.content + textChunk,
                status = MessageStatus.STREAMING.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun completeAssistantMessage(sessionId: String, assistantMessageId: String) {
        val now = System.currentTimeMillis()
        val entity = messageDao.getById(assistantMessageId) ?: return
        val finalContent = entity.content
        database.withTransaction {
            messageDao.update(entity.copy(status = MessageStatus.COMPLETED.name, updatedAt = now))
            sessionDao.updateSummary(
                sessionId = sessionId,
                title = sessionDao.getById(sessionId)?.title ?: DEFAULT_SESSION_TITLE,
                preview = finalContent,
                updatedAt = now
            )
        }
    }

    private suspend fun stopAssistantMessage(assistantMessageId: String) {
        val entity = messageDao.getById(assistantMessageId) ?: return
        messageDao.update(entity.copy(status = MessageStatus.STOPPED.name, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun failAssistantMessage(assistantMessageId: String) {
        val entity = messageDao.getById(assistantMessageId) ?: return
        messageDao.update(entity.copy(status = MessageStatus.FAILED.name, updatedAt = System.currentTimeMillis()))
    }

    private fun resolveNextTitle(currentTitle: String, userText: String): String {
        return if (currentTitle == DEFAULT_SESSION_TITLE) userText.take(TITLE_MAX_LEN) else currentTitle
    }

    private companion object {
        private const val DEFAULT_SESSION_TITLE = "新会话"
        private const val TITLE_MAX_LEN = 20
    }
}
