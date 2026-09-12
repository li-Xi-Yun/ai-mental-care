package org.lixiyun.pojo.bo.conversation.diagnosis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.knowledge.KnowledgeRetrieveResult;

import java.io.Serializable;

/**
 * 处理测-所有提示词数据
 * @author lixiyun
 * @since 2026-08-13 13:38
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisDataRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NAME = "DiagnosisDataRequest";

    /** 输入侧-所有结果集合 */
    private InputResult inputResult;

    /** 知识侧-检索输出结果 */
    private KnowledgeRetrieveResult knowledgeRetrieveResult;

}