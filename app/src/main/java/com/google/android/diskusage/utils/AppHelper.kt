package com.google.android.diskusage.utils

import android.content.Context
import com.google.android.diskusage.DiskUsageApplication

object AppHelper {

    @JvmStatic
    val appContext: Context get() = DiskUsageApplication.getInstance().applicationContext
}