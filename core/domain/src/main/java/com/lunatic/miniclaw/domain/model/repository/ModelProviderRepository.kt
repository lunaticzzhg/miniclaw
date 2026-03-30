package com.lunatic.miniclaw.domain.model.repository

import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig
import com.lunatic.miniclaw.domain.model.model.ModelProviderId
import kotlinx.coroutines.flow.Flow

interface ModelProviderRepository {
    fun observeCurrentProvider(): Flow<ModelProviderConfig?>
    fun observeAvailability(): Flow<ModelAvailability>
    suspend fun getCurrentProvider(): ModelProviderConfig?
    suspend fun saveProviderConfig(config: ModelProviderConfig)
    suspend fun switchProvider(providerId: ModelProviderId)
    suspend fun validateCurrentProvider(): ModelAvailability
}
