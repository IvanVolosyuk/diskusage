package com.google.android.diskusage.datasource.debug;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
import android.support.annotation.NonNull;
import com.google.android.diskusage.datasource.AppStatsCallback;
import com.google.android.diskusage.datasource.DataSource;
import com.google.android.diskusage.datasource.LegacyFile;
import com.google.android.diskusage.datasource.PkgInfo;
import com.google.android.diskusage.datasource.PortableFile;
import com.google.android.diskusage.datasource.StatFsSource;
import com.google.android.diskusage.datasource.fast.DefaultDataSource;
import com.google.android.diskusage.datasource.fast.StreamCopy;
import com.google.android.diskusage.proto.AppInfoProto;
import com.google.android.diskusage.proto.AppStatsProto;
import com.google.android.diskusage.proto.Dump;
import com.google.android.diskusage.proto.NativeScanProto;
import com.google.android.diskusage.proto.PortableFileProto;
import com.google.android.diskusage.proto.StatFsProto;
import org.jetbrains.annotations.Contract;

public class DebugDataSource extends DataSource {
  private final Dump dump;
  private final DataSource delegate;

  private DebugDataSource(Dump dump, DataSource delegate) {
    this.dump = dump;
    this.delegate = delegate;
  }

  @NonNull
  private static File dumpFile() {
    String path = Environment.getExternalStorageDirectory().getPath()
        + "/diskusage.bin";
    return new File(path);
  }

  public static boolean dumpExist() {
    File dumpFile = dumpFile();
    return dumpFile.exists();
  }

  @NonNull
  @Contract("_ -> new")
  public static DebugDataSource initNewDump(@NonNull Context c) throws IOException {
    PackageInfo info;
    Dump.Builder dump = Dump.newBuilder();
    try {
      info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
    } catch (NameNotFoundException e) {
      throw new RuntimeException(e);
    }
    DefaultDataSource delegate = new DefaultDataSource();
    dump.setVersion(info.versionName)
            .setVersionInt(info.versionCode)
            .setIsDeviceRooted(delegate.isDeviceRooted())
            .setAndroidVersion(delegate.getAndroidVersion());

    return new DebugDataSource(dump.build(), delegate);
  }

  @NonNull
  public static DebugDataSource loadDefaultDump() throws IOException {
    return loadDump(dumpFile());
  }

  @NonNull
  public static DebugDataSource loadDump(File dumpFile) throws IOException {
    Dump dump = Dump.parseFrom(StreamCopy.readFully(dumpFile));
    return new DebugDataSource(dump, null);
  }

  @Override
  public int getAndroidVersion() {
    return dump.getAndroidVersion();
  }

  @Override
  public synchronized List<PkgInfo> getInstalledPackages(PackageManager pm) {
    if (dump.getAppInfoList() == null || dump.getAppInfoList().size() == 0) {
      List<PkgInfo> packages = delegate.getInstalledPackages(pm);
      dump.toBuilder().addAllAppInfo(Arrays.asList(new AppInfoProto[packages.size()]));
      int i = 0;
      for (PkgInfo pkgInfo : packages) {
        AppInfoProto proto = AppInfoProtoImpl.makeProto(pkgInfo, dump.getAndroidVersion());
        dump.toBuilder().setAppInfo(i++, proto);
      }
    }
    List<PkgInfo> result = new ArrayList<>();

    for (final AppInfoProto appInfoProto : dump.getAppInfoList()) {
      result.add(new AppInfoProtoImpl(appInfoProto, dump.getAndroidVersion()));
    }
    return result;
  }

  @Override
  public void getPackageSizeInfo(
      final PkgInfo pkgInfo, final Method getPackageSizeInfo, final PackageManager pm,
      final AppStatsCallback callback) throws Exception {
    AppInfoProtoImpl appInfoImpl = (AppInfoProtoImpl) pkgInfo;
    final AppInfoProto proto = appInfoImpl.proto;

    if (proto.getStats() != null) {
      AppStatsProto stats = proto.getStats();
      callback.onGetStatsCompleted(
          stats.getHasAppStats()
          ? new AppStatsProtoImpl(stats, dump.getAndroidVersion())
          : null,
              stats.getSucceeded());
      return;
    }

    delegate.getPackageSizeInfo(
        pkgInfo, getPackageSizeInfo, pm, (appStats, succeeded) -> {
          AppStatsProto stats = AppStatsProtoImpl.makeProto(
              appStats, succeeded, dump.getAndroidVersion());
          proto.toBuilder().setStats(stats);
          callback.onGetStatsCompleted(
              stats.getHasAppStats() ? new AppStatsProtoImpl(
                  stats, dump.getAndroidVersion()) : null,
                  stats.getSucceeded());
          stats.toBuilder().setCallbackChildFinished(true);
        });
  }

  @Override
  public synchronized StatFsSource statFs(String mountPoint) {
    int emptyPos = -1;
    for (int i = 0; i < dump.getStatFsList().size(); i++) {
      if (dump.getStatFs(i) == null) {
        emptyPos = i;
      } else if (mountPoint.equals(dump.getStatFs(i).getMountPoint())) {
        return new StatFsSourceProtoImpl(dump.getStatFs(i), dump.getAndroidVersion());
      }
    }
    if (emptyPos == -1) {
      StatFsProto[] old = dump.getStatFsList().toArray(new StatFsProto[0]);
      dump.toBuilder().addAllStatFs(Arrays.asList(new StatFsProto[old.length * 2 + 3]));
      System.arraycopy(old,  0, dump.getStatFsList().toArray(), 0, old.length);
      emptyPos = old.length;
    }
    dump.toBuilder().setStatFs(emptyPos, StatFsSourceProtoImpl.makeProto(
            mountPoint, delegate.statFs(mountPoint), dump.getAndroidVersion()));
    StatFsProto proto = dump.getStatFs(emptyPos);
    return new StatFsSourceProtoImpl(proto, dump.getAndroidVersion());
  }

  @TargetApi(Build.VERSION_CODES.FROYO)
  @Override
  public PortableFile getExternalFilesDir(Context context) {
    if (dump.getAndroidVersion() < Build.VERSION_CODES.FROYO) {
      throw new NoClassDefFoundError("Undefined before FROYO");
    }
    if (dump.getExternalFilesDir() == null) {
      dump.toBuilder().setExternalFilesDir(PortableFileProtoImpl.makeProto(
          delegate.getExternalFilesDir(context), dump.getAndroidVersion()));
    }

    return PortableFileProtoImpl.make(dump.getExternalFilesDir(), dump.getAndroidVersion());
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  @Override
  public PortableFile[] getExternalFilesDirs(Context context) {
    if (dump.getAndroidVersion() < Build.VERSION_CODES.KITKAT) {
      throw new NoClassDefFoundError("Undefined before KITKAT");
    }

    if (dump.getExternalFilesDirsList() == null || dump.getExternalFilesDirsList().size() == 0) {
      PortableFile[] externalFilesDirs = delegate.getExternalFilesDirs(context);
      PortableFileProto[] protos = new PortableFileProto[externalFilesDirs.length];
      for (int i = 0; i < protos.length; i++) {
        protos[i] = PortableFileProtoImpl.makeProto(externalFilesDirs[i], dump.getAndroidVersion());
      }
      dump.toBuilder().addAllExternalFilesDirs(Arrays.asList(protos));
    }

    PortableFile[] result = new PortableFile[dump.getExternalFilesDirsList().size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = PortableFileProtoImpl.make(dump.getExternalFilesDirs(i), dump.getAndroidVersion());
    }
    return result;
  }

  @Override
  public PortableFile getExternalStorageDirectory() {
    if (dump.getExternalStorageDirectory() == null) {
      dump.toBuilder().setExternalFilesDir(PortableFileProtoImpl.makeProto(
          delegate.getExternalStorageDirectory(), dump.getAndroidVersion()));
    }

    return PortableFileProtoImpl.make(dump.getExternalStorageDirectory(), dump.getAndroidVersion());
  }

  @Override
  public boolean isDeviceRooted() {
    return dump.getIsDeviceRooted();
  }

  @Override
  public InputStream createNativeScanner(Context context, String path,
      boolean rootRequired) throws IOException, InterruptedException {
    int emptyPos = -1;
    for (int i = 0; i < dump.getNativeScanList().size(); i++) {
      if (dump.getNativeScan(i) == null) {
        emptyPos = i;
      } else if (path.equals(dump.getNativeScan(i).getPath())
          && rootRequired == dump.getNativeScan(i).getRootRequired()) {
        return PortableStreamProtoReaderImpl.create(dump.getNativeScan(i).getStream());
      }
    }
    if (emptyPos == -1) {
      NativeScanProto[] old = dump.getNativeScanList().toArray(new NativeScanProto[0]);
      dump.toBuilder().addAllNativeScan(Arrays.asList(new NativeScanProto[old.length * 2 + 3]));
      System.arraycopy(old,  0, dump.getNativeScanList().toArray(), 0, old.length);
      emptyPos = old.length;
    }
    dump.toBuilder().setNativeScan(emptyPos, NativeScanProto.newBuilder());
    final NativeScanProto.Builder proto = dump.getNativeScan(emptyPos).toBuilder();
    proto.setPath(path);
    proto.setRootRequired(rootRequired);
    return PortableStreamProtoWriterImpl.create(
        delegate.createNativeScanner(context, path, rootRequired), proto::setStream);
  }

  @Override
  public LegacyFile createLegacyScanFile(String root) {
    return delegate.createLegacyScanFile(root);
  }

  @Override
  public InputStream getProc() throws IOException {
    if (dump.getProc() != null) {
      return PortableStreamProtoReaderImpl.create(dump.getProc());
    }
    return PortableStreamProtoWriterImpl.create(delegate.getProc(), dump.toBuilder()::setProc);
  }

  @Override
  public PortableFile getParentFile(PortableFile in) {
    PortableFileProtoImpl file = (PortableFileProtoImpl) in;
    if (file.proto.getParent() != null) {
      return PortableFileProtoImpl.make(file.proto.getParent(), dump.getAndroidVersion());
    }

    file.proto.toBuilder().setParent(PortableFileProtoImpl.makeProto(
        delegate.getParentFile(in), dump.getAndroidVersion()));
    return PortableFileProtoImpl.make(file.proto.getParent(), dump.getAndroidVersion());
  }

  public void saveDumpAndSendReport(@NonNull Context context) throws IOException {
    byte[] dumpBytes = dump.toByteArray();
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
