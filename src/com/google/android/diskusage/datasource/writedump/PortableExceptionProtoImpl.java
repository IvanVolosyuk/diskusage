package com.google.android.diskusage.datasource.writedump;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;

import com.google.android.diskusage.proto.PortableExceptionProto;

public class PortableExceptionProtoImpl {
  public static PortableExceptionProto makeProto(Exception e) {
    PortableExceptionProto ex = new PortableExceptionProto();
    ex.class_ = e.getClass().getName();
    ex.msg = e.getMessage();
    try {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(os);
      e.printStackTrace(printStream);
      printStream.close();
      os.close();
      ex.stack = os.toString();
    } catch (IOException ee) {
      ex.stack = "Failed to obtain";
    }
    return ex;
  }

  @SuppressWarnings("unchecked")
  public static Exception create(PortableExceptionProto ex) {
    if (ex == null) {
      return null;
    }
    Exception e;
    try {
      Class<? extends Exception> clazz = (Class<? extends Exception>) Class.forName(ex.class_);
      Constructor<? extends Exception> c = clazz.getDeclaredConstructor(String.class);
      e = c.newInstance(ex.msg);
      return e;
    } catch (Throwable t) {
      return new RuntimeException(String.format(
          "Failed to restore exception: %s: %s", ex.class_, ex.msg));
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
