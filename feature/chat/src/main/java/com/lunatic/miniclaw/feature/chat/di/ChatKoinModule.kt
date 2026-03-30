package com.lunatic.miniclaw.feature.chat.di

import com.lunatic.miniclaw.feature.chat.presentation.ChatViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val chatKoinModule: Module = module {
    viewModelOf(::ChatViewModel)
}
