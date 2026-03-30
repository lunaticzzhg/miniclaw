package com.lunatic.miniclaw.data.repository.model

import com.lunatic.miniclaw.data.local.preferences.ProviderPreferencesStore
import com.lunatic.miniclaw.data.local.preferences.ProviderPreferences
import com.lunatic.miniclaw.data.local.secure.ProviderSecretStore
import com.lunatic.miniclaw.data.mapper.model.ModelProviderConfigMapper
import com.lunatic.miniclaw.data.remote.model.provider.ChatModelProviderRegistry
import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig
import com.lunatic.miniclaw.domain.model.model.ModelProviderId
import com.lunatic.miniclaw.domain.model.repository.ModelProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class LocalModelProviderRepository(
    private val providerPreferencesStore: ProviderPreferencesStore,
    private val providerSecretStore: ProviderSecretStore,
    private val providerRegistry: ChatModelProviderRegistry
) : ModelProviderRepository {
    private val mapper = ModelProviderConfigMapper()
    private val configValidator = ProviderConfigValidator()
    private val errorMapper = ModelErrorMapper()

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
        return providerPreferencesStore.observe()
            .map { preferences ->
                val config = preferences.toDomainConfig()
                val configInvalidStatus = configValidator.validate(config)
                if (configInvalidStatus != null) {
                    return@map configInvalidStatus
                }
                ModelAvailabilityStorageMapper.fromStorageValue(preferences.lastValidationStatus)
                    ?: ModelAvailability.Unknown
            }
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
        val currentConfig = getCurrentProvider()
        val configInvalidStatus = configValidator.validate(currentConfig)
        if (configInvalidStatus != null) {
            providerPreferencesStore.updateLastValidationStatus(
                ModelAvailabilityStorageMapper.toStorageValue(configInvalidStatus)
            )
            return configInvalidStatus
        }

        val config = currentConfig ?: return ModelAvailability.NotConfigured
        val provider = providerRegistry.find(config.providerId)
        if (provider == null) {
            providerPreferencesStore.updateLastValidationStatus(
                ModelAvailabilityStorageMapper.toStorageValue(ModelAvailability.ServiceUnavailable)
            )
            return ModelAvailability.ServiceUnavailable
        }

        val validatedStatus = runCatching { provider.validate(config) }
            .getOrElse { throwable -> errorMapper.toAvailability(throwable) }
        providerPreferencesStore.updateLastValidationStatus(
            ModelAvailabilityStorageMapper.toStorageValue(validatedStatus)
        )
        return validatedStatus
    }

    private fun ProviderPreferences.toDomainConfig(): ModelProviderConfig? {
        val providerId = currentProviderId ?: return null
        val apiKey = providerSecretStore.getApiKey(providerId)
        return mapper.toDomain(this, apiKey)
    }
}
