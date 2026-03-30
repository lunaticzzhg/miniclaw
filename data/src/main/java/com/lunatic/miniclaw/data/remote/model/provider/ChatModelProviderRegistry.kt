package com.lunatic.miniclaw.data.remote.model.provider

import com.lunatic.miniclaw.domain.model.model.ModelProviderId
import com.lunatic.miniclaw.domain.model.provider.ChatModelProvider

class ChatModelProviderRegistry(
    providers: List<ChatModelProvider>
) {
    private val providerMap: Map<ModelProviderId, ChatModelProvider> = providers.associateBy { it.providerId }

    fun get(providerId: ModelProviderId): ChatModelProvider {
        return providerMap[providerId]
            ?: error("ChatModelProvider not registered for providerId=$providerId")
    }
}
