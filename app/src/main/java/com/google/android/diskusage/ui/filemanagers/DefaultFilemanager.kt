package com.google.android.diskusage.ui.filemanagers

import android.app.Activity
import android.content.Intent
import com.google.android.diskusage.filesystem.entity.FileSystemEntry

class DefaultFilemanager(mEntry: FileSystemEntry, mActivity: Activity) : ExternalFileManager(mEntry, mActivity) {

    override fun getFilemanagerIntent(): Intent {
        var intent = Intent(Intent.ACTION_VIEW)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setDataAndType(getUri(), "inode/directory")
        return intent
    }

}