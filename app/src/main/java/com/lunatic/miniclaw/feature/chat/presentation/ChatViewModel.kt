package com.lunatic.miniclaw.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.miniclaw.domain.chat.model.MessageRole
import com.lunatic.miniclaw.domain.chat.model.MessageStatus
import com.lunatic.miniclaw.domain.chat.repository.ChatRepository
import com.lunatic.miniclaw.domain.session.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val sessionId: String,
    private val sessionRepository: SessionRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeSession()
        observeMessages()
    }

    fun onInputChanged(text: String) {
        _uiState.update {
            it.copy(
                inputText = text,
                canSend = text.isNotBlank() && !it.canStop
            )
        }
    }

    fun onSendClicked() {
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

    fun onStopClicked() {
        val requestId = _uiState.value.activeRequestId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatRepository.stopStreaming(requestId)
            }
        }
    }

    fun onRetryUserMessageClicked(messageId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                chatRepository.retryUserMessage(messageId)
            }
        }
    }

    fun onRetryAssistantMessageClicked(messageId: String) {
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

    private fun MessageStatus.toStatusText(): String? {
        return when (this) {
            MessageStatus.SENDING -> "发送中"
            MessageStatus.SENT -> null
            MessageStatus.SEND_FAILED -> "发送失败"
            MessageStatus.THINKING -> "思考中"
            MessageStatus.STREAMING -> "回复中"
            MessageStatus.COMPLETED -> null
            MessageStatus.FAILED -> "回复失败"
            MessageStatus.STOPPED -> "已停止"
        }
    }
}
