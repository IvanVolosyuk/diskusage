package com.google.android.diskusage.datasource.debug;

import android.annotation.TargetApi;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

  @Nullable
  public static PortableFileProtoImpl make(@NonNull PortableFileProto proto, int androidVersion) {
    if (!proto.getAbsolutePath().equals("")) {
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
        proto.getIsExternalStorageEmulated().getException());
    return proto.getIsExternalStorageEmulated().getValue();
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean isExternalStorageRemovable() {
    if (androidVersion < Build.VERSION_CODES.LOLLIPOP) {
      throw new NoClassDefFoundError("unavailable before L");
    }
    PortableExceptionProtoImpl.throwRuntimeException(
        proto.getIsExternalStorageRemovable().getException());
    return proto.getIsExternalStorageRemovable().getValue();
  }

  @Override
  public String getCanonicalPath() {
    return proto.getCanonicalPath();
  }

  @Override
  public String getAbsolutePath() {
    return proto.getAbsolutePath();
  }

  @Override
  public long getTotalSpace() {
    return proto.getTotalSpace();
  }

  @NonNull
  public static PortableFileProto makeProto(
      PortableFile file, int androidVersion) {
    PortableFileProto.Builder p = PortableFileProto.newBuilder();

    if (file == null) {
      return p.build();
    }
    p.setAbsolutePath(file.getAbsolutePath())
            .setCanonicalPath(file.getCanonicalPath());
    if (androidVersion >= Build.VERSION_CODES.GINGERBREAD) {
      p.setTotalSpace(file.getTotalSpace());
      if (androidVersion >= Build.VERSION_CODES.LOLLIPOP) {
        p.setIsExternalStorageEmulated(BooleanValueProto.newBuilder());
        try {
          p.getIsExternalStorageEmulated().toBuilder()
                  .setValue(file.isExternalStorageEmulated());
        } catch (RuntimeException e) {
          p.getIsExternalStorageEmulated().toBuilder()
                  .setException(PortableExceptionProtoImpl.makeProto(e));
        }
        p.setIsExternalStorageRemovable(BooleanValueProto.newBuilder());
        try {
          p.getIsExternalStorageRemovable().toBuilder()
                  .setValue(file.isExternalStorageRemovable());
        } catch (RuntimeException e) {
          p.getIsExternalStorageRemovable().toBuilder()
                  .setException(PortableExceptionProtoImpl.makeProto(e));
        }
      }
    }
    return p.build();
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
