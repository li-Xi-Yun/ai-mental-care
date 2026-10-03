package org.lixiyun.server.infrastructure.interaction.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.InputAdapterExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.infrastructure.interaction.adapter.impl.AudioInputAdapter;
import org.lixiyun.server.infrastructure.interaction.adapter.impl.TextInputAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 输入适配器工厂
 *
 * <p>核心职责：根据调用方传入的输入方式（InputDataType）集合，完成适配器的查找与组装。
 * 是输入层"组装配置"的唯一入口，只做纯查找，不负责资源初始化（init 由 InputAdapterSessionManager 统一调用）。</p>
 *
 * <p>设计要点：
 * <ul>
 * <li>适配器声明为 prototype 作用域，且持有会话级状态（userId / conversationId），
 *     <b>必须为每个会话创建独立的适配器实例</b></li>
 * <li>通过 {@link ObjectProvider} 注入适配器：每次 {@code getObject()} 向容器请求全新实例，
 *     避免集合注入使 prototype 退化成"半个单例"导致多会话字段串扰</li>
 * <li>assemble() 仅根据 inputTypes 查找对应适配器并返回新实例，不产生副作用</li>
 * <li>新增输入类型：新增一个 InputAdapter 实现类 + 在 {@link #resolveAdapter} 补充分发规则</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InputAdapterFactory {

    /**
     * 文本输入适配器的对象提供者——每次 getObject() 创建该会话独立的适配器实例
     */
    private final ObjectProvider<TextInputAdapter> textInputAdapterProvider;

    /**
     * 音频输入适配器的对象提供者——每次 getObject() 创建该会话独立的适配器实例
     */
    private final ObjectProvider<AudioInputAdapter> audioInputAdapterProvider;

    /**
     * 根据输入方式集合，查找并返回对应的适配器列表（纯查找，不做初始化）
     *
     * <p>调用时机：InputAdapterSessionManager.register() 内部调用</p>
     *
     * @param inputTypes 客户端请求的输入方式集合（例如 {TEXT, AUDIO}）
     * @return 匹配的适配器列表（未初始化，顺序与 inputTypes 遍历顺序一致）
     * @throws BusinessException 如果 inputTypes 为空或某个 InputDataType 没有对应的适配器实现
     */
    public List<InputAdapter> assemble(Set<InputDataType> inputTypes) {
        if (inputTypes == null || inputTypes.isEmpty()) {
            log.error("[输入适配器工厂] 输入方式集合为空，无法组装适配器");
            throw new BusinessException(InputAdapterExceptionEnum.INPUT_TYPES_EMPTY);
        }

        List<InputAdapter> assembled = new ArrayList<>(inputTypes.size());
        for (InputDataType type : inputTypes) {
            assembled.add(resolveAdapter(type));
        }
        log.debug("[输入适配器工厂] 适配器组装完成，类型：{}，数量：{}", inputTypes, assembled.size());
        return assembled;
    }

    /**
     * 按输入数据类型分发创建适配器新实例（每次调用均为全新 prototype 实例）
     *
     * @param type 输入数据类型
     * @return 对应类型的适配器新实例
     * @throws BusinessException 未注册的输入类型抛出 {@link InputAdapterExceptionEnum#ADAPTER_NOT_FOUND}
     */
    private InputAdapter resolveAdapter(InputDataType type) {
        switch (type) {
            case TEXT:
                return textInputAdapterProvider.getObject();
            case AUDIO:
                return audioInputAdapterProvider.getObject();
            default:
                log.error("[输入适配器工厂] 未找到InputDataType={}对应的适配器实现", type);
                throw new BusinessException(InputAdapterExceptionEnum.ADAPTER_NOT_FOUND);
        }
    }
}