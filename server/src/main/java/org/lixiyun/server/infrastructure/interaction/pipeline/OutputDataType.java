package org.lixiyun.server.infrastructure.interaction.pipeline;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管道中流动的输出数据类型
 *
 * <p>定义输出层支持的数据类型，用于：
 * <ul>
 *   <li>OutputPipelineFactory 根据 outputTypes 组装节点链</li>
 *   <li>各 OutputNode 在 process() 中通过 context.hasOutputType() 判断是否附加推送副作用</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
public enum OutputDataType {

    TEXT,
    AUDIO;

    /**
     * 从字符串集合转换为 OutputDataType 枚举集合
     *
     * @param types 字符串类型集合（大小写不敏感）
     * @return OutputDataType 枚举集合
     * @throws IllegalArgumentException 如果某字符串不是合法枚举值
     */
    public static Set<OutputDataType> fromStrings(Set<String> types) {
        return types.stream()
                .map(String::toUpperCase)
                .map(OutputDataType::valueOf)
                .collect(Collectors.toSet());
    }
}