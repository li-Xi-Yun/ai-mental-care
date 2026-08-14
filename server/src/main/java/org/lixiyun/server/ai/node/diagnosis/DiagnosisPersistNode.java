package org.lixiyun.server.ai.node.diagnosis;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.diagnosis.DiagnosisData;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 诊断流程-诊断数据持久化节点
 * <p>将处理侧输出的DiagnosisData转换为EmotionDiagnosis实体，使用MyBatis-Plus流式插入诊断表</p>
 *
 * @author lixiyun
 * @since 2026-08-14 12:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisPersistNode implements NodeActionWithConfig {

    public static final String NODE_NAME = "diagnosisPersistNode";

    private final EmotionDiagnosisMapper emotionDiagnosisMapper;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("诊断流程-诊断数据持久化-开始");

        Optional<DiagnosisData> diagnosisDataOpt = state.value(DiagnosisData.NAME);
        if (diagnosisDataOpt.isEmpty()) {
            log.error("诊断流程-诊断数据持久化-诊断数据为空");
            throw new BusinessException(ConversationExceptionEnum.DIAGNOSIS_DATA_RESULT_NOT_EXIST);
        }

        Optional<ConversationProcessContextBO> contextBOOpt = state.value(ConversationProcessContextBO.NAME);
        if (contextBOOpt.isEmpty()) {
            log.error("诊断流程-诊断数据持久化-会话上下文为空");
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_PROCESS_CONTEXT_NOT_EXIST);
        }

        DiagnosisData diagnosisData = diagnosisDataOpt.get();
        ConversationProcessContextBO contextBO = contextBOOpt.get();

        EmotionDiagnosis emotionDiagnosis = convertToEntity(diagnosisData, contextBO);

        emotionDiagnosisMapper.insert(emotionDiagnosis);
        log.info("诊断流程-诊断数据持久化-保存成功，会话ID：{}", emotionDiagnosis.getConversationId());

        return Map.of();
    }

    private EmotionDiagnosis convertToEntity(DiagnosisData data, ConversationProcessContextBO contextBO) {
        EmotionDiagnosis emotionDiagnosis = BeanUtil.copyProperties(data, EmotionDiagnosis.class);
        emotionDiagnosis.setConversationId(contextBO.getConversation().getId());
        emotionDiagnosis.setUserId(contextBO.getConversation().getUserId());
        return emotionDiagnosis;
    }
}