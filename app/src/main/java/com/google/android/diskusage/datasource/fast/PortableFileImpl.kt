package com.google.android.diskusage.datasource.fast

import android.os.Environment
import com.google.android.diskusage.datasource.PortableFile
import com.google.android.diskusage.utils.PathHelper
import java.io.File

class PortableFileImpl private constructor(private val file: File) : PortableFile {
    override val isExternalStorageEmulated: Boolean
        get() = try {
            Environment.isExternalStorageEmulated(file)
        } catch (e: Exception) {
            false
        }
    override val isExternalStorageRemovable: Boolean
        get() = try {
            Environment.isExternalStorageRemovable(file)
        } catch (e: Exception) {
            false
        }
    override val canonicalPath: String
        get() = try {
            file.canonicalPath
        } catch (e: Exception) {
            file.absolutePath
        }
    override val absolutePath: String
        get() = file.absolutePath
    override val totalSpace: Long
        get() = file.getTotalSpace()

    override fun equals(other: Any?): Boolean {
        if (other !is PortableFile) {
            return false
        }
        return other.absolutePath == absolutePath
    }

    override fun hashCode(): Int {
        return absolutePath.hashCode()
    }

    companion object {
        @JvmStatic
        fun make(file: File?): PortableFileImpl? {
            return file?.let { PortableFileImpl(it) }
        }

        @JvmStatic
        val externalAppFilesDirs: Array<PortableFile?>
            get() = PathHelper.getExternalAppFilesPaths().map { make(it) }.toTypedArray()
    }
}