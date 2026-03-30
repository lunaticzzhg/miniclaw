package com.lunatic.miniclaw.domain.model.model

data class ModelProviderConfig(
    val providerId: ModelProviderId,
    val modelName: String,
    val apiKey: String?,
    val baseUrl: String?,
    val isConfigured: Boolean
)
