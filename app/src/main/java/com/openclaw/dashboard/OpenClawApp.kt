package com.openclaw.dashboard

import android.app.Application

class OpenClawApp : Application() {
    
    companion object {
        lateinit var instance: OpenClawApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
