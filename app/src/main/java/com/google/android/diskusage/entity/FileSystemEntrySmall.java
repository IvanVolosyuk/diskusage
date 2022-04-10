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

package com.google.android.diskusage.entity;

public class FileSystemEntrySmall extends FileSystemEntry {
  int numFiles;
  public FileSystemEntrySmall(FileSystemEntry parent, String name, int numFiles) {
    super(parent, name);
    this.numFiles = numFiles;
  }
  
  public static FileSystemEntrySmall makeNode(
      FileSystemEntry parent, String name, int numFiles) {
    return new FileSystemEntrySmall(parent, name, numFiles);
  }

  @Override
  public FileSystemEntry create() {
    return new FileSystemEntrySmall(null, this.name, this.numFiles);
  }

  @Override
  public FileSystemEntry filter(CharSequence pattern, long blockSize) {
    return null;
  }
}
