package com.google.android.diskusage.datasource.debug;

import androidx.annotation.NonNull;
import com.google.android.diskusage.proto.PortableResultProto;

public abstract class PortableResult {

  public abstract void run() throws Exception;


  public static PortableResultProto eval(PortableResult r) {
    PortableResultProto.Builder proto = PortableResultProto.newBuilder();
    try {
      r.run();
      proto.setEvaluated(true);
    } catch (Exception e) {
      proto.setException(PortableExceptionProtoImpl.makeProto(e));
    }
    return proto.build();
  }

  private static void replayException(
      PortableResultProto status) throws Exception {
    if (status == null) {
      throw new RuntimeException("cannot replay - no data");
    }
    if (status.getException() != null) {
      throw PortableExceptionProtoImpl.create(status.getException());
    } else if (status.getEvaluated()) {
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
