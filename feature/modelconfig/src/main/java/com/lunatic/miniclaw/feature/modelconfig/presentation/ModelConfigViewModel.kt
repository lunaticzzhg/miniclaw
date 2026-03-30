package com.lunatic.miniclaw.feature.modelconfig.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.miniclaw.domain.model.model.ModelAvailability
import com.lunatic.miniclaw.domain.model.model.ModelProviderConfig
import com.lunatic.miniclaw.domain.model.model.ModelProviderId
import com.lunatic.miniclaw.domain.model.repository.ModelProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelConfigViewModel(
    private val modelProviderRepository: ModelProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelConfigUiState())
    val uiState: StateFlow<ModelConfigUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ModelConfigEffect>()
    val effects: SharedFlow<ModelConfigEffect> = _effects.asSharedFlow()

    fun onIntent(intent: ModelConfigIntent) {
        when (intent) {
            ModelConfigIntent.ScreenStarted -> loadCurrentConfig()
            is ModelConfigIntent.ProviderSelected -> updateProvider(intent.providerId)
            is ModelConfigIntent.ApiKeyChanged -> updateInputs(apiKey = intent.value)
            is ModelConfigIntent.ModelNameChanged -> updateInputs(modelName = intent.value)
            is ModelConfigIntent.BaseUrlChanged -> updateInputs(baseUrl = intent.value)
            ModelConfigIntent.SaveClicked -> saveConfig()
            ModelConfigIntent.ValidateClicked -> validateConfig()
            ModelConfigIntent.BackToChatClicked -> navigateBack()
        }
    }

    private fun loadCurrentConfig() {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { modelProviderRepository.getCurrentProvider() }
            if (config == null) {
                _uiState.update { state -> state.copy(canSave = computeCanSave(state.modelName, state.apiKeyInput)) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    selectedProvider = config.providerId,
                    modelName = config.modelName,
                    apiKeyInput = config.apiKey.orEmpty(),
                    baseUrlInput = config.baseUrl.orEmpty(),
                    canSave = computeCanSave(config.modelName, config.apiKey.orEmpty())
                )
            }
        }
    }

    private fun updateProvider(providerId: ModelProviderId) {
        _uiState.update { it.copy(selectedProvider = providerId) }
    }

    private fun updateInputs(
        modelName: String? = null,
        apiKey: String? = null,
        baseUrl: String? = null
    ) {
        _uiState.update { state ->
            val nextModelName = modelName ?: state.modelName
            val nextApiKey = apiKey ?: state.apiKeyInput
            val nextBaseUrl = baseUrl ?: state.baseUrlInput
            state.copy(
                modelName = nextModelName,
                apiKeyInput = nextApiKey,
                baseUrlInput = nextBaseUrl,
                availabilityText = null,
                canSave = computeCanSave(nextModelName, nextApiKey)
            )
        }
    }

    private fun saveConfig() {
        val state = _uiState.value
        if (!state.canSave || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    modelProviderRepository.saveProviderConfig(state.toDomainConfig())
                }
            }
            _uiState.update { it.copy(isSaving = false) }
            if (result.isSuccess) {
                _effects.emit(ModelConfigEffect.ShowToast("配置已保存"))
            } else {
                _effects.emit(ModelConfigEffect.ShowToast("保存失败，请重试"))
            }
        }
    }

    private fun validateConfig() {
        val state = _uiState.value
        if (!state.canSave || state.isValidating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true, availabilityText = "校验中...") }

            val availability = runCatching {
                withContext(Dispatchers.IO) {
                    modelProviderRepository.saveProviderConfig(state.toDomainConfig())
                    modelProviderRepository.validateCurrentProvider()
                }
            }.getOrElse { ModelAvailability.Unknown }

            _uiState.update {
                it.copy(
                    isValidating = false,
                    availabilityText = availability.toUiText()
                )
            }
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effects.emit(ModelConfigEffect.NavigateBack)
        }
    }

    private fun ModelConfigUiState.toDomainConfig(): ModelProviderConfig {
        return ModelProviderConfig(
            providerId = selectedProvider,
            modelName = modelName.trim(),
            apiKey = apiKeyInput.trim().ifEmpty { null },
            baseUrl = baseUrlInput.trim().ifEmpty { null },
            isConfigured = computeCanSave(modelName, apiKeyInput)
        )
    }

    private fun ModelAvailability.toUiText(): String {
        return when (this) {
            ModelAvailability.Available -> "已连接"
            ModelAvailability.NotConfigured -> "模型未配置"
            ModelAvailability.Validating -> "校验中"
            ModelAvailability.AuthFailed -> "配置无效，请检查鉴权"
            ModelAvailability.NetworkUnavailable -> "网络异常，请重试"
            ModelAvailability.ServiceUnavailable -> "服务暂不可用"
            ModelAvailability.Unknown -> "校验失败，请稍后重试"
        }
    }

    private fun computeCanSave(modelName: String, apiKey: String): Boolean {
        return modelName.trim().isNotBlank() && apiKey.trim().isNotBlank()
    }
}
