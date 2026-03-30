package com.lunatic.miniclaw.data.di

import android.content.Context
import androidx.room.Room
import com.lunatic.miniclaw.data.local.db.MiniClawDatabase
import com.lunatic.miniclaw.data.local.preferences.ProviderPreferencesStore
import com.lunatic.miniclaw.data.local.secure.ProviderSecretStore
import com.lunatic.miniclaw.data.remote.datasource.ChatRemoteDataSource
import com.lunatic.miniclaw.data.remote.datasource.FakeChatRemoteDataSource
import com.lunatic.miniclaw.data.remote.model.provider.ChatModelProviderRegistry
import com.lunatic.miniclaw.data.remote.model.provider.FakeChatModelProvider
import com.lunatic.miniclaw.data.repository.LocalChatRepository
import com.lunatic.miniclaw.data.repository.LocalSessionRepository
import com.lunatic.miniclaw.data.repository.model.LocalModelProviderRepository
import com.lunatic.miniclaw.domain.chat.repository.ChatRepository
import com.lunatic.miniclaw.domain.model.provider.ChatModelProvider
import com.lunatic.miniclaw.domain.model.repository.ModelProviderRepository
import com.lunatic.miniclaw.domain.session.repository.SessionRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val dataKoinModule: Module = module {
    single {
        Room.databaseBuilder(
            get(),
            MiniClawDatabase::class.java,
            DATABASE_NAME
        ).build()
    }
    single { get<MiniClawDatabase>().sessionDao() }
    single { get<MiniClawDatabase>().messageDao() }
    single { ProviderPreferencesStore(get<Context>()) }
    single { ProviderSecretStore(get<Context>()) }
    single<ChatRemoteDataSource> { FakeChatRemoteDataSource() }
    single<ChatModelProvider> { FakeChatModelProvider(get()) }
    single { ChatModelProviderRegistry(providers = getAll()) }
    single<SessionRepository> { LocalSessionRepository(get(), get()) }
    single<ChatRepository> { LocalChatRepository(get(), get(), get(), get()) }
    single<ModelProviderRepository> { LocalModelProviderRepository(get(), get()) }
}

private const val DATABASE_NAME = "miniclaw.db"
