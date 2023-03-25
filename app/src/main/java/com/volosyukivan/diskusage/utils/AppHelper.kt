package com.volosyukivan.diskusage.utils

import com.volosyukivan.diskusage.DiskUsageApplication

object AppHelper {

    @JvmStatic
    val appContext get() = DiskUsageApplication.getInstance().applicationContext
}