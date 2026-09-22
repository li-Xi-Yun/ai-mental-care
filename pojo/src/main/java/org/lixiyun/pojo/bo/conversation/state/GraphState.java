package org.lixiyun.pojo.bo.conversation.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图执行中断状态
 * <p>用于标识诊断主流程图执行过程中的中断记录，包含输入侧、知识侧、处理侧三个阶段的中断状态</p>
 *
 * <p>常量说明：</p>
 * <ul>
 *   <li>{@link #END} —— 条件边路由：结束图执行</li>
 *   <li>{@link #PROCESS} —— 条件边路由：继续执行下一个节点</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-09-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphState implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NAME = "graphState";

    // ============================================条件边路由常量==================================================

    /**
     * 条件边路由：结束图执行
     */
    public static final String END = "end";

    /**
     * 条件边路由：继续执行
     */
    public static final String PROCESS = "process";

    // ============================================三个阶段的中断状态==================================================

    /**
     * 输入侧图中断状态
     */
    private InputGraphState inputGraphState;

    /**
     * 知识侧图中断状态
     */
    private KnowledgeGraphState knowledgeGraphState;

    /**
     * 处理侧图中断状态
     */
    private ProcessGraphState processGraphState;
}