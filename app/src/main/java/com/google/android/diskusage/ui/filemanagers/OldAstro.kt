package com.google.android.diskusage.ui.filemanagers

import android.app.Activity
import android.content.Intent
import com.google.android.diskusage.filesystem.entity.FileSystemEntry

class OldAstro(mEntry: FileSystemEntry, mActivity: Activity) : ExternalFileManager(mEntry, mActivity) {

    override fun getFilemanagerIntent(): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setDataAndType(getUri(), "vnd.android.cursor.item/com.metago.filemanager.dir")
        return intent
    }

}