package org.lixiyun.common.agent.asr.vendor.volcengine.protocol;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.agent.asr.vendor.volcengine.model.VolcengineAsrResult;
import org.lixiyun.common.json.utils.JsonUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 火山引擎ASR二进制帧响应解析器
 * <p>将WebSocket接收到的原始字节数组解析为{@link VolcengineAsrResult}。
 * 解析流程：读取4字节协议头 → 提取JSON载荷 → 按需GZIP解压 → 使用{@link JsonUtils}反序列化载荷 →
 * 手动补全二进制帧头中的{@code code}/{@code isLastPackage}/{@code payloadSequence}等字段。</p>
 *
 * <p><b>注意：</b>二进制载荷JSON不包含外层的{@code code}/{@code is_last_package}等字段，
 * 这些信息从二进制帧头标志位读取后由Parser手动设置到{@code VolcengineAsrResult}中。</p>
 */
@Slf4j
public final class AsrResponseParser {

    private AsrResponseParser() {
    }

    /**
     * 解析WebSocket二进制响应帧为火山引擎ASR返回数据实体类
     *
     * @param rawMessage 原始二进制帧字节数组
     * @return 火山引擎ASR返回数据实体，二进制帧头字段已手动补全
     * @throws IOException 解析失败时抛出
     */
    public static VolcengineAsrResult parse(byte[] rawMessage) throws IOException {
        log.debug("[火山引擎-ASR解析器] 收到原始二进制帧，长度={}", rawMessage != null ? rawMessage.length : 0);

        if (rawMessage == null || rawMessage.length < 4) {
            log.error("[火山引擎-ASR解析器] 消息长度不足，无法解析协议头，length={}", rawMessage != null ? rawMessage.length : 0);
            throw new IOException("消息长度不足，无法解析协议头");
        }

        int offset = 0;

        // 第0字节：高4位=协议版本，低4位=头部长度（固定4字节）
        byte firstByte = rawMessage[offset];
        log.debug("[火山引擎-ASR解析器] 协议头[0] 原始值=0x{}, 协议版本={}(高4位), 头部大小={}(低4位)",
                Integer.toHexString(firstByte & 0xFF), (firstByte >> 4) & 0x0F, firstByte & 0x0F);
        offset += 1;

        // 第1字节：高4位=消息类型，低4位=消息标志
        byte secondByte = rawMessage[offset];
        byte messageType = (byte) ((secondByte >> 4) & 0x0F);
        byte flags = (byte) ((secondByte) & 0x0F);
        log.debug("[火山引擎-ASR解析器] 协议头[1] 原始值=0x{}, 消息类型={}, 消息标志=0x{}",
                Integer.toHexString(secondByte & 0xFF), messageType, Integer.toHexString(flags & 0xFF));
        offset += 1;

        // 判断是否携带序列号
        boolean hasSequence = (flags & MessageFlag.POS_SEQUENCE.getCode()) != 0;
        // 判断是否为最后一个响应包（flags位2）
        boolean isLastPackage = (flags & 0x02) != 0;
        // 判断是否携带事件
        boolean hasEvent = (flags & 0x04) != 0;
        log.debug("[火山引擎-ASR解析器] 标志位解析：hasSequence={}, isLastPackage={}, hasEvent={}",
                hasSequence, isLastPackage, hasEvent);

        // 第2字节：高4位=序列化方式，低4位=压缩方式（服务端可选GZIP压缩）
        byte thirdByte = rawMessage[offset];
        byte serializationType = (byte) ((thirdByte >> 4) & 0x0F);
        byte compressionType = (byte) ((thirdByte) & 0x0F);
        log.debug("[火山引擎-ASR解析器] 协议头[2] 原始值=0x{}, 序列化方式={}, 压缩方式={}",
                Integer.toHexString(thirdByte & 0xFF), serializationType, compressionType);
        offset += 2; // 跳过序列化/压缩标志字节和保留字节

        MessageType type = MessageType.fromCode(messageType);
        if (type == null) {
            log.error("[火山引擎-ASR解析器] 未知消息类型: {}", messageType);
            throw new IOException("未知消息类型: " + messageType);
        }

        log.debug("[火山引擎-ASR解析器] 消息类型={}，偏移量offset={}，进入对应解析分支", type, offset);
        return switch (type) {
            case SERVER_FULL_RESPONSE -> parseFullResponse(rawMessage, offset, hasSequence, hasEvent, isLastPackage, compressionType);
            case SERVER_ERROR_RESPONSE -> parseErrorResponse(rawMessage, offset, compressionType);
            default -> throw new IOException("客户端不应接收该消息类型: " + type);
        };
    }

    /**
     * 解析服务端完整响应帧，按需解压后反序列化为VolcengineAsrResult，
     * 并将二进制帧头中的code/isLastPackage/payloadSequence/payloadSize补全到实体中
     */
    private static VolcengineAsrResult parseFullResponse(byte[] rawMessage, int offset,
                                                         boolean hasSequence, boolean hasEvent,
                                                         boolean isLastPackage, byte compressionType) throws IOException {
        log.debug("[火山引擎-ASR解析器] parseFullResponse开始，offset={}, hasSequence={}, hasEvent={}, isLastPackage={}, compressionType={}",
                offset, hasSequence, hasEvent, isLastPackage, compressionType);

        int payloadSequence = 0;

        // 读取序号（4字节）
        if (hasSequence) {
            payloadSequence = ProtocolCodec.bytesToInt(
                    new byte[]{rawMessage[offset], rawMessage[offset + 1], rawMessage[offset + 2], rawMessage[offset + 3]});
            log.debug("[火山引擎-ASR解析器] 读取payloadSequence={}，offset从{}变为{}", payloadSequence, offset, offset + 4);
            offset += 4;
        }

        // 跳过事件编号（4字节）
        if (hasEvent) {
            int eventId = ProtocolCodec.bytesToInt(
                    new byte[]{rawMessage[offset], rawMessage[offset + 1], rawMessage[offset + 2], rawMessage[offset + 3]});
            log.debug("[火山引擎-ASR解析器] 跳过事件编号={}，offset从{}变为{}", eventId, offset, offset + 4);
            offset += 4;
        }

        // 读取载荷长度（4字节）
        int payloadSize = ProtocolCodec.bytesToInt(
                new byte[]{rawMessage[offset], rawMessage[offset + 1], rawMessage[offset + 2], rawMessage[offset + 3]});
        log.debug("[火山引擎-ASR解析器] 读取载荷长度={}，offset从{}变为{}", payloadSize, offset, offset + 4);
        offset += 4;

        // 读取载荷并解压
        if (payloadSize <= 0 || offset + payloadSize > rawMessage.length) {
            log.error("[火山引擎-ASR解析器] 载荷大小异常，payloadSize={}, offset={}, rawMessage.length={}",
                    payloadSize, offset, rawMessage.length);
            throw new IOException("载荷大小异常: " + payloadSize);
        }

        byte[] payload = new byte[payloadSize];
        System.arraycopy(rawMessage, offset, payload, 0, payloadSize);
        log.debug("[火山引擎-ASR解析器] 载荷复制完成，原始大小={}字节，offset结束位置={}", payloadSize, offset + payloadSize);

        // 仅当服务端返回GZIP压缩时才解压
        if (compressionType == CompressionType.GZIP.getCode()) {
            int compressedSize = payload.length;
            payload = ProtocolCodec.gzipDecompress(payload);
            log.debug("[火山引擎-ASR解析器] GZIP解压完成，压缩前={}字节，解压后={}字节", compressedSize, payload.length);
        } else {
            log.debug("[火山引擎-ASR解析器] 无需解压，compressionType={}", compressionType);
        }

        // 使用项目统一JsonUtils反序列化载荷JSON
        String payloadStr = new String(payload, StandardCharsets.UTF_8);
        log.debug("[火山引擎-ASR解析器] 反序列化前载荷内容: {}", payloadStr);
        VolcengineAsrResult result = JsonUtils.parseObject(payload, VolcengineAsrResult.class);
        if (result == null) {
            log.debug("[火山引擎-ASR解析器] JsonUtils反序列化返回null，创建空VolcengineAsrResult");
            result = new VolcengineAsrResult();
        }

        // 补全二进制帧头字段
        result.setCode(0);
        result.setLastPackage(isLastPackage);
        result.setPayloadSequence(payloadSequence);
        result.setPayloadSize(payloadSize);

        log.debug("[火山引擎-ASR解析器] parseFullResponse完成：isLastPackage={}, payloadSequence={}, payloadSize={}, result={}",
                isLastPackage, payloadSequence, payloadSize, payloadStr);
        return result;
    }

    /**
     * 解析服务端错误响应帧，按需解压
     */
    private static VolcengineAsrResult parseErrorResponse(byte[] rawMessage, int offset,
                                                          byte compressionType) throws IOException {
        log.debug("[火山引擎-ASR解析器] parseErrorResponse开始，offset={}, compressionType={}", offset, compressionType);

        // 错误码（4字节）
        int errorCode = ProtocolCodec.bytesToInt(
                new byte[]{rawMessage[offset], rawMessage[offset + 1], rawMessage[offset + 2], rawMessage[offset + 3]});
        log.debug("[火山引擎-ASR解析器] 读取错误码={}，offset从{}变为{}", errorCode, offset, offset + 4);
        offset += 4;

        // 载荷长度（4字节）
        int payloadSize = ProtocolCodec.bytesToInt(
                new byte[]{rawMessage[offset], rawMessage[offset + 1], rawMessage[offset + 2], rawMessage[offset + 3]});
        log.debug("[火山引擎-ASR解析器] 读取错误载荷长度={}，offset从{}变为{}", payloadSize, offset, offset + 4);
        offset += 4;

        // 构造错误响应
        VolcengineAsrResult result = new VolcengineAsrResult();
        result.setCode(errorCode);
        result.setEvent(0);
        result.setLastPackage(true);

        if (payloadSize > 0 && offset + payloadSize <= rawMessage.length) {
            byte[] payload = new byte[payloadSize];
            System.arraycopy(rawMessage, offset, payload, 0, payloadSize);
            log.debug("[火山引擎-ASR解析器] 错误载荷复制完成，大小={}字节", payloadSize);

            if (compressionType == CompressionType.GZIP.getCode()) {
                int compressedSize = payload.length;
                payload = ProtocolCodec.gzipDecompress(payload);
                log.debug("[火山引擎-ASR解析器] 错误载荷GZIP解压完成，压缩前={}字节，解压后={}字节", compressedSize, payload.length);
            } else {
                log.debug("[火山引擎-ASR解析器] 错误载荷无需解压，compressionType={}", compressionType);
            }

            String errorMsg = new String(payload, StandardCharsets.UTF_8);
            log.debug("[火山引擎-ASR解析器] 错误消息内容: {}", errorMsg);

            VolcengineAsrResult.Result errorResult = new VolcengineAsrResult.Result();
            errorResult.setText(errorMsg);
            result.setResult(errorResult);
        } else {
            log.debug("[火山引擎-ASR解析器] 无错误载荷，payloadSize={}, offset={}, rawMessage.length={}",
                    payloadSize, offset, rawMessage.length);
        }

        log.debug("[火山引擎-ASR解析器] parseErrorResponse完成：errorCode={}, result={}", errorCode, result);
        return result;
    }
}