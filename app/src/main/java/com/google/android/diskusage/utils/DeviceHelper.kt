package com.google.android.diskusage.utils

import java.io.File

object DeviceHelper {
    private val PATH: String? = System.getenv("PATH")
    private const val PATH_TO_BIN_SU = "/system/bin/su"
    private const val PATH_TO_XBIN_SU = "/system/xbin/su"

    @JvmStatic
    fun isDeviceRooted(): Boolean {
        if (PATH != null) {
            val pathsToSearch = PATH.split(":")
            for (path in pathsToSearch) {
                if (path.isEmpty()) continue
                val su = File("${path}/su")
                if (su.exists() && !su.isDirectory) {
                    return true
                }
            }
        }
        return File(PATH_TO_BIN_SU).isFile || File(PATH_TO_XBIN_SU).isFile
    }
}