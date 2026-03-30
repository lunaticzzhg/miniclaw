package com.lunatic.miniclaw.app

import android.app.Application
import com.lunatic.miniclaw.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MiniClawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MiniClawApp)
            modules(appModules)
        }
    }
}
