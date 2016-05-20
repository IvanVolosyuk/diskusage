package com.google.android.diskusage.datasource.debug;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.android.diskusage.proto.PortableStreamProto;

public class PortableStreamProtoWriterImpl extends InputStream {
  interface CloseCallback {
    void onClose(PortableStreamProto proto);
  }

  ByteArrayOutputStream mirror;
  private final InputStream is;
  private final PortableStreamProto proto;
  private final CloseCallback callback;

  private PortableStreamProtoWriterImpl(
      InputStream is, CloseCallback callback) {
    this.mirror = new ByteArrayOutputStream();
    this.proto = new PortableStreamProto();
    this.is = is;
    this.callback = callback;
  }

  public static PortableStreamProtoWriterImpl create(
      InputStream is, CloseCallback callback) {
    return new PortableStreamProtoWriterImpl(is, callback);
  }

  private void closeMirror() throws IOException {
    if (mirror != null) {
      mirror.close();
      proto.data = mirror.toByteArray();
      callback.onClose(proto);
      mirror = null;
    }
  }

  @Override
  public void close() throws IOException {
    try {
      is.close();
    } catch (IOException e) {
      proto.closeException = PortableExceptionProtoImpl.makeProto(e);
      throw e;
    }
    closeMirror();
  }

  protected void finalize() throws Throwable {
    closeMirror();
  }

  @Override
  public int read(byte[] buffer, int offset, int count) throws IOException {
    if (mirror == null) {
      throw new IOException("mirror is already closed");
    }
    final int result;

    try {
      result = is.read(buffer, offset, count);
    } catch (IOException e) {
      proto.readException = PortableExceptionProtoImpl.makeProto(e);
      closeMirror();
      throw e;
    }

    if (result == -1) {
      closeMirror();
      return -1;
    }

    mirror.write(buffer, offset, result);
    return result;
  }

  @Override
  public int read() throws IOException {
    if (mirror == null) {
      throw new IOException("mirror is already closed");
    }
    final int result;

    try {
      result = is.read();
    } catch (IOException e) {
      proto.readException = PortableExceptionProtoImpl.makeProto(e);
      throw e;
    }

    if (result == -1) {
      closeMirror();
      return -1;
    }

    mirror.write(result);
    return result;
  }
}
