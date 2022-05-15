package com.google.android.diskusage.utils

import com.google.android.diskusage.DiskUsageApplication
import java.io.File

object PathHelper {
    @JvmStatic
    fun getExternalAppFilesPaths(): Array<out File> {
        return DiskUsageApplication.getInstance()
            .getExternalFilesDirs(null)
    }
}