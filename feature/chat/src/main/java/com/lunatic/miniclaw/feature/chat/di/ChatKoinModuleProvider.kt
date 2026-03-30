package com.lunatic.miniclaw.feature.chat.di

import com.lunatic.miniclaw.core.di.KoinModuleProvider
import org.koin.core.module.Module

class ChatKoinModuleProvider : KoinModuleProvider {
    override fun modules(): List<Module> = listOf(chatKoinModule)
}
