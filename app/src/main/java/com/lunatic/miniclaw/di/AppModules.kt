package com.lunatic.miniclaw.di

import com.lunatic.miniclaw.data.di.dataKoinModule
import com.lunatic.miniclaw.feature.chat.di.chatKoinModule
import com.lunatic.miniclaw.feature.sessionlist.di.sessionListKoinModule
import org.koin.core.module.Module

val appModules: List<Module> = listOf(
    dataKoinModule,
    sessionListKoinModule,
    chatKoinModule
)
