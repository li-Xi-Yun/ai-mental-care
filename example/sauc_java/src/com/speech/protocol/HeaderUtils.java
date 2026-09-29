package com.speech.protocol;

import okhttp3.Request;

public final class HeaderUtils {
    private HeaderUtils() {
    }

    // 新版控制台鉴权: X-Api-Key；老版控制台鉴权: X-Api-App-Key + X-Api-Access-Key
    public static void applyAuthHeaders(Request.Builder builder, String apiKey, String appKey, String accessKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("X-Api-Key", apiKey);
        } else {
            builder.header("X-Api-App-Key", appKey);
            builder.header("X-Api-Access-Key", accessKey);
        }
    }

    // 组装4字节协议帧请求头
    public static byte[] getHeader(byte messageType, byte messageTypeSpecificFlags,
            byte serialMethod, byte compressionType, byte reservedData) {
        final byte[] header = new byte[4];
        header[0] = (byte) ((ProtocolConstants.PROTOCOL_VERSION << 4) | ProtocolConstants.DEFAULT_HEADER_SIZE);
        header[1] = (byte) ((messageType << 4) | messageTypeSpecificFlags);
        header[2] = (byte) ((serialMethod << 4) | compressionType);
        header[3] = reservedData;
        return header;
    }
}
