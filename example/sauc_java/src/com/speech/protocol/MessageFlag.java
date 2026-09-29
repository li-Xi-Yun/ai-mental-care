package com.speech.protocol;

// Message Type Specific Flags
public enum MessageFlag {
    NO_SEQUENCE((byte) 0b0000),
    POS_SEQUENCE((byte) 0b0001),
    NEG_SEQUENCE((byte) 0b0010),
    NEG_WITH_SEQUENCE((byte) 0b0011);

    private final byte code;

    MessageFlag(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }
}
