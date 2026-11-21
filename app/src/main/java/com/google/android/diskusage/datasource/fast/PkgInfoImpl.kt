package com.google.android.diskusage.datasource.fast

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.android.diskusage.datasource.AppInfo
import com.google.android.diskusage.datasource.PkgInfo

class PkgInfoImpl(private val info: PackageInfo, private val pm: PackageManager) : PkgInfo {
    override val packageName: String
        get() = info.packageName
    override val applicationInfo: AppInfo?
        // applicationInfo is annotated as nullable since Android 14 (packages
        // without any code, e.g. stubs, can be returned by the platform).
        get() = (info.applicationInfo as ApplicationInfo?)?.let { AppInfoImpl(it, pm) }
}
