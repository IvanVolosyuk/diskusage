package com.google.android.diskusage.ui.filemanagers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.google.android.diskusage.filesystem.entity.FileSystemEntry

class DocumentsUIFilemanager(mEntry: FileSystemEntry, mActivity: Activity) : ExternalFileManager(mEntry, mActivity) {

    override fun getFilemanagerIntent(): Intent {
        // Documentation for the reason behind the replacements
        // https://stackoverflow.com/a/75299820
        var path = mEntry.absolutePath()
        path = path.replaceFirst("storage/emulated/0", "primary")
        path = path.replaceFirst("/", "")
        path = path.replaceFirst("/", "%3A")
        path = path.replace("/", "%2F")

        var intent = Intent(Intent.ACTION_VIEW)
        var uri = Uri.parse(
            "content://com.android.externalstorage.documents/document/" + path
        )

        Log.e("TAG", mEntry.absolutePath())
        Log.e("TAG", uri.toString())

        intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
        return intent
    }

}