/*
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008-2011 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.google.android.diskusage.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.diskusage.databinding.ListDirItemBinding
import com.google.android.diskusage.databinding.ListFileItemBinding

class FileInfoAdapter(private val infos: List<FileInfo>) :
    RecyclerView.Adapter<FileInfoAdapter.ViewHolder>() {
    class ViewHolder(val ui: ViewBinding) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val ui = if (viewType == 0) {
            ListFileItemBinding.inflate(inflater)
        } else {
            ListDirItemBinding.inflate(inflater)
        }
        ui.root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return ViewHolder(ui)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fileInfo = infos[position]
        if (fileInfo.size.isEmpty()) {
            // directory
            val ui = holder.ui as ListDirItemBinding
            ui.name.text = fileInfo.name
        } else {
            val ui = holder.ui as ListFileItemBinding
            ui.name.text = fileInfo.name
            ui.size.text = fileInfo.size
        }
    }


    override fun getItemCount(): Int {
        return infos.size
    }

    override fun getItemViewType(position: Int): Int {
        val fileInfo = infos[position]
        return if ((fileInfo.size.isEmpty())) 1 else 0
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}
