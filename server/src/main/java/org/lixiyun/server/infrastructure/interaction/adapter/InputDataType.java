package org.lixiyun.server.infrastructure.interaction.adapter;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 输入数据类型枚举
 *
 * <p>定义输入层支持的数据类型，用于 InputAdapter 的类型标识和工厂的适配器分发。
 * 与输出层 DataType 枚举独立，仅描述输入侧的数据格式。</p>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
public enum InputDataType {

    TEXT,
    AUDIO;

    /**
     * 从字符串集合转换为 InputDataType 枚举集合
     *
     * @param types 字符串类型集合（大小写不敏感）
     * @return InputDataType 枚举集合
     * @throws IllegalArgumentException 如果某字符串不是合法枚举值
     */
    public static Set<InputDataType> fromStrings(Set<String> types) {
        return types.stream()
                .map(String::toUpperCase)
                .map(InputDataType::valueOf)
                .collect(Collectors.toSet());
    }
}