package com.google.android.diskusage.entity;

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
  
  public final FileSystemEntry getByAbsolutePath(String path) {
    if (path.startsWith(rootPath)) {
      return getEntryByName(path.substring(rootPath.length() + 1, path.length()), true);
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
