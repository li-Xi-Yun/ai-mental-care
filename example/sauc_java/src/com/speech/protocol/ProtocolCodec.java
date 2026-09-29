package com.speech.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public final class ProtocolCodec {
    private ProtocolCodec() {
    }

    public static byte[] intToBytes(int a) {
        return new byte[] {
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    public static byte[] gzipCompress(byte[] src) {
        return gzipCompress(src, src.length);
    }

    public static byte[] gzipCompress(byte[] src, int len) {
        if (src == null || len == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(src, 0, len);
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
        return out.toByteArray();
    }
}
