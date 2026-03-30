package com.lunatic.miniclaw.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lunatic.miniclaw.data.local.dao.MessageDao
import com.lunatic.miniclaw.data.local.dao.SessionDao
import com.lunatic.miniclaw.data.local.entity.MessageEntity
import com.lunatic.miniclaw.data.local.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MiniClawDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
}
