package com.google.android.diskusage.datasource.fast

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.android.diskusage.datasource.AppInfo

class AppInfoImpl(
    private val app: ApplicationInfo,
    private val pm: PackageManager
) : AppInfo {
    override val flags: Int
        get() = app.flags
    override val dataDir: String
        get() = app.dataDir
    override val isEnabled: Boolean
        get() = app.enabled
    override val name: String
        get() = app.name
    override val packageName: String
        get() = app.packageName
    override val publicSourceDir: String
        get() = app.publicSourceDir
    override val sourceDir: String
        get() = app.sourceDir

    override val splitSourceDirs: Array<String?>?
        get() = app.splitSourceDirs
    override val applicationLabel: String
        get() = pm.getApplicationLabel(app).toString()
}