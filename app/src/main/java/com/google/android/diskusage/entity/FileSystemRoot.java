package com.google.android.diskusage.entity;

import com.google.android.diskusage.MountPoint;

public class FileSystemRoot extends FileSystemEntry {
  final String rootPath;

  protected FileSystemRoot(String name, String rootPath) {
    super(null, name);
    this.rootPath = rootPath;
  }
  
  public static FileSystemRoot makeNode(String name, String rootPath) {
    return new FileSystemRoot(name, rootPath);
  }

  @Override
  public FileSystemEntry create() {
    return new FileSystemRoot(this.name, this.rootPath);
  }

  @Override
  public FileSystemEntry filter(CharSequence pattern, int blockSize) {
    // don't match name
    return filterChildren(pattern, blockSize);
  }
  
  public static String withSlash(String path) {
    if (path.length() > 0 && path.charAt(path.length() - 1) != '/')
      path += '/';
    return path;
  }
  
  public final FileSystemEntry getByAbsolutePath(String path) {
    String rootPathWithSlash = withSlash(rootPath);
    String pathWithSlash = withSlash(path);
    
    if (pathWithSlash.equals(rootPathWithSlash)) {
      return getEntryByName(path, true);
    }
    if (pathWithSlash.startsWith(rootPathWithSlash)) {
      return getEntryByName(path.substring(rootPathWithSlash.length(), path.length()), true);
    }
    for (FileSystemEntry s : children) {
      if (s instanceof FileSystemRoot) {
        FileSystemRoot subroot = (FileSystemRoot) s;
        FileSystemEntry e = subroot.getByAbsolutePath(path);
        if (e != null) return e;
      }
    }
    return null;
  }
}
