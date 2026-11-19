package com.google.android.diskusage.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.diskusage.R
import com.google.android.diskusage.utils.AppHelper

class DiskUsageViewModel : ViewModel() {
    val toolbarActionButtonVisible = MutableLiveData(false)

    val showButton = MutableLiveData(false)

    val rescanButton = MutableLiveData(false)

    val deleteButton = MutableLiveData(false)

    val rendererButtonTitle = MutableLiveData(AppHelper.appContext.getString(R.string.rederer))

    fun showToolbarActionButton() {
        toolbarActionButtonVisible.value = true
    }

    fun hideToolBarActionButton() {
        toolbarActionButtonVisible.value = false
    }

    fun enableShowButton() {
        showButton.value = true
    }

    fun disableShowButton() {
        showButton.value = false
    }

    fun enableRescanButton() {
        rescanButton.value = true
    }

    fun disableRescanButton() {
        rescanButton.value = false
    }

    fun enableDeleteButton() {
        deleteButton.value = true
    }

    fun disableDeleteButton() {
        deleteButton.value = false
    }

    fun setRendererButtonTitle(title: String) {
        rendererButtonTitle.value = title
    }
}