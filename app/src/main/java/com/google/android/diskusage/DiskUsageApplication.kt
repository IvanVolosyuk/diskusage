package com.google.android.diskusage

import android.app.Application
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
        try {
            if (BuildConfig.DEBUG) {
                Timber.plant(
                    object : Timber.DebugTree() {
                        override fun createStackElementTag(element: StackTraceElement): String =
                            "${super.createStackElementTag(element)}|${element.fileName}:${element.lineNumber}"

                        override fun log(
                            priority: Int,
                            tag: String?,
                            message: String,
                            t: Throwable?,
                        ) {
                            super.log(
                                priority,
                                "[${Thread.currentThread().name}] ${tag?.substringBefore('|')}",
                                "${tag?.substringAfter('|')}] $message",
                                t,
                            )
                        }
                    },
                )
            }
        } catch (e: Exception) {
            e.fillInStackTrace()
            return
        }
    }
}