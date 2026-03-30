package com.lunatic.miniclaw.data.repository

import androidx.room.withTransaction
import com.lunatic.miniclaw.data.local.dao.SessionDao
import com.lunatic.miniclaw.data.local.db.MiniClawDatabase
import com.lunatic.miniclaw.data.local.entity.SessionEntity
import com.lunatic.miniclaw.data.mapper.SessionEntityMapper
import com.lunatic.miniclaw.domain.session.model.ChatSession
import com.lunatic.miniclaw.domain.session.repository.SessionRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalSessionRepository(
    private val database: MiniClawDatabase,
    private val sessionDao: SessionDao
) : SessionRepository {
    private val mapper = SessionEntityMapper()

    override fun observeSessions(): Flow<List<ChatSession>> {
        return sessionDao.observeSessions().map { entities ->
            entities.map { mapper.toDomain(it) }
        }
    }

    override fun observeSession(sessionId: String): Flow<ChatSession?> {
        return sessionDao.observeById(sessionId).map { entity ->
            entity?.let { mapper.toDomain(it) }
        }
    }

    override suspend fun bootstrapIfNeeded(): String? {
        if (sessionDao.count() > 0) {
            return null
        }
        val sessionId = createSessionInternal()
        return sessionId
    }

    override suspend fun createSession(): String {
        return createSessionInternal()
    }

    private suspend fun createSessionInternal(): String {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = sessionId,
            title = DEFAULT_SESSION_TITLE,
            lastMessagePreview = null,
            updatedAt = now,
            createdAt = now
        )
        database.withTransaction {
            sessionDao.insert(session)
        }
        return sessionId
    }

    private companion object {
        private const val DEFAULT_SESSION_TITLE = "新会话"
    }
}
