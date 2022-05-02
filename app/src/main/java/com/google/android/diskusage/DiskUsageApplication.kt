package com.google.android.diskusage

import android.app.Application

class DiskUsageApplication: Application() {
    companion object {
        private var instance: DiskUsageApplication? = null
        fun getInstance() = instance
            ?: throw IllegalStateException("DiskUsage application is not created!")

    }
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}