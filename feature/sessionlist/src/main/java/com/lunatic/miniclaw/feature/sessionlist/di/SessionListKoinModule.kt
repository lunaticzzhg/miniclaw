package com.lunatic.miniclaw.feature.sessionlist.di

import com.lunatic.miniclaw.feature.sessionlist.presentation.SessionListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sessionListKoinModule: Module = module {
    viewModelOf(::SessionListViewModel)
}
