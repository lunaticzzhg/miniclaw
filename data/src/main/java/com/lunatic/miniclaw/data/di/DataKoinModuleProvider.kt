package com.lunatic.miniclaw.data.di

import com.lunatic.miniclaw.core.di.KoinModuleProvider
import org.koin.core.module.Module

class DataKoinModuleProvider : KoinModuleProvider {
    override fun modules(): List<Module> = listOf(dataKoinModule)
}
