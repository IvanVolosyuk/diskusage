package com.google.android.diskusage.ui.filemanagers

import android.app.Activity
import android.content.Intent
import com.google.android.diskusage.R
import com.google.android.diskusage.filesystem.entity.FileSystemEntry

class OpenIntentsPickDir(mEntry: FileSystemEntry, mActivity: Activity) : ExternalFileManager(mEntry, mActivity) {

    override fun getFilemanagerIntent(): Intent {
        val intent = Intent("org.openintents.action.PICK_DIRECTORY")
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setData(getUri())
        intent.putExtra(
            "org.openintents.extra.TITLE",
            mActivity.getString(R.string.title_in_oi_file_manager)
        )
        intent.putExtra(
            "org.openintents.extra.BUTTON_TEXT",
            mActivity.getString(R.string.button_text_in_oi_file_manager)
        )
        return intent
    }

}