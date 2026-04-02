package com.canary.devicecare

import android.app.Application

class DeviceCareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DeviceCareApp
            private set
    }
}
