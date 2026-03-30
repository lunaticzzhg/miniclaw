package com.lunatic.miniclaw.feature.modelconfig.di

import com.lunatic.miniclaw.core.di.KoinModuleProvider
import org.koin.core.module.Module

class ModelConfigKoinModuleProvider : KoinModuleProvider {
    override fun modules(): List<Module> = listOf(modelConfigKoinModule)
}
