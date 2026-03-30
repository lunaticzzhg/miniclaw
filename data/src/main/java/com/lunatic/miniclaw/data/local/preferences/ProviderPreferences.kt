package com.lunatic.miniclaw.data.local.preferences

data class ProviderPreferences(
    val currentProviderId: String? = null,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val isConfigured: Boolean = false,
    val lastValidationStatus: String? = null,
    val updatedAt: Long? = null
)
