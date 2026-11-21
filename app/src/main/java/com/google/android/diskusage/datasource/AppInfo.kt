package com.google.android.diskusage.datasource

interface AppInfo {
    val flags: Int
    val dataDir: String?
    val isEnabled: Boolean
    val name: String?
    val packageName: String?
    val publicSourceDir: String?
    val sourceDir: String?

    val splitSourceDirs: Array<String?>?
    val applicationLabel: String?
}