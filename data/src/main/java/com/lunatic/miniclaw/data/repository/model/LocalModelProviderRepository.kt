package com.lunatic.miniclaw.data.repository.model

import com.lunatic.miniclaw.data.local.preferences.ProviderPreferencesStore
import com.lunatic.miniclaw.data.local.secure.ProviderSecretStore
import com.lunatic.miniclaw.data.mapper.model.ModelProviderConfigMapper
import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig
import com.lunatic.miniclaw.domain.model.model.ModelProviderId
import com.lunatic.miniclaw.domain.model.repository.ModelProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class LocalModelProviderRepository(
    private val providerPreferencesStore: ProviderPreferencesStore,
    private val providerSecretStore: ProviderSecretStore
) : ModelProviderRepository {
    private val mapper = ModelProviderConfigMapper()

    override fun observeCurrentProvider(): Flow<ModelProviderConfig?> {
        return providerPreferencesStore.observe()
            .map { preferences ->
                val providerId = preferences.currentProviderId
                val apiKey = providerId?.let(providerSecretStore::getApiKey)
                mapper.toDomain(preferences, apiKey)
            }
            .distinctUntilChanged()
    }

    override fun observeAvailability(): Flow<ModelAvailability> {
        return observeCurrentProvider()
            .map { config -> config.toAvailability() }
            .distinctUntilChanged()
    }

    override suspend fun getCurrentProvider(): ModelProviderConfig? {
        val preferences = providerPreferencesStore.read()
        val providerId = preferences.currentProviderId ?: return null
        val apiKey = providerSecretStore.getApiKey(providerId)
        return mapper.toDomain(preferences, apiKey)
    }

    override suspend fun saveProviderConfig(config: ModelProviderConfig) {
        providerPreferencesStore.save(mapper.toPreferences(config))
        providerSecretStore.saveApiKey(config.providerId.name, config.apiKey)
    }

    override suspend fun switchProvider(providerId: ModelProviderId) {
        val current = providerPreferencesStore.read()
        providerPreferencesStore.save(
            current.copy(
                currentProviderId = providerId.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun validateCurrentProvider(): ModelAvailability {
        return getCurrentProvider().toAvailability()
    }

    private fun ModelProviderConfig?.toAvailability(): ModelAvailability {
        if (this == null) return ModelAvailability.NotConfigured
        if (!isConfigured) return ModelAvailability.NotConfigured
        return ModelAvailability.Unknown
    }
}
