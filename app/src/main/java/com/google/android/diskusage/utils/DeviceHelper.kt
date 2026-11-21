package com.google.android.diskusage.utils

import java.io.File

object DeviceHelper {
    private val SU = "su"
    private val suLocations = arrayOf(
        "/system/bin/", "/system/xbin/", "/sbin/", "/system/sd/xbin/",
        "/system/bin/failsafe/", "/data/local/xbin/", "/data/local/bin/", "/data/local/",
        "/system/sbin/", "/usr/bin/", "/vendor/bin/"
    )

    @JvmStatic
    fun isDeviceRooted(): Boolean {
        return suLocations.any { location -> File(location, SU).exists() }
    }
}
