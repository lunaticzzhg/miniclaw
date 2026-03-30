package com.lunatic.miniclaw.core.di

import org.koin.core.module.Module

interface KoinModuleProvider {
    fun modules(): List<Module>
}
