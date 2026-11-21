package com.google.android.diskusage.datasource.fast

import com.google.android.diskusage.datasource.LegacyFile
import java.io.File
import java.io.IOException

class LegacyFileImpl private constructor(private val file: File) : LegacyFile {
    override val name: String
        get() = file.getName()

    @get:Throws(IOException::class)
    override val canonicalPath: String
        get() = file.getCanonicalPath()

    override val isLink: Boolean
        get() {
            try {
                if (file.getCanonicalPath() == file.path) return false
            } catch (ignored: Throwable) {
            }
            return true
        }

    override val isFile: Boolean
        get() = file.isFile

    override fun length(): Long {
        return file.length()
    }

    override fun listFiles(): Array<LegacyFile>? {
        return file.listFiles()?.map { LegacyFileImpl(it) }?.toTypedArray()
    }

    override fun list(): Array<String>? {
        return file.list()
    }

    override fun getChild(string: String): LegacyFile {
        return LegacyFileImpl(File(file, string))
    }

    companion object {
        @JvmStatic
        fun createRoot(root: String): LegacyFile {
            return LegacyFileImpl(File(root))
        }
    }
}
