package com.lunatic.miniclaw.data.remote.model.provider

import com.lunatic.miniclaw.data.remote.datasource.ChatRemoteDataSource
import com.lunatic.miniclaw.data.remote.model.ChatStreamEvent as RemoteChatStreamEvent
import com.lunatic.miniclaw.data.remote.model.ChatStreamRequest
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.domain.model.model.ChatModelRequest
import com.lunatic.miniclaw.domain.model.model.ChatStreamEvent
import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelCallError
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig
import com.lunatic.miniclaw.domain.model.model.ModelProviderId
import com.lunatic.miniclaw.domain.model.provider.ChatModelProvider
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FakeChatModelProvider(
    private val remoteDataSource: ChatRemoteDataSource
) : ChatModelProvider {

    override val providerId: ModelProviderId = ModelProviderId.MINIMAX

    override suspend fun validate(config: ModelProviderConfig): ModelAvailability {
        return if (config.isConfigured) ModelAvailability.Available else ModelAvailability.NotConfigured
    }

    override fun streamChat(config: ModelProviderConfig, request: ChatModelRequest): Flow<ChatStreamEvent> {
        val latestUserMessage = request.messages
            .lastOrNull { it.role == MessageRole.USER }
            ?.content
            .orEmpty()
        val remoteRequest = ChatStreamRequest(
            sessionId = request.sessionId,
            userText = latestUserMessage
        )
        return flow {
            try {
                remoteDataSource.startChat(remoteRequest)
            } catch (_: Throwable) {
                emit(ChatStreamEvent.Failed(ModelCallError.Unknown))
                return@flow
            }

            remoteDataSource.streamChat(remoteRequest)
                .map { event -> event.toDomainEvent() }
                .collect { event -> emit(event) }
        }
    }

    private fun RemoteChatStreamEvent.toDomainEvent(): ChatStreamEvent {
        return when (this) {
            RemoteChatStreamEvent.Started -> ChatStreamEvent.Started
            is RemoteChatStreamEvent.Delta -> ChatStreamEvent.Delta(textChunk = textChunk)
            RemoteChatStreamEvent.Completed -> ChatStreamEvent.Completed
            is RemoteChatStreamEvent.Failed -> ChatStreamEvent.Failed(ModelCallError.Unknown)
        }
    }
}
