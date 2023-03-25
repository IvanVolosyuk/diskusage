package com.volosyukivan.diskusage.datasource.fast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import androidx.annotation.NonNull;
import com.volosyukivan.diskusage.utils.AppHelper;
import com.volosyukivan.diskusage.utils.DeviceHelper;
import org.jetbrains.annotations.Contract;

public class NativeScannerStream extends InputStream {

  private final InputStream is;
  private final Process process;

  public NativeScannerStream(InputStream is, Process process) {
    this.is = is;
    this.process = process;
  }

  @Override
  public int read() throws IOException {
    return is.read();
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    return is.read(buffer);
  }

  @Override
  public int read(byte[] buffer, int byteOffset, int byteCount)
      throws IOException {
    return is.read(buffer, byteOffset, byteCount);
  }

  @Override
  public void close() throws IOException {
    is.close();
    try {
      process.waitFor();
    } catch (InterruptedException e) {
      throw new IOException(e.getMessage());
    }
  }



  public static class Factory {
    // private static final boolean remove = true;

    @NonNull
    @Contract("_, _ -> new")
    public static NativeScannerStream create(String path, boolean rootRequired)
        throws IOException, InterruptedException {
      return runScanner(path, rootRequired);
    }

    @NonNull
    @Contract("_, _ -> new")
    private static NativeScannerStream runScanner(String root, boolean rootRequired)
            throws IOException, InterruptedException {
      String binaryName = "libscan.so";
      Process process = null;

      if (!(rootRequired && DeviceHelper.isDeviceRooted())) {
        process = Runtime.getRuntime().exec(new String[] {
                getScanBinaryPath(binaryName), root});
      } else {
        IOException e = null;
        for (String su : new String[] { "su", "/system/bin/su", "/system/xbin/su" }) {
          try {
            process = Runtime.getRuntime().exec(new String[] { su });
            break;
          } catch (IOException newe) {
            e = newe;
          }
        }

        if (process == null) {
          throw e;
        }

        OutputStream os = process.getOutputStream();
        os.write((getScanBinaryPath(binaryName) + " " + root).getBytes("UTF-8"));
        os.flush();
        os.close();
      }
      InputStream is = process.getInputStream();
      return new NativeScannerStream(is, process);
    }

    @NonNull
    private static String getScanBinaryPath(String binaryName) {
      return AppHelper.getAppContext().getApplicationInfo().nativeLibraryDir
              + "/" + binaryName;
    }
  }
}
