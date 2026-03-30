package com.lunatic.miniclaw.feature.modelconfig.presentation

import com.lunatic.miniclaw.domain.model.model.ModelProviderId

data class ModelConfigUiState(
    val selectedProvider: ModelProviderId = ModelProviderId.MINIMAX,
    val modelName: String = "",
    val apiKeyInput: String = "",
    val baseUrlInput: String = "",
    val isSaving: Boolean = false,
    val isValidating: Boolean = false,
    val availabilityText: String? = null,
    val canSave: Boolean = false
)

sealed interface ModelConfigIntent {
    data object ScreenStarted : ModelConfigIntent
    data class ProviderSelected(val providerId: ModelProviderId) : ModelConfigIntent
    data class ApiKeyChanged(val value: String) : ModelConfigIntent
    data class ModelNameChanged(val value: String) : ModelConfigIntent
    data class BaseUrlChanged(val value: String) : ModelConfigIntent
    data object SaveClicked : ModelConfigIntent
    data object ValidateClicked : ModelConfigIntent
    data object BackToChatClicked : ModelConfigIntent
}

sealed interface ModelConfigEffect {
    data object NavigateBack : ModelConfigEffect
    data class ShowToast(val message: String) : ModelConfigEffect
}
