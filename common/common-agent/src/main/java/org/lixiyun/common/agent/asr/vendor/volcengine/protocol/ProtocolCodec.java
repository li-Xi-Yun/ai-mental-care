package org.lixiyun.common.agent.asr.vendor.volcengine.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 火山引擎ASR协议编解码工具类，提供字节转换、GZIP压缩/解压等方法
 */
public final class ProtocolCodec {

    private ProtocolCodec() {
    }

    /**
     * int 转 4字节大端序数组
     */
    public static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /**
     * 4字节大端序数组转 int
     */
    public static int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    /**
     * 合并多个字节数组
     */
    public static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] arr : arrays) {
            totalLength += arr.length;
        }
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }
        return result;
    }

    /**
     * GZIP压缩
     */
    public static byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    /**
     * GZIP解压
     */
    public static byte[] gzipDecompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
        }
        return bos.toByteArray();
    }

    /**
     * GZIP解压并转为UTF-8字符串
     */
    public static String gzipDecompressToString(byte[] compressed) throws IOException {
        return new String(gzipDecompress(compressed), StandardCharsets.UTF_8);
    }

    /**
     * 构建ASR消息帧头部（4字节）
     *
     * @param messageType 消息类型
     * @param flags       消息标志
     * @return 4字节头部
     */
    public static byte[] buildHeader(MessageType messageType, MessageFlag flags) {
        byte[] header = new byte[4];
        header[0] = (byte) ((ProtocolConstants.PROTOCOL_VERSION << 4) | ProtocolConstants.DEFAULT_HEADER_SIZE);
        header[1] = (byte) ((messageType.getCode() << 4) | flags.getCode());
        header[2] = (byte) ((SerializationType.JSON.getCode() << 4) | CompressionType.GZIP.getCode());
        header[3] = 0x00; // reserved
        return header;
    }

    /**
     * 构建完整的ASR二进制消息帧（头部 + 序列号 + 载荷长度 + 压缩后的载荷）
     *
     * @param messageType   消息类型
     * @param flags         消息标志
     * @param sequence      序列号（仅 POS_SEQUENCE 或 NEG_WITH_SEQUENCE 标志时有效）
     * @param payloadBytes  载荷字节数组
     * @return 完整的二进制消息帧
     */
    public static byte[] buildFrame(MessageType messageType, MessageFlag flags, int sequence, byte[] payloadBytes) throws IOException {
        byte[] header = buildHeader(messageType, flags);
        byte[] seqBytes = new byte[0];
        if (flags == MessageFlag.POS_SEQUENCE || flags == MessageFlag.NEG_WITH_SEQUENCE) {
            seqBytes = intToBytes(sequence);
        }
        byte[] compressed = gzipCompress(payloadBytes);
        byte[] sizeBytes = intToBytes(compressed.length);
        return concat(header, seqBytes, sizeBytes, compressed);
    }
}