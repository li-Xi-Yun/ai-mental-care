package org.lixiyun.pojo.bo.conversation.diagnosis.input.clean;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 会话级清洗结果
 * 模块1预清洗的总输出，对应一次完整会话的所有清洗后数据
 */
@Data
@Builder
public class SessionCleanResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 有效等级：valid有效 */
    public static final int LEVEL_VALID = 0;
    /** 有效等级：weak弱语义 */
    public static final int LEVEL_WEAK = 1;
    /** 有效等级：invalid无效 */
    public static final int LEVEL_INVALID = 2;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 按轮次分组的清洗结果列表
     */
    private List<RoundEffectiveLevel> roundEffectiveLevelList;

    public static String getPrompt() {
        return "userId：用户ID，" +
                "conversationId：会话ID，" +
                "roundEffectiveLevelList：" + RoundEffectiveLevel.getPrompt();
    }
}