package com.google.android.diskusage.datasource

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.diskusage.datasource.fast.DefaultDataSource
import com.google.android.diskusage.ui.LoadableActivity

abstract class DataSource {
    abstract fun getInstalledPackages(pm: PackageManager): List<PkgInfo>
    abstract fun statFs(mountPoint: String?): StatFsSource
    abstract fun getExternalFilesDir(context: Context): PortableFile?
    abstract val externalStorageDirectory: PortableFile?
    abstract fun createLegacyScanFile(root: String): LegacyFile?

    abstract fun getParentFile(file: PortableFile): PortableFile?

    companion object {
        private var currentDataSource: DataSource = DefaultDataSource()
        fun get(): DataSource = currentDataSource

        fun override(dataSource: DataSource) {
            LoadableActivity.resetStoredStates()
            currentDataSource = dataSource
        }
    }
}
