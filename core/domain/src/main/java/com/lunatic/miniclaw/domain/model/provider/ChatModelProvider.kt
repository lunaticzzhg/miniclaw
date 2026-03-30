package com.lunatic.miniclaw.domain.model.provider

import com.lunatic.miniclaw.domain.model.model.ChatModelRequest
import com.lunatic.miniclaw.domain.model.model.ChatStreamEvent
import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig
import com.lunatic.miniclaw.domain.model.model.ModelProviderId
import kotlinx.coroutines.flow.Flow

interface ChatModelProvider {
    val providerId: ModelProviderId

    suspend fun validate(config: ModelProviderConfig): ModelAvailability

    fun streamChat(
        config: ModelProviderConfig,
        request: ChatModelRequest
    ): Flow<ChatStreamEvent>
}
