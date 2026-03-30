package com.lunatic.miniclaw.feature.sessionlist.di

import com.lunatic.miniclaw.core.di.KoinModuleProvider
import org.koin.core.module.Module

class SessionListKoinModuleProvider : KoinModuleProvider {
    override fun modules(): List<Module> = listOf(sessionListKoinModule)
}
