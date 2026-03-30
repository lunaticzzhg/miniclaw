package com.lunatic.miniclaw.domain.session.repository

import com.lunatic.miniclaw.domain.session.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSessions(): Flow<List<ChatSession>>
    fun observeSession(sessionId: String): Flow<ChatSession?>
    suspend fun bootstrapIfNeeded(): String?
    suspend fun createSession(): String
}
