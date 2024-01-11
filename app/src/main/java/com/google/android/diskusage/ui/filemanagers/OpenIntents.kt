package com.google.android.diskusage.ui.filemanagers

import android.app.Activity
import android.content.Intent
import com.google.android.diskusage.filesystem.entity.FileSystemEntry

class OpenIntents(mEntry: FileSystemEntry, mActivity: Activity) : ExternalFileManager(mEntry, mActivity) {

    override fun getFilemanagerIntent(): Intent {
        val intent = Intent("org.openintents.action.VIEW_DIRECTORY")
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setData(getUri())
        return intent
    }

}