package com.lunatic.miniclaw.feature.sessionlist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.miniclaw.domain.session.model.ChatSession
import com.lunatic.miniclaw.domain.session.repository.SessionRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionListViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SessionListEffect>()
    val effects: SharedFlow<SessionListEffect> = _effects.asSharedFlow()

    init {
        observeSessions()
        bootstrapSessionIfNeeded()
    }

    fun onIntent(intent: SessionListIntent) {
        when (intent) {
            SessionListIntent.CreateSessionClicked -> handleCreateSessionClicked()
        }
    }

    private fun handleCreateSessionClicked() {
        viewModelScope.launch {
            val sessionId = withContext(Dispatchers.IO) {
                sessionRepository.createSession()
            }
            _effects.emit(SessionListEffect.NavigateToChat(sessionId))
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            sessionRepository.observeSessions().collectLatest { sessions ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = sessions.map(::toUiModel)
                    )
                }
            }
        }
    }

    private fun bootstrapSessionIfNeeded() {
        viewModelScope.launch {
            val sessionId = withContext(Dispatchers.IO) {
                sessionRepository.bootstrapIfNeeded()
            }
            if (sessionId != null) {
                _effects.emit(SessionListEffect.NavigateToChat(sessionId))
            }
        }
    }

    private fun toUiModel(session: ChatSession): SessionItemUiModel {
        return SessionItemUiModel(
            id = session.id,
            title = session.title,
            preview = session.lastMessagePreview ?: "今天想聊点什么？",
            updatedAtText = formatter.format(Instant.ofEpochMilli(session.updatedAt))
        )
    }

    private companion object {
        val formatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }
}
