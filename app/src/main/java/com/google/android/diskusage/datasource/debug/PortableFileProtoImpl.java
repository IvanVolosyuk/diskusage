package com.google.android.diskusage.datasource.debug;

import android.annotation.TargetApi;
import android.os.Build;

import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.proto.BooleanValueProto;
import com.google.android.diskusage.proto.PortableFileProto;

public class PortableFileProtoImpl implements PortableFile {
  final PortableFileProto proto;
  private final int androidVersion;

  private PortableFileProtoImpl(PortableFileProto proto, int androidVersion) {
    this.proto = proto;
    this.androidVersion = androidVersion;
  }

  public static PortableFileProtoImpl make(PortableFileProto proto, int androidVersion) {
    if (proto.absolutePath != "" && proto.absolutePath != null) {
      return new PortableFileProtoImpl(proto, androidVersion);
    } else {
      return null;
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageEmulated() {
    if (androidVersion < Build.VERSION_CODES.LOLLIPOP) {
      throw new NoClassDefFoundError("unavailable before L");
    }
    PortableExceptionProtoImpl.throwRuntimeException(
        proto.isExternalStorageEmulated.exception);
    return proto.isExternalStorageEmulated.value;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageRemovable() {
    if (androidVersion < Build.VERSION_CODES.LOLLIPOP) {
      throw new NoClassDefFoundError("unavailable before L");
    }
    PortableExceptionProtoImpl.throwRuntimeException(
        proto.isExternalStorageRemovable.exception);
    return proto.isExternalStorageRemovable.value;
  }

  @Override
  public String getCanonicalPath() {
    return proto.canonicalPath;
  }

  @Override
  public String getAbsolutePath() {
    return proto.absolutePath;
  }

  @Override
  public long getTotalSpace() {
    return proto.totalSpace;
  }

  public static PortableFileProto makeProto(
      PortableFile file, int androidVersion) {
    PortableFileProto p = new PortableFileProto();

    if (file == null) {
      return p;
    }
    p.absolutePath = file.getAbsolutePath();
    p.canonicalPath = file.getCanonicalPath();
    if (androidVersion >= Build.VERSION_CODES.GINGERBREAD) {
      p.totalSpace = file.getTotalSpace();
      if (androidVersion >= Build.VERSION_CODES.LOLLIPOP) {
        p.isExternalStorageEmulated = new BooleanValueProto();
        try {
          p.isExternalStorageEmulated.value = file.isExternalStorageEmulated();
        } catch (RuntimeException e) {
          p.isExternalStorageEmulated.exception = PortableExceptionProtoImpl.makeProto(e);
        }
        p.isExternalStorageRemovable = new BooleanValueProto();
        try {
          p.isExternalStorageRemovable.value = file.isExternalStorageRemovable();
        } catch (RuntimeException e) {
          p.isExternalStorageRemovable.exception = PortableExceptionProtoImpl.makeProto(e);
        }
      }
    }
    return p;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PortableFile)) {
      return false;
    }
    PortableFile other = (PortableFile) o;
    return other.getAbsolutePath().equals(getAbsolutePath());
  }

  @Override
  public int hashCode() {
    return getAbsolutePath().hashCode();
  }
}
