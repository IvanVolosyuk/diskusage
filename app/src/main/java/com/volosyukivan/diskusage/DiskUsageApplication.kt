package com.volosyukivan.diskusage

import android.app.Application
import android.util.Log
import timber.log.Timber

class DiskUsageApplication: Application() {
    companion object {
        private var instance: DiskUsageApplication? = null
        fun getInstance() = instance
            ?: throw IllegalStateException("DiskUsage application is not created!")

    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority < Log.INFO) return
                    super.log(priority, tag, message, t)
                }
            })
        }
    }
}