package com.lunatic.miniclaw.feature.sessionlist.presentation

data class SessionListUiState(
    val isLoading: Boolean = true,
    val sessions: List<SessionItemUiModel> = emptyList()
)

data class SessionItemUiModel(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtText: String
)

sealed interface SessionListIntent {
    data object CreateSessionClicked : SessionListIntent
}

sealed interface SessionListEffect {
    data class NavigateToChat(val sessionId: String) : SessionListEffect
}
