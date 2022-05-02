package com.google.android.diskusage.utils

import com.google.android.diskusage.DiskUsageApplication

object AppHelper {

    @JvmStatic
    val appContext get() = DiskUsageApplication.getInstance().applicationContext
}