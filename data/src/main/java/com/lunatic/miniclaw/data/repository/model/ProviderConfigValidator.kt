package com.lunatic.miniclaw.data.repository.model

import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig

class ProviderConfigValidator {
    fun validate(config: ModelProviderConfig?): ModelAvailability? {
        if (config == null) return ModelAvailability.NotConfigured
        if (!config.isConfigured) return ModelAvailability.NotConfigured
        if (config.modelName.isBlank()) return ModelAvailability.NotConfigured
        if (config.apiKey.isNullOrBlank()) return ModelAvailability.NotConfigured
        return null
    }
}
