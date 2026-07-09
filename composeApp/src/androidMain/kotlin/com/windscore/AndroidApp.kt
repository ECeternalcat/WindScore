package com.windscore

import android.app.Application
import android.content.Context

class AndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
