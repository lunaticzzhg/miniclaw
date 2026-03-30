package com.lunatic.miniclaw.di

import com.lunatic.miniclaw.core.di.KoinModuleProvider
import org.koin.core.module.Module
import java.util.ServiceLoader

val appModules: List<Module> = ServiceLoader
    .load(KoinModuleProvider::class.java)
    .toList()
    .sortedBy { it::class.java.name }
    .flatMap { it.modules() }
    .ifEmpty {
        error("No Koin modules found. Check KoinModuleProvider service registration.")
    }
