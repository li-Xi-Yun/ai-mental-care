package org.lixiyun.pojo.bo.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话元数据信息
 * <p>封装AI流程执行期间需要的会话元数据（conversationId、userId、currentRound），
 * 供AOP切面从OverAllState中直接获取，避免从不同子图的不同入参中反复提取。</p>
 *
 * @author lixiyun
 * @since 2026-09-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NAME = "conversationMetadata";

    /** 会话ID */
    private Long conversationId;

    /** 用户ID */
    private Long userId;

    /** 当前轮次 */
    private Integer currentRound;
}