package com.google.android.diskusage.datasource.debug;

import android.support.annotation.NonNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.android.diskusage.proto.PortableStreamProto;

import org.jetbrains.annotations.Contract;

public class PortableStreamProtoReaderImpl extends InputStream {
  private final PortableStreamProto proto;
  private final ByteArrayInputStream is;

  public PortableStreamProtoReaderImpl(@NonNull PortableStreamProto proto) {
    this.proto = proto;
    this.is = new ByteArrayInputStream(proto.getData().toByteArray());
  }

  @NonNull
  @Contract("_ -> new")
  public static PortableStreamProtoReaderImpl create(
      PortableStreamProto proto) {
    return new PortableStreamProtoReaderImpl(proto);
  }

  @Override
  public int read() throws IOException {
    int res = is.read();
    if (res == -1) {
      PortableExceptionProtoImpl.throwIOException(proto.getReadException());
    }
    return res;
  }

  @Override
  public int read(byte[] buffer, int byteOffset, int byteCount)
      throws IOException {
    int res = is.read(buffer, byteOffset, byteCount);
    if (res == -1) {
      PortableExceptionProtoImpl.throwIOException(proto.getReadException());
    }
    return res;
  }

  @Override
  public void close() throws IOException {
    is.close();
    PortableExceptionProtoImpl.throwIOException(proto.getCloseException());
  }
}
