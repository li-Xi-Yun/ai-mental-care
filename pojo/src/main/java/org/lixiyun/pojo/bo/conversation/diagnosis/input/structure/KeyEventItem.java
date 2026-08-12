package org.lixiyun.pojo.bo.conversation.diagnosis.input.structure;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 关键事件项
 * @author lixiyun
 * @since 2026-08-10 14:11
 */
@Data
@Builder
public class KeyEventItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件出现的对话轮次号
     */
    private Integer roundNum;

    /**
     * 事件描述原文
     */
    private String eventDesc;

    public static String getPrompt() {
        return "roundNum：事件出现的对话轮次号，" +
                "eventDesc：事件描述原文";
    }
}