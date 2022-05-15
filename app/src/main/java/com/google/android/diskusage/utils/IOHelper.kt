package com.google.android.diskusage.utils

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader

object IOHelper {
    private const val PROC_MOUNTS = "/proc/mounts"

    @JvmStatic
    @Throws(IOException::class)
    fun getProcMountsReader(): BufferedReader
        = BufferedReader(InputStreamReader(FileInputStream(PROC_MOUNTS)))

}