package com.google.android.diskusage.datasource.debug;

import android.support.annotation.NonNull;
import com.google.android.diskusage.proto.PortableResultProto;

public abstract class PortableResult {

  public abstract void run() throws Exception;


  public static PortableResultProto eval(PortableResult r) {
    PortableResultProto proto = new PortableResultProto();
    try {
      r.run();
      proto.evaluated = true;
    } catch (Exception e) {
      proto.exception = PortableExceptionProtoImpl.makeProto(e);
    }
    return proto;
  }

  private static void replayException(
      PortableResultProto status) throws Exception {
    if (status == null) {
      throw new RuntimeException("cannot replay - no data");
    }
    if (status.exception != null) {
      throw PortableExceptionProtoImpl.create(status.exception);
    } else if (status.evaluated) {
      // all good
    } else {
      throw new RuntimeException("cannot replay, no data");
    }
  }

  public static void replayWithException(
          PortableResultProto status, @NonNull Runnable runnable) throws Exception {
    replayException(status);
    runnable.run();
  }

  public static void replay(PortableResultProto status, Runnable r) {
    try {
      replayException(status);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    r.run();
  }
}
