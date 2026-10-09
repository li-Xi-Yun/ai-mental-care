package org.lixiyun.server.ai.node.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.AIChatExceptionEnum;
import org.lixiyun.common.core.error.enums.ScaleExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationMetadata;
import org.lixiyun.pojo.bo.conversation.tool.ScaleToolResult;
import org.lixiyun.pojo.constant.DeleteConstant;
import org.lixiyun.pojo.entity.config.AiNodeConfig;
import org.lixiyun.pojo.entity.scale.Scale;
import org.lixiyun.pojo.entity.scale.ScaleDimension;
import org.lixiyun.pojo.entity.scale.ScaleQuestion;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswer;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswerOption;
import org.lixiyun.pojo.entity.scale.ScaleUserAnswerText;
import org.lixiyun.pojo.entity.scale.ScaleUserDimScore;
import org.lixiyun.pojo.entity.scale.ScaleUserRecord;
import org.lixiyun.pojo.entity.scale.ScaleVersion;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.model.tool.ScaleAnalysisModel;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.mapper.ScaleDimensionMapper;
import org.lixiyun.server.mapper.ScaleMapper;
import org.lixiyun.server.mapper.ScaleQuestionMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerOptionMapper;
import org.lixiyun.server.mapper.ScaleUserAnswerTextMapper;
import org.lixiyun.server.mapper.ScaleUserDimScoreMapper;
import org.lixiyun.server.mapper.ScaleUserRecordMapper;
import org.lixiyun.server.mapper.ScaleVersionMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 量表分析节点
 * <p>
 * 负责将用户完成的量表测评结果（量表元数据 + 测评记录 + 维度得分 + 逐题作答明细 + 对话上下文）
 * 组装为完整的提示词，调用分析模型产出温暖、专业的分析文本。
 * </p>
 *
 * @author lixiyun
 * @since 2026-10-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScaleAnalysisNode {

    public static final String NODE_NAME = "scaleAnalysisNode";

    private final ScaleAnalysisModel scaleAnalysisModel;
    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    private final ScaleUserRecordMapper scaleUserRecordMapper;
    private final ScaleMapper scaleMapper;
    private final ScaleVersionMapper scaleVersionMapper;
    private final ScaleDimensionMapper scaleDimensionMapper;
    private final ScaleQuestionMapper scaleQuestionMapper;
    private final ScaleUserDimScoreMapper scaleUserDimScoreMapper;
    private final ScaleUserAnswerMapper scaleUserAnswerMapper;
    private final ScaleUserAnswerOptionMapper scaleUserAnswerOptionMapper;
    private final ScaleUserAnswerTextMapper scaleUserAnswerTextMapper;

    /**
     * 分析用户量表测评结果
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param scaleRecordId  测评记录ID（scale_user_record.id）
     * @return 分析结果（含 analysisText）
     */
    public ScaleToolResult apply(Long conversationId, Long userId, Long scaleRecordId) throws BusinessException {
        log.debug("[量表分析节点] 开始执行，会话ID：{}，用户ID：{}，测评记录ID：{}",
                conversationId, userId, scaleRecordId);

        // 1. 加载测评记录与量表元数据、用户测评数据
        ScaleUserRecord record = loadScaleRecord(scaleRecordId, userId);
        Scale scale = loadScale(record.getScaleId());
        ScaleVersion version = loadScaleVersion(record.getScaleVersionId());
        List<ScaleDimension> dimensions = loadDimensions(record.getScaleVersionId());
        List<ScaleUserDimScore> dimScores = loadDimScores(record.getId());
        List<ScaleUserAnswer> answers = loadAnswers(record.getId());
        Map<Long, List<ScaleUserAnswerOption>> optionsByAnswer = loadAnswerOptions(answers);
        Map<Long, ScaleUserAnswerText> textByAnswer = loadAnswerTexts(answers);
        Map<Long, String> dimensionNameByQuestion = loadQuestionDimensionMap(record.getScaleVersionId(), dimensions);

        // 2. 构建完整提示词
        String prompt = buildPrompt(record, scale, version, dimensions, dimScores,
                answers, optionsByAnswer, textByAnswer, dimensionNameByQuestion);
        log.debug("[量表分析节点] 构建提示词完成，会话ID：{}，提示词长度：{}", conversationId, prompt.length());

        // 3. 调用模型
        AiNodeConfig aiNodeConfig = aiNodeConfigManager.getConfigWithLoad(NODE_NAME);
        ChatModel chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));

        ScaleToolResult result;
        try {
            ConversationMetadata metadata = ConversationMetadata.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .build();
            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .addMetadata(ConversationMetadata.NAME, metadata)
                    .build();
            result = scaleAnalysisModel.callForResult(chatModel, prompt, aiNodeConfig, runnableConfig);
            log.debug("[量表分析节点] 模型调用完成，会话ID：{}，分析文本长度：{}",
                    conversationId, result.getAnalysisText() != null ? result.getAnalysisText().length() : 0);
        } catch (GraphRunnerException e) {
            log.error("[量表分析节点] 模型调用失败，会话ID：{}，测评记录ID：{}", conversationId, scaleRecordId, e);
            throw new BusinessException(AIChatExceptionEnum.LLM_CALL_FAILED);
        }

        log.info("[量表分析节点] 量表分析完成，会话ID：{}，测评记录ID：{}", conversationId, scaleRecordId);
        return result;
    }

    // ==================== 查询方法 ====================

    /**
     * 查询并校验测评记录：存在、归属当前用户、已完成提交
     *
     * @param recordId 测评记录ID
     * @param userId   用户ID
     * @return 测评记录
     */
    private ScaleUserRecord loadScaleRecord(Long recordId, Long userId) {
        ScaleUserRecord record = scaleUserRecordMapper.selectById(recordId);
        if (record == null || record.getDeleted() != null && record.getDeleted() == DeleteConstant.DELETE_FLAG_YES) {
            log.error("[量表分析节点] 测评记录不存在或已删除，recordId={}", recordId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_FOUND);
        }
        if (record.getUserId() == null || !record.getUserId().equals(userId)) {
            log.error("[量表分析节点] 测评记录不属于当前用户，recordId={}，userId={}", recordId, userId);
            throw new BusinessException(ScaleExceptionEnum.SCALE_RECORD_NOT_OWNED);
        }
        if (!record.isFinished()) {
            log.error("[量表分析节点] 测评尚未完成提交，recordId={}，finishStatus={}", recordId, record.getFinishStatus());
            throw new BusinessException(ScaleExceptionEnum.SCALE_TOOL_RECORD_UNFINISHED);
        }
        return record;
    }

    /**
     * 查询量表主表元数据
     *
     * @param scaleId 量表ID
     * @return 量表实体
     */
    private Scale loadScale(Long scaleId) {
        Scale scale = scaleId == null ? null : scaleMapper.selectById(scaleId);
        if (scale == null || scale.deleteFlat()) {
            log.warn("[量表分析节点] 量表不存在或已删除，scaleId={}", scaleId);
            return null;
        }
        return scale;
    }

    /**
     * 查询量表版本信息
     *
     * @param scaleVersionId 量表版本ID
     * @return 版本实体
     */
    private ScaleVersion loadScaleVersion(Long scaleVersionId) {
        ScaleVersion version = scaleVersionId == null ? null : scaleVersionMapper.selectById(scaleVersionId);
        if (version == null || version.deleteFlat()) {
            log.warn("[量表分析节点] 量表版本不存在或已删除，scaleVersionId={}", scaleVersionId);
            return null;
        }
        return version;
    }

    /**
     * 查询量表全部维度元数据
     *
     * @param scaleVersionId 量表版本ID
     * @return 维度列表
     */
    private List<ScaleDimension> loadDimensions(Long scaleVersionId) {
        if (scaleVersionId == null) {
            return List.of();
        }
        return scaleDimensionMapper.selectList(new LambdaQueryWrapper<ScaleDimension>()
                .eq(ScaleDimension::getScaleVersionId, scaleVersionId)
                .eq(ScaleDimension::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByAsc(ScaleDimension::getSort));
    }

    /**
     * 查询测评记录的各维度得分
     *
     * @param recordId 测评记录ID
     * @return 维度得分列表
     */
    private List<ScaleUserDimScore> loadDimScores(Long recordId) {
        return scaleUserDimScoreMapper.selectList(new LambdaQueryWrapper<ScaleUserDimScore>()
                .eq(ScaleUserDimScore::getRecordId, recordId)
                .eq(ScaleUserDimScore::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByAsc(ScaleUserDimScore::getCreatedTime));
    }

    /**
     * 查询测评记录的逐题作答明细
     *
     * @param recordId 测评记录ID
     * @return 答题明细列表
     */
    private List<ScaleUserAnswer> loadAnswers(Long recordId) {
        return scaleUserAnswerMapper.selectList(new LambdaQueryWrapper<ScaleUserAnswer>()
                .eq(ScaleUserAnswer::getRecordId, recordId)
                .eq(ScaleUserAnswer::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                .orderByAsc(ScaleUserAnswer::getId));
    }

    /**
     * 按答题ID批量查询用户选中选项
     *
     * @param answers 答题明细
     * @return answerId -> 选中选项列表
     */
    private Map<Long, List<ScaleUserAnswerOption>> loadAnswerOptions(List<ScaleUserAnswer> answers) {
        if (CollUtil.isEmpty(answers)) {
            return Map.of();
        }
        List<Long> answerIds = answers.stream().map(ScaleUserAnswer::getId).toList();
        List<ScaleUserAnswerOption> options = scaleUserAnswerOptionMapper.selectList(
                new LambdaQueryWrapper<ScaleUserAnswerOption>()
                        .in(ScaleUserAnswerOption::getAnswerId, answerIds)
                        .eq(ScaleUserAnswerOption::getDeleted, DeleteConstant.DELETE_FLAG_NO)
                        .orderByAsc(ScaleUserAnswerOption::getId));
        if (CollUtil.isEmpty(options)) {
            return Map.of();
        }
        return options.stream().collect(Collectors.groupingBy(ScaleUserAnswerOption::getAnswerId));
    }

    /**
     * 按答题ID批量查询用户填空文本
     *
     * @param answers 答题明细
     * @return answerId -> 填空文本
     */
    private Map<Long, ScaleUserAnswerText> loadAnswerTexts(List<ScaleUserAnswer> answers) {
        if (CollUtil.isEmpty(answers)) {
            return Map.of();
        }
        List<Long> answerIds = answers.stream().map(ScaleUserAnswer::getId).toList();
        List<ScaleUserAnswerText> texts = scaleUserAnswerTextMapper.selectList(
                new LambdaQueryWrapper<ScaleUserAnswerText>()
                        .in(ScaleUserAnswerText::getAnswerId, answerIds)
                        .eq(ScaleUserAnswerText::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        if (CollUtil.isEmpty(texts)) {
            return Map.of();
        }
        return texts.stream().collect(Collectors.toMap(ScaleUserAnswerText::getAnswerId,
                Function.identity(), (a, b) -> a));
    }

    /**
     * 构建 题目ID -> 维度名称 映射，便于逐题作答明细标注所属维度
     *
     * @param scaleVersionId 量表版本ID
     * @param dimensions     维度列表
     * @return questionId -> 维度名称
     */
    private Map<Long, String> loadQuestionDimensionMap(Long scaleVersionId, List<ScaleDimension> dimensions) {
        if (scaleVersionId == null || CollUtil.isEmpty(dimensions)) {
            return Map.of();
        }
        Map<Long, String> nameByDimensionId = dimensions.stream()
                .filter(dim -> dim.getId() != null && StrUtil.isNotBlank(dim.getDimName()))
                .collect(Collectors.toMap(ScaleDimension::getId, ScaleDimension::getDimName, (a, b) -> a));

        List<ScaleQuestion> questions = scaleQuestionMapper.selectList(new LambdaQueryWrapper<ScaleQuestion>()
                .eq(ScaleQuestion::getScaleVersionId, scaleVersionId)
                .eq(ScaleQuestion::getDeleted, DeleteConstant.DELETE_FLAG_NO));
        if (CollUtil.isEmpty(questions)) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (ScaleQuestion question : questions) {
            if (question.getDimensionId() != null && nameByDimensionId.containsKey(question.getDimensionId())) {
                result.put(question.getId(), nameByDimensionId.get(question.getDimensionId()));
            }
        }
        return result;
    }

    // ==================== 提示词构建 ====================

    /**
     * 构建完整的量表测评分析提示词
     * <p>仅包含量表测评相关的数据信息：量表元数据、测评结果摘要、各维度得分、逐题作答明细。</p>
     *
     * @return 完整提示词
     */
    private String buildPrompt(ScaleUserRecord record, Scale scale, ScaleVersion version,
                               List<ScaleDimension> dimensions, List<ScaleUserDimScore> dimScores,
                               List<ScaleUserAnswer> answers, Map<Long, List<ScaleUserAnswerOption>> optionsByAnswer,
                               Map<Long, ScaleUserAnswerText> textByAnswer,
                               Map<Long, String> dimensionNameByQuestion) {
        StringBuilder sb = new StringBuilder();

        // 1. 量表元数据
        sb.append("【量表信息】\n");
        String scaleName = scale != null ? scale.getScaleName() : StrUtil.nullToEmpty(record.getScaleName());
        sb.append("- 量表名称：").append(scaleName).append("\n");
        if (version != null) {
            if (StrUtil.isNotBlank(version.getVersionNo())) {
                sb.append("- 版本号：").append(version.getVersionNo()).append("\n");
            }
            if (StrUtil.isNotBlank(version.getDescription())) {
                sb.append("- 量表说明：").append(version.getDescription()).append("\n");
            }
        }
        sb.append("\n");

        // 2. 维度说明
        if (CollUtil.isNotEmpty(dimensions)) {
            sb.append("【量表维度说明】\n");
            for (ScaleDimension dim : dimensions) {
                sb.append("- ").append(StrUtil.nullToEmpty(dim.getDimName()));
                if (StrUtil.isNotBlank(dim.getDimDesc())) {
                    sb.append("：").append(dim.getDimDesc());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 3. 测评结果摘要
        sb.append("【测评结果摘要】\n");
        sb.append("- 原始总分：").append(fmt(record.getTotalScore())).append("\n");
        if (record.getStandardScore() != null) {
            sb.append("- 标准分：").append(record.getStandardScore()).append("\n");
        }
        if (record.getPercentile() != null) {
            sb.append("- 百分等级：").append(record.getPercentile()).append("%\n");
        }
        if (StrUtil.isNotBlank(record.getResultText())) {
            sb.append("- 结果描述：").append(record.getResultText()).append("\n");
        }
        if (record.getRiskLevel() != null) {
            sb.append("- 风险等级：").append(riskLevelText(record.getRiskLevel())).append("\n");
        }
        sb.append("\n");

        // 4. 各维度得分
        if (CollUtil.isNotEmpty(dimScores)) {
            sb.append("【各维度得分】\n");
            Map<Long, String> dimNameById = dimensions.stream()
                    .collect(Collectors.toMap(ScaleDimension::getId, ScaleDimension::getDimName, (a, b) -> a));
            for (ScaleUserDimScore dimScore : dimScores) {
                sb.append("- ").append(dimNameById.getOrDefault(dimScore.getDimensionId(), "维度" + dimScore.getDimensionId()))
                        .append("：得分 ").append(fmt(dimScore.getDimScore()));
                if (StrUtil.isNotBlank(dimScore.getDimResult())) {
                    sb.append("，解读：").append(dimScore.getDimResult());
                }
                if (dimScore.getRiskLevel() != null) {
                    sb.append("，风险等级：").append(riskLevelText(dimScore.getRiskLevel()));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 5. 逐题作答明细
        if (CollUtil.isNotEmpty(answers)) {
            sb.append("【逐题作答明细】\n");
            int index = 1;
            for (ScaleUserAnswer answer : answers) {
                sb.append(index).append(". ").append(StrUtil.nullToEmpty(answer.getQuestionTitle())).append("\n");
                String dimName = answer.getQuestionId() == null ? null : dimensionNameByQuestion.get(answer.getQuestionId());
                if (StrUtil.isNotBlank(dimName)) {
                    sb.append("   所属维度：").append(dimName).append("\n");
                }
                sb.append("   用户作答：").append(buildAnswerText(answer, optionsByAnswer, textByAnswer)).append("\n");
                index++;
            }
            sb.append("\n");
        }

        log.debug("[量表分析节点] 提示词构建完成，量表：{}", scaleName);
        return sb.toString();
    }

    /**
     * 组装单题答案文本：优先取选中选项，其次取填空文本，均无则为未作答
     *
     * @param answer          答题明细
     * @param optionsByAnswer answerId -> 选中选项
     * @param textByAnswer    answerId -> 填空文本
     * @return 单题答案文本
     */
    private String buildAnswerText(ScaleUserAnswer answer,
                                   Map<Long, List<ScaleUserAnswerOption>> optionsByAnswer,
                                   Map<Long, ScaleUserAnswerText> textByAnswer) {
        List<ScaleUserAnswerOption> options = optionsByAnswer.getOrDefault(answer.getId(), List.of());
        String optionText = options.stream()
                .map(ScaleUserAnswerOption::getOptionText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("、"));
        if (StrUtil.isNotBlank(optionText)) {
            return optionText;
        }
        ScaleUserAnswerText text = textByAnswer.get(answer.getId());
        if (text != null && StrUtil.isNotBlank(text.getAnswerText())) {
            return text.getAnswerText();
        }
        return "未作答";
    }

    /**
     * 数字格式化：null 转换为 "-"
     *
     * @param value 数值
     * @return 展示文本
     */
    private String fmt(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 风险等级转展示文本
     *
     * @param riskLevel 0-无 1-低 2-中 3-高
     * @return 展示文本
     */
    private String riskLevelText(Integer riskLevel) {
        return switch (riskLevel) {
            case 1 -> "低";
            case 2 -> "中";
            case 3 -> "高";
            default -> "无";
        };
    }

}