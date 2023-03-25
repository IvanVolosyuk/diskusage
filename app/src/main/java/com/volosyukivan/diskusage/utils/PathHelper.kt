package com.volosyukivan.diskusage.utils

import com.volosyukivan.diskusage.DiskUsageApplication
import java.io.File

object PathHelper {
    @JvmStatic
    fun getExternalAppFilesPaths(): Array<out File> {
        return DiskUsageApplication.getInstance()
            .getExternalFilesDirs(null)
    }
}