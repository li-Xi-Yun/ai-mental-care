package com.speech.protocol;

// Compression Type
public enum CompressionType {
    GZIP((byte) 0b0001);

    private final byte code;

    CompressionType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }
}
