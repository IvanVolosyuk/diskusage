package com.google.android.diskusage.datasource.debug;

import androidx.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;

import com.google.android.diskusage.proto.PortableExceptionProto;

public class PortableExceptionProtoImpl {
  @NonNull
  public static PortableExceptionProto makeProto(@NonNull Exception e) {
    PortableExceptionProto.Builder ex = PortableExceptionProto.newBuilder()
            .setClass_(e.getClass().getName())
            .setMsg(e.getMessage());
    try {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(os);
      e.printStackTrace(printStream);
      printStream.close();
      os.close();
      ex.setStack(os.toString());
    } catch (IOException ee) {
      ex.setStack("Failed to obtain");
    }
    return ex.build();
  }

  @SuppressWarnings("unchecked")
  public static Exception create(PortableExceptionProto ex) {
    if (ex == null) {
      return null;
    }
    Exception e;
    try {
      Class<? extends Exception> clazz = (Class<? extends Exception>) Class.forName(ex.getClass_());
      Constructor<? extends Exception> c = clazz.getDeclaredConstructor(String.class);
      e = c.newInstance(ex.getMsg());
      return e;
    } catch (Throwable t) {
      return new RuntimeException(String.format(
          "Failed to restore exception: %s: %s", ex.getClass_(), ex.getMsg()));
    }
  }

  public static void throwRuntimeException(PortableExceptionProto ex) {
    Exception e = create(ex);
    if (e == null) {
      return;
    }
    if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    } else {
      throw new RuntimeException("Unexpected exception", e);
    }
  }
  public static void throwIOException(PortableExceptionProto ex) throws IOException {
    Exception e = PortableExceptionProtoImpl.create(ex);
    if (e == null) {
      return;
    }
    if (e instanceof IOException) {
      throw (IOException) e;
    } else if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    } else {
      throw new RuntimeException("Cannot throw exception", e);
    }
  }

}
