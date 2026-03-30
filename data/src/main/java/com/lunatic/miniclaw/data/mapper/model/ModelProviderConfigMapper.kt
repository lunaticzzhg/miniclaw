package com.lunatic.miniclaw.data.mapper.model

import com.lunatic.miniclaw.data.local.preferences.ProviderPreferences
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig
import com.lunatic.miniclaw.domain.model.model.ModelProviderId

class ModelProviderConfigMapper {
    fun toDomain(
        preferences: ProviderPreferences,
        apiKey: String?
    ): ModelProviderConfig? {
        val providerId = preferences.currentProviderId
            ?.let { raw -> runCatching { ModelProviderId.valueOf(raw) }.getOrNull() }
            ?: return null

        return ModelProviderConfig(
            providerId = providerId,
            modelName = preferences.modelName.orEmpty(),
            apiKey = apiKey,
            baseUrl = preferences.baseUrl,
            isConfigured = preferences.isConfigured
        )
    }

    fun toPreferences(domain: ModelProviderConfig): ProviderPreferences {
        return ProviderPreferences(
            currentProviderId = domain.providerId.name,
            modelName = domain.modelName,
            baseUrl = domain.baseUrl,
            isConfigured = domain.isConfigured,
            lastValidationStatus = null,
            updatedAt = System.currentTimeMillis()
        )
    }
}
