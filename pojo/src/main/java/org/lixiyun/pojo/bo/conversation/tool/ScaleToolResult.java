package org.lixiyun.pojo.bo.conversation.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 量表测评工具执行结果（tool_result字段，action_type=1）
 *
 * @author lixiyun
 * @since 2026-10-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleToolResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 分析Agent产出的核心分析文本
     */
    private String analysisText;

}