package com.google.android.diskusage.datasource

interface PortableFile {
    val isExternalStorageEmulated: Boolean
    val isExternalStorageRemovable: Boolean

    /** Retries with getAbsolutePath() on IOException  */
    val canonicalPath: String
    val absolutePath: String
    val totalSpace: Long
}
