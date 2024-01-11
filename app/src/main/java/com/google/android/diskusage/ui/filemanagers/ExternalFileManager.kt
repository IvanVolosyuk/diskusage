package com.google.android.diskusage.ui.filemanagers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.FileUriExposedException
import androidx.core.content.FileProvider
import com.google.android.diskusage.BuildConfig
import com.google.android.diskusage.filesystem.entity.FileSystemEntry
import java.io.File

abstract class ExternalFileManager(var mEntry: FileSystemEntry, var mActivity: Activity) {


    companion object {
        fun getUri(entry: FileSystemEntry, context: Context): Uri {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".provider",
                    File(entry.absolutePath()))
            }
            return Uri.fromFile(File(entry.absolutePath()))
        }
    }


    abstract fun getFilemanagerIntent(): Intent


    fun getUri(): Uri {
        return Companion.getUri(mEntry, mActivity)
    }

    fun open(): Boolean {
        try {
            mActivity.startActivity(getFilemanagerIntent())
            return true
        } catch (ignored: ActivityNotFoundException) {
            ignored.printStackTrace()
        } catch (ignored: FileUriExposedException) {
            ignored.printStackTrace()
        }
        return false
    }




}