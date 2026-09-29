package org.lixiyun.common.agent.asr.vendor.volcengine.converter;

import org.lixiyun.common.agent.asr.api.AsrProviderType;
import org.lixiyun.common.agent.asr.model.AsrResult;
import org.lixiyun.common.agent.asr.vendor.volcengine.model.VolcengineAsrResult;
import org.lixiyun.common.agent.asr.vendor.volcengine.protocol.AsrResponseParser;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山引擎ASR数据转换器
 * <p>
 * 提供两个核心转换方法：
 * <ul>
 * <li>toCommonResult：将二进制帧原始数据转换为通用业务实体类 AsrResult</li>
 * <li>toVendorResult：将二进制帧原始数据转换为与API文档字段一一对应的 VolcengineAsrResult</li>
 * </ul>
 */
public final class VolcengineAsrConverter {

    private VolcengineAsrConverter() {
    }

    /**
     * 将原始ASR返回数据（二进制帧）转换为通用业务实体类
     *
     * @param rawResponse    二进制帧字节数组
     * @param conversationId 会话ID
     * @param providerType   厂商类型
     * @return 标准化的AsrResult
     * @throws IOException 解析失败时抛出
     */
    public static AsrResult toCommonResult(byte[] rawResponse, Long conversationId, AsrProviderType providerType)
            throws IOException {
        VolcengineAsrResult vendorResult = toVendorResult(rawResponse);
        return toCommonResult(vendorResult, conversationId, providerType);
    }

    /**
     * 将火山引擎ASR厂商返回数据转换为通用业务实体类
     *
     * @param vendorResult   火山引擎ASR返回数据实体
     * @param conversationId 会话ID
     * @param providerType   厂商类型
     * @return 标准化的AsrResult
     */
    public static AsrResult toCommonResult(VolcengineAsrResult vendorResult, Long conversationId,
                                           AsrProviderType providerType) {
        // 确定结果状态码
        int resultCode = determineResultCode(vendorResult);

        VolcengineAsrResult.Result result = vendorResult.getResult();
        if (result == null) {
            return AsrResult.builder()
                    .conversationId(conversationId)
                    .providerType(providerType)
                    .resultCode(resultCode)
                    .build();
        }

        AsrResult.AsrResultBuilder builder = AsrResult.builder()
                .conversationId(conversationId)
                .providerType(providerType)
                .resultCode(resultCode);

        List<VolcengineAsrResult.Utterance> utterances = result.getUtterances();
        if (utterances != null && !utterances.isEmpty()) {
            VolcengineAsrResult.Utterance target = findBestUtterance(utterances);

            builder.text(target.getText())
                    .startTime((long) target.getStartTime())
                    .endTime((long) target.getEndTime());

            builder.sentenceIndex(countDefiniteUtterances(utterances));

            VolcengineAsrResult.UtteranceAdditions additions = target.getAdditions();
            if (additions != null) {
                builder.emotion(additions.getEmotion())
                        .emotionDegree(additions.getEmotionDegree())
                        .emotionDegreeScore(parseDoubleOrNull(additions.getEmotionDegreeScore()))
                        .emotionScore(parseDoubleOrNull(additions.getEmotionScore()))
                        .gender(additions.getGender())
                        .genderScore(parseDoubleOrNull(additions.getGenderScore()))
                        .age(additions.getAge())
                        .speechRate(parseDoubleOrNull(additions.getSpeechRate()))
                        .volume(parseDoubleOrNull(additions.getVolume()));

                builder.extra(buildUtteranceExtra(additions));
            }
        } else if (result.getText() != null) {
            builder.text(result.getText());
        }

        return builder.build();
    }

    /**
     * 将原始ASR返回数据（二进制帧）转换为火山引擎ASR返回数据实体类
     *
     * @param rawResponse 二进制帧字节数组
     * @return 火山引擎ASR返回数据实体，二进制帧头字段已由AsrResponseParser补全
     * @throws IOException 解析失败时抛出
     */
    public static VolcengineAsrResult toVendorResult(byte[] rawResponse) throws IOException {
        return AsrResponseParser.parse(rawResponse);
    }

    // ==================== 私有辅助方法 ====================

    private static int determineResultCode(VolcengineAsrResult vendorResult) {
        VolcengineAsrResult.Result result = vendorResult.getResult();
        if (result != null) {
            List<VolcengineAsrResult.Utterance> utterances = result.getUtterances();
            if (utterances != null && utterances.stream().anyMatch(VolcengineAsrResult.Utterance::isDefinite)) {
                return AsrResult.SENTENCE_END;
            }
        }
        return AsrResult.INTERMEDIATE;
    }

    private static int countDefiniteUtterances(List<VolcengineAsrResult.Utterance> utterances) {
        return (int) utterances.stream().filter(VolcengineAsrResult.Utterance::isDefinite).count();
    }

    private static VolcengineAsrResult.Utterance findBestUtterance(List<VolcengineAsrResult.Utterance> utterances) {
        for (int i = utterances.size() - 1; i >= 0; i--) {
            if (utterances.get(i).isDefinite()) {
                return utterances.get(i);
            }
        }
        return utterances.get(utterances.size() - 1);
    }

    /**
     * 将Utterance级扩展属性中没有AsrResult独立字段的剩余属性放入AsrResult.extra
     */
    private static Map<String, Object> buildUtteranceExtra(VolcengineAsrResult.UtteranceAdditions additions) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("source", additions.getSource());
        extra.put("speaker_id", additions.getSpeakerId());
        extra.put("fixed_prefix_result", additions.getFixedPrefixResult());
        extra.put("invoke_type", additions.getInvokeType());
        extra.put("use_bigasr_post_process", additions.getUseBigasrPostProcess());
        extra.put("all_matched_hotwords", additions.getAllMatchedHotwords());
        return extra;
    }

    /**
     * 将字符串解析为Double，解析失败返回null
     */
    private static Double parseDoubleOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}