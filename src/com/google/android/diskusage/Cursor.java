/**
 * DiskUsage - displays sdcard usage on android.
 * Copyright (C) 2008 Ivan Volosyuk
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

package com.google.android.diskusage;

import com.google.android.diskusage.FileSystemState.MainThreadAction;
import com.google.android.diskusage.entity.FileSystemEntry;

public class Cursor {
  FileSystemEntry root;
  public FileSystemEntry position;
  public long top;
  public int depth;

  Cursor(FileSystemState state,
      FileSystemEntry root) {
    this.root = root;

    if (root.children == null || root.children.length == 0) {
      throw new RuntimeException("no place for position");
    }
    position = root.children[0]; 
    depth = 0;
    top = 0;
    updateTitle(state);
  }

  public void updateTitle(FileSystemState state) {
    state.mainThreadAction.updateTitle(position);
  }


  void down(FileSystemState view) {
    FileSystemEntry newCursor = position.getNext();
    if (newCursor == position) return;
    view.invalidate(this);
    top += position.encodedSize;
    position = newCursor;
    view.invalidate(this);
    updateTitle(view);
  }

  void up(FileSystemState view) {
    FileSystemEntry newCursor = position.getPrev();
    if (newCursor == position) return;
    view.invalidate(this);
    top -= newCursor.encodedSize;
    position = newCursor;
    view.invalidate(this);
    updateTitle(view);
  }

  void right(FileSystemState state) {
    if (position.children == null) return;
    if (position.children.length == 0) return;
    state.invalidate(this);
    position = position.children[0];
    depth++;
    // Log.d("Sample", "position depth = " + depth);
    state.invalidate(this);
    updateTitle(state);
  }

  boolean left(FileSystemState state) {
    if (position.parent == root) return false;
    state.invalidate(this);
    position = position.parent;
    top = root.getOffset(position);
    depth--;
    // Log.d("Sample", "position depth = " + depth);
    state.invalidate(this);
    updateTitle(state);
    return true;
  }

  void set(FileSystemState state, FileSystemEntry newpos) {
    if (newpos == root) throw new RuntimeException("will break zoomOut()");
    state.invalidate(this);
    position = newpos;
    depth = root.depth(position) - 1;
    // Log.d("Sample", "position depth = " + depth);
    top = root.getOffset(position);
    state.invalidate(this);
    updateTitle(state);
  }

  void refresh(FileSystemState view) {
    set(view, position);
  }
}