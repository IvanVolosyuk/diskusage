/**
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

/**
 * Non displayed entry which contains just one entry which is
 * displayed root of filesystem.
 */
public class FileSystemSuperRoot extends FileSystemSpecial {
  final int blockSize;
  
  public FileSystemSuperRoot(int blockSize) {
    super(null, 0, blockSize);
    this.blockSize = blockSize;
  }
  
  public int getDisplayBlockSize() {
    return blockSize;
  }
}
