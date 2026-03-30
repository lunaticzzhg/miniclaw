package com.lunatic.miniclaw.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                canSend = text.isNotBlank()
            )
        }
    }

    fun onSendClicked() {
        val text = _uiState.value.inputText.trim()
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
                _uiState.update {
                    it.copy(
                        messages = messages.map { message ->
                            ChatMessageItemUiModel(
                                id = message.id,
                                role = message.role,
                                content = message.content
                            )
                        }
                    )
                }
            }
        }
    }
}
