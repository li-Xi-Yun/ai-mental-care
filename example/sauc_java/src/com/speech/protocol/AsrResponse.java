package com.speech.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

// 响应解析结果类
public class AsrResponse {
    public int code;
    public int event;
    public boolean isLastPackage;
    public int payloadSequence;
    public int payloadSize;
    public String payloadMsg;

    @Override
    public String toString() {
        return String.format("AsrResponse{code=%d, event=%d, isLastPackage=%s, payloadSequence=%d, payloadSize=%d, payloadMsg='%s'}",
            code, event, isLastPackage, payloadSequence, payloadSize, payloadMsg);
    }

    public static AsrResponse parseResponse(byte[] res) {
        if (res == null || res.length == 0) {
            return new AsrResponse();
        }

        AsrResponse result = new AsrResponse();

        // 解析头部
        int protocolVersion = (res[0] >> 4) & 0x0f;
        int headerSize = res[0] & 0x0f;
        int messageType = (res[1] >> 4) & 0x0f;
        int messageTypeSpecificFlags = res[1] & 0x0f;
        int serializationMethod = (res[2] >> 4) & 0x0f;
        int messageCompression = res[2] & 0x0f;
        int reserved = res[3];

        // 解析payload
        byte[] payload = Arrays.copyOfRange(res, headerSize * 4, res.length);

        // 解析messageTypeSpecificFlags
        if ((messageTypeSpecificFlags & 0x01) != 0) {
            result.payloadSequence = bytesToInt(Arrays.copyOfRange(payload, 0, 4));
            payload = Arrays.copyOfRange(payload, 4, payload.length);
        }
        if ((messageTypeSpecificFlags & 0x02) != 0) {
            result.isLastPackage = true;
        }
        if ((messageTypeSpecificFlags & 0x04) != 0) {
            result.event = bytesToInt(Arrays.copyOfRange(payload, 0, 4));
            payload = Arrays.copyOfRange(payload, 4, payload.length);
        }

        // 解析messageType
        MessageType responseType = MessageType.fromCode(messageType);
        if (responseType != null) {
            switch (responseType) {
                case SERVER_FULL_RESPONSE:
                    result.payloadSize = bytesToInt(Arrays.copyOfRange(payload, 0, 4));
                    payload = Arrays.copyOfRange(payload, 4, payload.length);
                    break;
                case SERVER_ERROR_RESPONSE:
                    result.code = bytesToInt(Arrays.copyOfRange(payload, 0, 4));
                    result.payloadSize = bytesToInt(Arrays.copyOfRange(payload, 4, 8));
                    payload = Arrays.copyOfRange(payload, 8, payload.length);
                    break;
                default:
                    break;
            }
        }

        if (payload.length == 0) {
            return result;
        }

        // 是否压缩
        if (messageCompression == CompressionType.GZIP.getCode()) {
            payload = gzipDecompress(payload);
        }

        // 解析payload
        if (serializationMethod == SerializationType.JSON.getCode() && payload != null) {
            result.payloadMsg = new String(payload);
        }

        return result;
    }

    private static int bytesToInt(byte[] src) {
        if (src == null || (src.length != 4)) {
            throw new IllegalArgumentException("Invalid byte array for int conversion");
        }
        // 使用大端序（Big Endian）解析，与协议保持一致
        return ((src[0] & 0xFF) << 24)
                | ((src[1] & 0xff) << 16)
                | ((src[2] & 0xff) << 8)
                | ((src[3] & 0xff));
    }

    private static byte[] gzipDecompress(byte[] src) {
        if (src == null || src.length == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream ins = new ByteArrayInputStream(src);
        try (GZIPInputStream gzip = new GZIPInputStream(ins)) {
            byte[] buffer = new byte[256];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return out.toByteArray();
    }
}
