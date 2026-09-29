package com.speech.protocol;

// Serialization Type
public enum SerializationType {
    NO_SERIALIZATION((byte) 0b0000),
    JSON((byte) 0b0001);

    private final byte code;

    SerializationType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }
}
