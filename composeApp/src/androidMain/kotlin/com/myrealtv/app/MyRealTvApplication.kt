package com.myrealtv.app

import android.app.Application

class MyRealTvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MyRealTvApplication
            private set
    }
}
