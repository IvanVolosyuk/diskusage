package com.google.android.diskusage.datasource

import com.google.android.diskusage.filesystem.entity.FileSystemEntry.SearchInterruptedException
import com.google.android.diskusage.filesystem.entity.FileSystemSuperRoot
import com.google.android.diskusage.ui.DiskUsageMenu

class SearchManager(private val menu: DiskUsageMenu) {
    private var finishedSearch: Search? = null
    private var activeSearch: Search? = null
    private lateinit var query: String

    private inner class Search(
        val query: String,
        var baseRoot: FileSystemSuperRoot
    ) : Thread() {
        var newRoot: FileSystemSuperRoot? = null
        override fun run() {
            try {
                val root = menu.masterRoot
                newRoot = root?.filter(this.query, baseRoot.displayBlockSize) as FileSystemSuperRoot
                if (isInterrupted) return
                menu.diskusage.handler.post { searchFinished(this@Search) }
            } catch (ignored: SearchInterruptedException) {
            }
        }
    }

    fun search(newQuery: String) {
        query = newQuery.lowercase()
        activeSearch?.let {
            if (newQuery.contains(it.query)) {
                return
            } else {
                it.interrupt()
                activeSearch = null
            }
        }
        startSearch()
    }

    private fun startSearch() {
        var baseRoot = menu.masterRoot
        finishedSearch?.let {
            if (query.contains(it.query)) {
                baseRoot = it.newRoot
            } else {
                finishedSearch = null
            }
        }
        if (baseRoot != null) {
            val search = Search(query, baseRoot!!)
            search.start()
        } else {
            menu.finishedSearch(null, null)
        }
    }

    private fun searchFinished(search: Search) {
        if (activeSearch === search) {
            activeSearch = null
        }
        finishedSearch = search
        if (query != search.query) {
            startSearch()
        }
        menu.finishedSearch(search.newRoot, search.query)
    }

    fun cancelSearch() {
        activeSearch?.interrupt()
        activeSearch = null
        finishedSearch = null
    }
}