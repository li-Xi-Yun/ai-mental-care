package org.lixiyun.pojo.bo.conversation.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 知识侧图中断状态
 * <p>用于标识KnowledgeGraph执行过程中的中断记录，记录知识侧阶段的中断信息</p>
 *
 * @author lixiyun
 * @since 2026-09-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraphState implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NAME = "knowledgeGraphState";

    /**
     * 知识侧图是否中断
     */
    private boolean interrupted;

    /**
     * 中断原因
     */
    private String interruptReason;
}