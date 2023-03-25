package com.volosyukivan.diskusage.utils

import java.io.File

object DeviceHelper {

    @JvmStatic
    fun isDeviceRooted(): Boolean {
        val su = "su"
        val locations = arrayOf(
            "/system/bin/", "/system/xbin/", "/sbin/", "/system/sd/xbin/",
            "/system/bin/failsafe/", "/data/local/xbin/", "/data/local/bin/", "/data/local/",
            "/system/sbin/", "/usr/bin/", "/vendor/bin/"
        )
        for (location in locations) {
            if (File("${location}${su}").exists()) {
                return true
            }
        }
        return false
    }
}
