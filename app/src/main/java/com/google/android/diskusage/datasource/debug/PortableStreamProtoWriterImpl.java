package com.google.android.diskusage.datasource.debug;

import android.support.annotation.NonNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.android.diskusage.proto.PortableStreamProto;
import com.google.protobuf.ByteString;

import org.jetbrains.annotations.Contract;

public class PortableStreamProtoWriterImpl extends InputStream {
  interface CloseCallback {
    void onClose(PortableStreamProto proto);
  }

  ByteArrayOutputStream mirror;
  private final InputStream is;
  private final PortableStreamProto.Builder proto;
  private final CloseCallback callback;

  private PortableStreamProtoWriterImpl(
      InputStream is, CloseCallback callback) {
    this.mirror = new ByteArrayOutputStream();
    this.proto = PortableStreamProto.newBuilder();
    this.is = is;
    this.callback = callback;
  }

  @NonNull
  @Contract("_, _ -> new")
  public static PortableStreamProtoWriterImpl create(
      InputStream is, CloseCallback callback) {
    return new PortableStreamProtoWriterImpl(is, callback);
  }

  private void closeMirror() throws IOException {
    if (mirror != null) {
      mirror.close();
      proto.setData(ByteString.copyFrom(mirror.toByteArray()));
      callback.onClose(proto.build());
      mirror = null;
    }
  }

  @Override
  public void close() throws IOException {
    try {
      is.close();
    } catch (IOException e) {
      proto.setCloseException(PortableExceptionProtoImpl.makeProto(e));
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
      proto.setReadException(PortableExceptionProtoImpl.makeProto(e));
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
      proto.setReadException(PortableExceptionProtoImpl.makeProto(e));
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
