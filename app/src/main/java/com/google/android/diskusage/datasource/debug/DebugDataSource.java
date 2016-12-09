package com.google.android.diskusage.datasource.debug;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import com.google.android.diskusage.datasource.AppStats;
import com.google.android.diskusage.datasource.AppStatsCallback;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.LegacyFile;
import com.google.android.diskusage.datasource.PkgInfo;
import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.datasource.debug.PortableStreamProtoWriterImpl.CloseCallback;
import com.google.android.diskusage.datasource.fast.DefaultDataSource;
import com.google.android.diskusage.datasource.fast.StreamCopy;
import com.google.android.diskusage.proto.AppInfoProto;
import com.google.android.diskusage.proto.AppStatsProto;
import com.google.android.diskusage.proto.Dump;
import com.google.android.diskusage.proto.NativeScanProto;
import com.google.android.diskusage.proto.PortableFileProto;
import com.google.android.diskusage.proto.PortableStreamProto;
import com.google.android.diskusage.proto.StatFsProto;
import com.google.protobuf.nano.MessageNano;

public class DebugDataSource extends DataSource {
  private final Dump dump;
  private final DataSource delegate;

  private DebugDataSource(Dump dump, DataSource delegate) {
    this.dump = dump;
    this.delegate = delegate;
  }

  private static File dumpFile() {
    String path = Environment.getExternalStorageDirectory().getPath()
        + "/diskusage.bin";
    File dumpFile = new File(path);
    return dumpFile;
  }

  public static boolean dumpExist() {
    File dumpFile = dumpFile();
    return dumpFile.exists();
  }

  public static DebugDataSource initNewDump(Context c) throws IOException {
    PackageInfo info;
    Dump dump = new Dump();
    try {
      info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
    } catch (NameNotFoundException e) {
      throw new RuntimeException(e);
    }
    dump.version = info.versionName;
    dump.versionInt = info.versionCode;

    DefaultDataSource delegate = new DefaultDataSource();
    dump.isDeviceRooted = delegate.isDeviceRooted();
    dump.androidVersion = delegate.getAndroidVersion();
    return new DebugDataSource(dump, delegate);

  }

  public static DebugDataSource loadDefaultDump() throws IOException {
    return loadDump(dumpFile());
  }

  public static DebugDataSource loadDump(File dumpFile) throws IOException {
    Dump dump = Dump.parseFrom(StreamCopy.readFully(dumpFile));
    return new DebugDataSource(dump, null);
  }

  @Override
  public int getAndroidVersion() {
    return dump.androidVersion;
  }

  @Override
  public synchronized List<PkgInfo> getInstalledPackages(PackageManager pm) {
    if (dump.appInfo == null || dump.appInfo.length == 0) {
      List<PkgInfo> packages = delegate.getInstalledPackages(pm);
      dump.appInfo = new AppInfoProto[packages.size()];
      int i = 0;
      for (PkgInfo pkgInfo : packages) {
        AppInfoProto proto = AppInfoProtoImpl.makeProto(pkgInfo, dump.androidVersion);
        dump.appInfo[i++] = proto;
      }
    }
    List<PkgInfo> result = new ArrayList<PkgInfo>();

    for (int i = 0; i < dump.appInfo.length; i++) {
      result.add(new AppInfoProtoImpl(dump.appInfo[i], dump.androidVersion));
    }
    return result;
  }

  @Override
  public void getPackageSizeInfo(
      final PkgInfo pkgInfo, final Method getPackageSizeInfo, final PackageManager pm,
      final AppStatsCallback callback) throws Exception {
    AppInfoProtoImpl appInfoImpl = (AppInfoProtoImpl) pkgInfo;
    final AppInfoProto proto = appInfoImpl.proto;

    if (proto.stats != null) {
      AppStatsProto stats = proto.stats;
      callback.onGetStatsCompleted(
          stats.hasAppStats
          ? new AppStatsProtoImpl(stats, dump.androidVersion)
          : null,
              stats.succeeded);
      return;
    }

    delegate.getPackageSizeInfo(
        pkgInfo, getPackageSizeInfo, pm, new AppStatsCallback() {
          @Override
          public void onGetStatsCompleted(AppStats appStats, boolean succeeded) {
            AppStatsProto stats = AppStatsProtoImpl.makeProto(
                appStats, succeeded, dump.androidVersion);
            proto.stats = stats;
            callback.onGetStatsCompleted(
                stats.hasAppStats ? new AppStatsProtoImpl(
                    stats, dump.androidVersion) : null,
                    stats.succeeded);
            stats.callbackChildFinished = true;
          }
        });
  }

  @Override
  public synchronized StatFsSource statFs(String mountPoint) {
    int emptyPos = -1;
    for (int i = 0; i < dump.statFs.length; i++) {
      if (dump.statFs[i] == null) {
        emptyPos = i;
      } else if (mountPoint.equals(dump.statFs[i].mountPoint)) {
        return new StatFsSourceProtoImpl(dump.statFs[i], dump.androidVersion);
      }
    }
    if (emptyPos == -1) {
      StatFsProto[] old = dump.statFs;
      dump.statFs = new StatFsProto[old.length * 2 + 3];
      System.arraycopy(old,  0, dump.statFs, 0, old.length);
      emptyPos = old.length;
    }
    StatFsProto proto = dump.statFs[emptyPos] = StatFsSourceProtoImpl.makeProto(
            mountPoint, delegate.statFs(mountPoint), dump.androidVersion);
    return new StatFsSourceProtoImpl(proto, dump.androidVersion);
  }

  @TargetApi(Build.VERSION_CODES.FROYO)
  @Override
  public PortableFile getExternalFilesDir(Context context) {
    if (dump.androidVersion < Build.VERSION_CODES.FROYO) {
      throw new NoClassDefFoundError("Undefined before FROYO");
    }
    if (dump.externalFilesDir == null) {
      dump.externalFilesDir = PortableFileProtoImpl.makeProto(
          delegate.getExternalFilesDir(context), dump.androidVersion);
    }

    return PortableFileProtoImpl.make(dump.externalFilesDir, dump.androidVersion);
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  @Override
  public PortableFile[] getExternalFilesDirs(Context context) {
    if (dump.androidVersion < Build.VERSION_CODES.KITKAT) {
      throw new NoClassDefFoundError("Undefined before KITKAT");
    }

    if (dump.externalFilesDirs == null || dump.externalFilesDirs.length == 0) {
      PortableFile[] externalFilesDirs = delegate.getExternalFilesDirs(context);
      PortableFileProto[] protos = new PortableFileProto[externalFilesDirs.length];
      for (int i = 0; i < protos.length; i++) {
        protos[i] = PortableFileProtoImpl.makeProto(externalFilesDirs[i], dump.androidVersion);
      }
      dump.externalFilesDirs = protos;
    }

    PortableFile[] result = new PortableFile[dump.externalFilesDirs.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = PortableFileProtoImpl.make(dump.externalFilesDirs[i], dump.androidVersion);
    }
    return result;
  }

  @Override
  public PortableFile getExternalStorageDirectory() {
    if (dump.externalStorageDirectory == null) {
      dump.externalStorageDirectory = PortableFileProtoImpl.makeProto(
          delegate.getExternalStorageDirectory(), dump.androidVersion);
    }

    return PortableFileProtoImpl.make(dump.externalStorageDirectory, dump.androidVersion);
  }

  @Override
  public boolean isDeviceRooted() {
    return dump.isDeviceRooted;
  }

  @Override
  public InputStream createNativeScanner(Context context, String path,
      boolean rootRequired) throws IOException, InterruptedException {
    int emptyPos = -1;
    for (int i = 0; i < dump.nativeScan.length; i++) {
      if (dump.nativeScan[i] == null) {
        emptyPos = i;
      } else if (path.equals(dump.nativeScan[i].path)
          && rootRequired == dump.nativeScan[i].rootRequired) {
        return PortableStreamProtoReaderImpl.create(dump.nativeScan[i].stream);
      }
    }
    if (emptyPos == -1) {
      NativeScanProto[] old = dump.nativeScan;
      dump.nativeScan = new NativeScanProto[old.length * 2 + 3];
      System.arraycopy(old,  0, dump.nativeScan, 0, old.length);
      emptyPos = old.length;
    }
    final NativeScanProto proto = dump.nativeScan[emptyPos] = new NativeScanProto();
    proto.path = path;
    proto.rootRequired = rootRequired;
    return PortableStreamProtoWriterImpl.create(
        delegate.createNativeScanner(context, path, rootRequired), new CloseCallback() {
          @Override
          public void onClose(PortableStreamProto stream) {
            proto.stream = stream;
          }
        });
  }

  @Override
  public LegacyFile createLegacyScanFile(String root) {
    return delegate.createLegacyScanFile(root);
  }

  @Override
  public InputStream getProc() throws IOException {
    if (dump.proc != null) {
      return PortableStreamProtoReaderImpl.create(dump.proc);
    }


    return PortableStreamProtoWriterImpl.create(delegate.getProc(), new CloseCallback() {
      @Override
      public void onClose(PortableStreamProto proto) {
        dump.proc = proto;
      }
    });
  }

  @Override
  public PortableFile getParentFile(PortableFile in) {
    PortableFileProtoImpl file = (PortableFileProtoImpl) in;
    if (file.proto.parent != null) {
      return PortableFileProtoImpl.make(file.proto.parent, dump.androidVersion);
    }

    file.proto.parent = PortableFileProtoImpl.makeProto(
        delegate.getParentFile(in), dump.androidVersion);
    return PortableFileProtoImpl.make(file.proto.parent, dump.androidVersion);
  }

  public void saveDumpAndSendReport(Context context) throws IOException {
    byte[] dumpBytes = MessageNano.toByteArray(dump);
    InputStream is = new ByteArrayInputStream(dumpBytes);
    File dumpFile = dumpFile();
    OutputStream os = new FileOutputStream(dumpFile);
    StreamCopy.copyStream(is, os);
    Intent emailIntent = new Intent(Intent.ACTION_SEND);
    emailIntent.setType("message/rfc822");
    emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"ivan.volosyuk+diskusage@gmail.com"});
    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "DiskUsage bugreport");
    emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(dumpFile));
    emailIntent.putExtra(Intent.EXTRA_TEXT,
        "Please add some description of a problem\n" +
        "The attached dump may contain file names and install application names\n");
    context.startActivity(Intent.createChooser(
        emailIntent, "Send bugreport email..."));
  }
}
