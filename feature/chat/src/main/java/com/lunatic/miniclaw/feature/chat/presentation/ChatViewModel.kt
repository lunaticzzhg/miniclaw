package com.lunatic.miniclaw.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.domain.chat.model.MessageStatus
import com.lunatic.miniclaw.domain.chat.repository.ChatRepository
import com.lunatic.miniclaw.domain.model.repository.ModelProviderRepository
import com.lunatic.miniclaw.domain.session.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val sessionId: String,
    private val sessionRepository: SessionRepository,
    private val chatRepository: ChatRepository,
    private val modelProviderRepository: ModelProviderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<ChatEffect>()
    val effects: SharedFlow<ChatEffect> = _effects.asSharedFlow()

    init {
        observeSession()
        observeMessages()
        observeModelState()
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputChanged -> handleInputChanged(intent.text)
            ChatIntent.SendClicked -> handleSendClicked()
            ChatIntent.StopClicked -> handleStopClicked()
            ChatIntent.ModelSwitcherClicked -> handleModelSwitcherClicked()
            is ChatIntent.RetryUserMessageClicked -> handleRetryUserMessage(intent.messageId)
            is ChatIntent.RetryAssistantMessageClicked -> handleRetryAssistantMessage(intent.messageId)
        }
    }

    private fun handleModelSwitcherClicked() {
        viewModelScope.launch {
            _effects.emit(ChatEffect.NavigateToModelConfig(sessionId))
        }
    }

    private fun handleInputChanged(text: String) {
        _uiState.update {
            it.copy(
                inputText = text,
                canSend = text.isNotBlank() && !it.canStop
            )
        }
    }

    private fun handleSendClicked() {
        val current = _uiState.value
        if (!current.canSend) {
            return
        }
        val text = current.inputText.trim()
        if (text.isEmpty()) {
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatRepository.sendUserMessage(sessionId = sessionId, text = text)
            }
            _uiState.update {
                it.copy(inputText = "", canSend = false)
            }
        }
    }

    private fun handleStopClicked() {
        val requestId = _uiState.value.activeRequestId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatRepository.stopStreaming(requestId)
            }
        }
    }

    private fun handleRetryUserMessage(messageId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatRepository.retryUserMessage(messageId)
            }
        }
    }

    private fun handleRetryAssistantMessage(messageId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatRepository.retryAssistantMessage(messageId)
            }
        }
    }

    private fun observeSession() {
        viewModelScope.launch {
            sessionRepository.observeSession(sessionId).collectLatest { session ->
                if (session != null) {
                    _uiState.update { it.copy(title = session.title) }
                }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages(sessionId).collectLatest { messages ->
                val activeAssistant = messages.lastOrNull {
                    it.role == MessageRole.ASSISTANT &&
                        (it.status == MessageStatus.THINKING || it.status == MessageStatus.STREAMING)
                }
                _uiState.update { state ->
                    state.copy(
                        messages = messages.map { message ->
                            ChatMessageItemUiModel(
                                id = message.id,
                                role = message.role,
                                content = message.content,
                                status = message.status,
                                statusText = message.status.toStatusText(),
                                showRetry = message.status == MessageStatus.SEND_FAILED ||
                                    message.status == MessageStatus.FAILED
                            )
                        },
                        canStop = activeAssistant != null,
                        activeRequestId = activeAssistant?.requestId,
                        canSend = state.inputText.isNotBlank() && activeAssistant == null
                    )
                }
            }
        }
    }

    private fun observeModelState() {
        viewModelScope.launch {
            modelProviderRepository.observeCurrentProvider()
                .combine(modelProviderRepository.observeAvailability()) { provider, availability ->
                    provider to availability
                }
                .collectLatest { (provider, availability) ->
                    _uiState.update { state ->
                        state.copy(
                            currentProviderId = provider?.providerId,
                            currentProviderLabel = provider?.providerId.toUiLabel(),
                            availabilityText = availability.toUiText()
                        )
                    }
                }
        }
    }
}
