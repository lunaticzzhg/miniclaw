package com.lunatic.miniclaw.feature.modelconfig.di

import com.lunatic.miniclaw.feature.modelconfig.presentation.ModelConfigViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val modelConfigKoinModule: Module = module {
    viewModelOf(::ModelConfigViewModel)
}
