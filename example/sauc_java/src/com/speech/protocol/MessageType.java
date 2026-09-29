package com.speech.protocol;

// Message Type
public enum MessageType {
    CLIENT_FULL_REQUEST((byte) 0b0001),
    CLIENT_AUDIO_ONLY_REQUEST((byte) 0b0010),
    SERVER_FULL_RESPONSE((byte) 0b1001),
    SERVER_ERROR_RESPONSE((byte) 0b1111);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
