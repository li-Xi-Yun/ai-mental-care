package org.lixiyun.server.ai.node.diagnosis;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.bo.conversation.ConversationProcessContextBO;
import org.lixiyun.pojo.bo.conversation.state.GraphState;
import org.lixiyun.pojo.bo.conversation.state.InputGraphState;
import org.lixiyun.pojo.bo.conversation.state.KnowledgeGraphState;
import org.lixiyun.pojo.bo.conversation.state.ProcessGraphState;
import org.lixiyun.pojo.entity.conversation.ConversationMemory;
import org.lixiyun.pojo.entity.conversation.EmotionAnalysis;
import org.lixiyun.pojo.entity.conversation.EmotionDiagnosis;
import org.lixiyun.server.ai.message.enums.MessageType;
import org.lixiyun.server.ai.model.diagnosis.IntentRecognitionModel;
import org.lixiyun.server.ai.model.factory.ChatModelFactory;
import org.lixiyun.server.ai.model.factory.ChatModelType;
import org.lixiyun.server.ai.node.NodeExecutionSummary;
import org.lixiyun.server.infrastructure.ai.AiNodeConfigManager;
import org.lixiyun.server.mapper.EmotionDiagnosisMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 诊断图意图识别节点
 *
 * <p>作为诊断图 START 之后的第一个节点，判断本轮对话是否需要触发诊断流程。</p>
 *
 * <p>四步决策流程：</p>
 * <ol>
 *   <li><b>Step 0 - 危机关键词处理</b>：检测当前轮是否包含危机关键词，有近期诊断则升级风险等级</li>
 *   <li><b>Step 1 - 动态轮次门控</b>：基于历史风险等级和情绪趋势判断是否达到诊断间隔</li>
 *   <li><b>Step 2 - 片段质量 + 触发信号分析</b>：三维度（语义饱和度、求助/反思信号、情绪状态变化）综合判断</li>
 *   <li><b>Step 3 - LLM 兜底判断</b>：Step 2 无法明确判断时，调用轻量模型做最终决策</li>
 * </ol>
 *
 * <p>核心原则：危机关键词不主动触发诊断。它要么升级已有诊断的风险等级，要么不做处理，
 * 让后续的轮次门控在合适时机自然触发诊断。</p>
 *
 * @author lixiyun
 * @since 2026-09-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRecognitionNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "intentRecognitionNode";

    // ==================== 阈值常量 ====================

    /** 基础诊断间隔轮次 */
    private static final int BASE_THRESHOLD = 5;
    /** 最大诊断间隔轮次（所有用户的上限） */
    private static final int MAX_INTERVAL = 20;
    /** 首次诊断前的最小会话轮次 */
    private static final int FIRST_DX_MIN_ROUND = 5;
    /** 情绪强度变化阈值（0~1），超过此值视为显著变化 */
    private static final double EMOTION_DELTA_THRESHOLD = 0.3;

    // ==================== 危机关键词模式（Step 0） ====================

    private static final List<Pattern> CRISIS_PATTERNS = List.of(
            Pattern.compile(".*(自杀|不想活|死了算了|结束生命|活不下去|解脱|想死|轻生|寻死|活够了).*"),
            Pattern.compile(".*(割腕|自残|伤害自己|自伤|划伤自己|掐自己|撞墙|跳楼|跳河).*"),
            Pattern.compile(".*(想杀人|同归于尽|报复社会|杀人).*")
    );

    // ==================== 求助/反思信号模式（Step 2b） ====================

    private static final List<Pattern> HELP_SEEKING_PATTERNS = List.of(
            Pattern.compile(".*(怎[么样]办|帮[帮我]|帮帮忙|能不能分析|你觉得|分析一下|评价|诊断).*"),
            Pattern.compile(".*(我是不是|为什么会这样|怎么改变|正常吗|有问题|是不是有|太).*"),
            Pattern.compile(".*(就是这样|说完了|大概就这样|差不多|差不多就这些).*")
    );

    // ==================== 纯应答/寒暄排除模式 ====================

    private static final List<Pattern> FILLER_PATTERNS = List.of(
            Pattern.compile("^[嗯哦啊诶嗨嘿嗯]+$"),
            Pattern.compile("^(好的|知道了|收到|了解|明白|行|可以|ok|OK|Ok|谢谢|多谢|感谢|你好|hi|Hello|hello|在吗|在不在)$")
    );

    /** MyBatis Mapper，用于更新诊断记录的风险等级（危机关键词触发时） */
    private final EmotionDiagnosisMapper emotionDiagnosisMapper;
    /** 节点配置管理器，加载模型参数（modelType、temperature、maxToken 等） */
    private final AiNodeConfigManager aiNodeConfigManager;
    /** 模型工厂，根据配置的 {@link ChatModelType} 创建对应的 ChatModel 实例 */
    private final ChatModelFactory chatModelFactory;
    /** 意图识别轻量模型，封装 LLM 调用与系统提示词 */
    private final IntentRecognitionModel intentRecognitionModel;

    // ==================== NodeActionWithConfig ====================

    /**
     * 意图识别主入口，串联四步决策流程。
     *
     * <p>任一步骤判定"无需诊断"时，通过返回值写入{@link GraphState}中断标志，
     * 由{@code DiagnosisGraph.createInterruptConditionEdge()} 读取并路由至 END。</p>
     *
     * @param state  图状态，包含 {@link ConversationProcessContextBO}
     * @param config 运行配置
     * @return 包含 GraphState 的 Map（中断时）或空 Map（继续流程时）
     * @throws BusinessException 会话上下文缺失时抛出
     */
    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("[意图识别] 开始");

        Optional<ConversationProcessContextBO> ctxOpt = state.value(ConversationProcessContextBO.NAME);
        if (ctxOpt.isEmpty()) {
            log.error("[意图识别] 会话上下文为空");
            throw new BusinessException(ConversationExceptionEnum.CONVERSATION_PROCESS_CONTEXT_NOT_EXIST);
        }

        ConversationProcessContextBO ctx = ctxOpt.get();
        int currentRound = ctx.getConversation().getCurrentRound();
        EmotionDiagnosis lastDx = ctx.getEmotionDiagnosis();
        Integer lastDxRound = (lastDx != null) ? lastDx.getRoundNum() : null;
        List<EmotionAnalysis> analyses = ctx.getEmotionAnalyses();

        String currentUserInput = extractCurrentUserInput(ctx);

        log.debug("[意图识别] 输入数据: currentRound={}, lastDxRound={}, analysesCount={}, userInput={}",
                currentRound, lastDxRound, analyses != null ? analyses.size() : 0, currentUserInput);

        // ===== Step 0: 危机关键词处理 =====
        int threshold = computeDynamicThreshold(lastDx, analyses);
        log.debug("[意图识别] 动态阈值计算结果: threshold={}", threshold);
        if (handleCrisisKeywords(currentUserInput, lastDx, currentRound, threshold)) {
            return buildInterruptedState("意图识别-Step0-危机关键词命中");
        }

        // ===== Step 1: 动态轮次门控 =====
        if (!passRoundGate(currentRound, lastDxRound, lastDx, analyses)) {
            return buildInterruptedState("意图识别-Step1-轮次门控未通过");
        }

        // ===== Step 2: 片段质量 + 触发信号分析 =====
        boolean hasHelpSignal = hasHelpSignal(currentUserInput);
        boolean saturated = isSemanticSaturated(ctx, lastDxRound);
        boolean hasDelta = hasEmotionDelta(analyses, lastDxRound);

        log.debug("[意图识别] Step2三维度: saturated={}, hasHelpSignal={}, hasDelta={}",
                saturated, hasHelpSignal, hasDelta);

        if (saturated && hasHelpSignal) {
            log.info("[意图识别] Step2-语义饱和+求助信号 → process");
            return Map.of();
        }
        if (saturated && hasDelta) {
            log.info("[意图识别] Step2-语义饱和+情绪变化 → process");
            return Map.of();
        }
        if (!saturated && hasHelpSignal) {
            log.info("[意图识别] Step2-求助信号(即使不饱和) → process");
            return Map.of();
        }
        if (!saturated && !hasDelta) {
            return buildInterruptedState("意图识别-Step2-不饱和且无情绪变化");
        }

        // 剩余：饱和 + 无求助 + 无情绪变化 → 模糊 → Step 3
        log.info("[意图识别] Step2-模糊，进入Step3 LLM兜底判断");
        if (!llmIntentCheck(currentUserInput, ctx, lastDxRound, analyses, lastDx, config)) {
            return buildInterruptedState("意图识别-Step3-LLM兜底判定无需诊断");
        }
        return Map.of();
    }

    /**
     * 构建中断状态的 GraphState
     *
     * @param reason 中断原因
     * @return 包含中断 GraphState 的 Map
     */
    private Map<String, Object> buildInterruptedState(String reason) {
        GraphState graphState = GraphState.builder()
                .inputGraphState(InputGraphState.builder().interrupted(true).interruptReason(reason).build())
                .knowledgeGraphState(new KnowledgeGraphState())
                .processGraphState(new ProcessGraphState())
                .build();
        return Map.of(GraphState.NAME, graphState);
    }

    // ==================== Step 0: 危机关键词处理 ====================

    /**
     * 检测当前轮输入是否包含危机关键词，若近期有诊断记录则升级风险等级。
     *
     * <p>三组危机 Pattern：自杀信号 / 自伤信号 / 暴力信号。
     * 命中后不触发新的诊断，仅对已有诊断记录做风险升级。</p>
     *
     * @param currentUserInput 当前轮用户原始输入
     * @param lastDx           最近一次诊断记录（可能为 null）
     * @param currentRound     当前会话轮次
     * @param threshold        动态诊断间隔阈值
     * @return true 表示命中了危机关键词（已做处理），调用方应中断后续流程
     */
    private boolean handleCrisisKeywords(String currentUserInput, EmotionDiagnosis lastDx,
                                         int currentRound, int threshold) {
        if (currentUserInput == null || currentUserInput.isEmpty()) {
            return false;
        }
        if (!matchesAnyPattern(CRISIS_PATTERNS, currentUserInput)) {
            return false;
        }

        log.warn("[意图识别-Step0] 危机关键词命中，input={}", currentUserInput);

        if (lastDx != null && lastDx.getRoundNum() != null
                && (currentRound - lastDx.getRoundNum()) < threshold) {
            log.warn("[意图识别-Step0] 有近期诊断记录({}轮前)，升级风险等级，conversationId={}",
                    currentRound - lastDx.getRoundNum(), lastDx.getConversationId());
            lastDx.setEmotionRiskLevel(EmotionDiagnosis.EMOTION_RISK_CRITICAL);
            lastDx.setSelfHarmRiskLevel(EmotionDiagnosis.SELF_HARM_RISK_VERY_HIGH);
            lastDx.setSuicideRiskLevel(EmotionDiagnosis.SUICIDE_RISK_VERY_HIGH);
            lastDx.setCrisisWarning(EmotionDiagnosis.CRISIS_WARNING_YES);
            emotionDiagnosisMapper.updateById(lastDx);
        } else {
            log.info("[意图识别-Step0] 无近期诊断记录，跳过，等待自然触发诊断");
        }

        return true;
    }

    // ==================== Step 1: 动态轮次门控 ====================

    /**
     * 判断是否达到触发诊断的轮次间隔。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>首次诊断（lastDx 为 null）需满足 {@link #FIRST_DX_MIN_ROUND} 轮</li>
     *   <li>超过 {@link #MAX_INTERVAL} 轮强制触发</li>
     *   <li>达到动态阈值（{@link #computeDynamicThreshold}）时触发</li>
     *   <li>检测到情绪标签话题切换时触发</li>
     * </ul>
     *
     * @param currentRound    当前会话轮次
     * @param lastDxRound     最近诊断时的轮次（首次为 null）
     * @param lastDx          最近诊断记录
     * @param emotionAnalyses 情绪分析列表
     * @return true 表示通过门控，可继续后续判断
     */
    private boolean passRoundGate(int currentRound, Integer lastDxRound,
                                  EmotionDiagnosis lastDx,
                                  List<EmotionAnalysis> emotionAnalyses) {
        if (lastDx == null || lastDxRound == null) {
            boolean pass = currentRound >= FIRST_DX_MIN_ROUND;
            log.info("[意图识别-Step1] 首次诊断，currentRound={}, 门控结果={}", currentRound, pass);
            return pass;
        }

        int roundDiff = currentRound - lastDxRound;
        log.debug("[意图识别-Step1] roundDiff={}, maxInterval={}", roundDiff, MAX_INTERVAL);

        if (roundDiff >= MAX_INTERVAL) {
            log.info("[意图识别-Step1] 超过最大间隔({}轮)，强制触发诊断", MAX_INTERVAL);
            return true;
        }

        int threshold = computeDynamicThreshold(lastDx, emotionAnalyses);
        log.debug("[意图识别-Step1] 动态阈值={}, roundDiff={}", threshold, roundDiff);
        if (roundDiff >= threshold) {
            log.info("[意图识别-Step1] roundDiff={} >= threshold={}，通过门控", roundDiff, threshold);
            return true;
        }

        if (roundDiff > 0 && hasTopicSwitch(emotionAnalyses, lastDxRound)) {
            log.info("[意图识别-Step1] 检测到话题切换，通过门控");
            return true;
        }

        log.info("[意图识别-Step1] roundDiff={}, threshold={}, 未通过门控", roundDiff, threshold);
        return false;
    }

    /**
     * 根据历史诊断风险等级与近期情绪趋势，动态计算诊断轮次间隔阈值。
     *
     * <p>基准阈值 {@link #BASE_THRESHOLD}（5轮），按以下规则调整：</p>
     * <ul>
     *   <li>高风险/危急 → -3</li>
     *   <li>中风险 → -1</li>
     *   <li>近期趋势恶化 → -1</li>
     * </ul>
     * <p>下限为 1 轮。</p>
     *
     * @param lastDx   最近诊断记录
     * @param analyses 情绪分析列表
     * @return 动态阈值（1 ~ 5）
     */
    private int computeDynamicThreshold(EmotionDiagnosis lastDx,
                                        List<EmotionAnalysis> analyses) {
        int threshold = BASE_THRESHOLD;
        int riskAdjustment = 0;
        int trendAdjustment = 0;

        if (lastDx != null && lastDx.getEmotionRiskLevel() != null) {
            switch (lastDx.getEmotionRiskLevel()) {
                case EmotionDiagnosis.EMOTION_RISK_HIGH:
                case EmotionDiagnosis.EMOTION_RISK_CRITICAL:
                    riskAdjustment = -3;
                    threshold -= 3;
                    break;
                case EmotionDiagnosis.EMOTION_RISK_MEDIUM:
                    riskAdjustment = -1;
                    threshold -= 1;
                    break;
                default:
                    break;
            }
        }

        EmotionAnalysis latest = getLatestAnalysis(analyses);
        if (latest != null && isWorseningTrend(latest.getEmotionTrend())) {
            trendAdjustment = -1;
            threshold -= 1;
        }

        int finalThreshold = Math.max(threshold, 1);
        log.debug("[意图识别] computeDynamicThreshold: base={}, riskAdjust={}, trendAdjust={}, final={}",
                BASE_THRESHOLD, riskAdjustment, trendAdjustment, finalThreshold);
        return finalThreshold;
    }

    /**
     * 检测自上次诊断以来，相近两轮的情绪标签是否发生了变化（话题切换信号）。
     *
     * @param analyses    情绪分析列表
     * @param lastDxRound 上次诊断轮次
     * @return true 表示标签发生了变化
     */
    private boolean hasTopicSwitch(List<EmotionAnalysis> analyses, Integer lastDxRound) {
        if (analyses == null || analyses.size() < 2) {
            return false;
        }
        List<EmotionAnalysis> recent = analyses.stream()
                .filter(a -> a.getRoundNum() != null && a.getRoundNum() > lastDxRound)
                .toList();
        if (recent.size() < 2) {
            return false;
        }
        EmotionAnalysis prev = recent.get(recent.size() - 2);
        EmotionAnalysis curr = recent.get(recent.size() - 1);
        if (prev.getEmotionLabel() == null || curr.getEmotionLabel() == null) {
            return false;
        }
        return !prev.getEmotionLabel().equals(curr.getEmotionLabel());
    }

    // ==================== Step 2: 片段质量 + 触发信号分析 ====================

    /**
     * 分析上次诊断至今的用户消息，判断语义是否足够饱和以支撑一次有意义的诊断。
     *
     * <p>排除纯语气词/寒暄，计算有效消息占比。满足任一条件即视为饱和：</p>
     * <ul>
     *   <li>有效消息占比 &gt; 60%</li>
     *   <li>有效消息数 ≥ 3 条</li>
     * </ul>
     *
     * @param ctx         会话上下文
     * @param lastDxRound 上次诊断轮次（null 表示首次）
     * @return true 表示语义饱和
     */
    private boolean isSemanticSaturated(ConversationProcessContextBO ctx, Integer lastDxRound) {
        List<ConversationMemory> history = ctx.getConversationHistory();
        if (history == null || history.isEmpty()) {
            log.debug("[意图识别-Step2a] 语义饱和度: history为空 → 不饱和");
            return false;
        }

        String userType = MessageType.USER.getName();
        List<ConversationMemory> userMessages = history.stream()
                .filter(m -> userType.equals(m.getType()))
                .filter(m -> lastDxRound == null || m.getRoundNum() > lastDxRound)
                .toList();

        if (userMessages.isEmpty()) {
            log.debug("[意图识别-Step2a] 语义饱和度: 区间内无用户消息 → 不饱和");
            return false;
        }

        long validCount = userMessages.stream()
                .filter(m -> m.getContent() != null && !isEmptyResponse(m.getContent()))
                .count();

        double ratio = (double) validCount / userMessages.size();
        boolean saturated = ratio > 0.6 || validCount >= 3;
        log.debug("[意图识别-Step2a] 语义饱和度: totalUserMsg={}, validCount={}, ratio={}, saturated={}",
                userMessages.size(), validCount, String.format("%.2f", ratio), saturated);
        return saturated;
    }

    /**
     * 检测当前用户输入中是否包含求助、反思或事件结束信号。
     *
     * @param currentUserInput 当前轮用户输入
     * @return true 表示命中求助/反思/结束信号
     */
    private boolean hasHelpSignal(String currentUserInput) {
        if (currentUserInput == null || currentUserInput.isEmpty()) {
            return false;
        }
        boolean matched = matchesAnyPattern(HELP_SEEKING_PATTERNS, currentUserInput);
        log.debug("[意图识别-Step2b] 求助信号: matched={}", matched);
        return matched;
    }

    /**
     * 检测上次诊断轮次至今的情绪状态是否发生显著变化。
     *
     * <p>三个维度：</p>
     * <ul>
     *   <li>情绪强度变化 &gt; {@link #EMOTION_DELTA_THRESHOLD}</li>
     *   <li>情绪标签前后不同</li>
     *   <li>情绪趋势恶化</li>
     * </ul>
     *
     * @param analyses    情绪分析列表
     * @param lastDxRound 上次诊断轮次
     * @return true 表示情绪状态发生了值得关注的变化
     */
    private boolean hasEmotionDelta(List<EmotionAnalysis> analyses, Integer lastDxRound) {
        if (analyses == null || analyses.isEmpty()) {
            return false;
        }

        EmotionAnalysis lastDxEmotion = getEmotionAtOrBeforeRound(analyses, lastDxRound);
        EmotionAnalysis current = getLatestAnalysis(analyses);

        if (lastDxEmotion == null || current == null) {
            log.debug("[意图识别-Step2c] 情绪变化量: 缺少基准或当前分析数据");
            return false;
        }

        if (current.getEmotionIntensity() != null && lastDxEmotion.getEmotionIntensity() != null) {
            double delta = Math.abs(current.getEmotionIntensity() - lastDxEmotion.getEmotionIntensity());
            log.debug("[意图识别-Step2c] 情绪变化量: lastIntensity={}, currentIntensity={}, delta={}, threshold={}",
                    lastDxEmotion.getEmotionIntensity(), current.getEmotionIntensity(), delta, EMOTION_DELTA_THRESHOLD);
            if (delta > EMOTION_DELTA_THRESHOLD) {
                return true;
            }
        }

        boolean labelChanged = current.getEmotionLabel() != null
                && !current.getEmotionLabel().equals(lastDxEmotion.getEmotionLabel());
        log.debug("[意图识别-Step2c] 情绪变化量: lastLabel={}, currentLabel={}, labelChanged={}",
                lastDxEmotion.getEmotionLabel(), current.getEmotionLabel(), labelChanged);
        if (labelChanged) {
            return true;
        }

        boolean worsening = isWorseningTrend(current.getEmotionTrend());
        log.debug("[意图识别-Step2c] 情绪变化量: trend={}, worsening={}", current.getEmotionTrend(), worsening);
        return worsening;
    }

    // ==================== Step 3: LLM 兜底判断 ====================

    /**
     * 当 Step-2 无法明确判断时，调用轻量模型做二分类决策。
     *
     * <p>userPrompt 仅包含动态数据（轮次差、风险等级、情绪序列、当前消息、区间对话片段），
     * 角色定义与输出格式指令由 {@link IntentRecognitionModel} 的 systemPrompt 提供。</p>
     *
     * <p>通过 {@link IntentRecognitionModel#callForResult} 获取
     * {@link IntentRecognitionModel.IntentRecognitionResult}，
     * 再调用 {@code isNeedDiagnosis()} 判断。调用异常时默认返回 true（宁可多诊）。</p>
     *
     * @param currentUserInput 当前轮用户输入
     * @param ctx              会话上下文
     * @param lastDxRound      上次诊断轮次
     * @param analyses         情绪分析列表
     * @param lastDx           最近诊断记录
     * @param runnableConfig   图运行时信息
     * @return true 表示需要触发诊断
     */
    private boolean llmIntentCheck(String currentUserInput, ConversationProcessContextBO ctx,
                                    Integer lastDxRound,
                                    List<EmotionAnalysis> analyses,
                                    EmotionDiagnosis lastDx,
                                   RunnableConfig runnableConfig) {
        int currentRound = ctx.getConversation().getCurrentRound();
        int roundsSinceLastDx = (lastDx == null || lastDx.getRoundNum() == null)
                ? 0 : currentRound - lastDx.getRoundNum();

        String riskDesc = (lastDx != null && lastDx.getEmotionRiskLevel() != null)
                ? lastDx.getRiskLevelDesc() : "无";

        String recentLabels = buildRecentLabels(analyses);
        String segmentText = buildSegmentText(ctx, lastDxRound);

        String userPrompt = String.format("""
                距上次诊断已过 %d 轮
                上次诊断风险等级: %s
                用户近期情绪标签序列: [%s]

                当前用户消息: "%s"

                区间对话片段:
                %s""", roundsSinceLastDx, riskDesc, recentLabels, currentUserInput, segmentText);

        log.debug("[意图识别-Step3] LLM userPrompt: {}", userPrompt);

        var aiNodeConfig = aiNodeConfigManager.getConfigWithLoad(NODE_NAME);
        var chatModel = chatModelFactory.getChatModel(ChatModelType.fromType(aiNodeConfig.getModelType()));

        try {
            var result = intentRecognitionModel.callForResult(chatModel, userPrompt, aiNodeConfig, runnableConfig);
            log.debug("[意图识别-Step3] LLM响应: raw={}, needDiagnosis={}", result.getResponse(), result.isNeedDiagnosis());
            return result.isNeedDiagnosis();
        } catch (Exception e) {
            log.error("[意图识别-Step3] LLM调用异常，默认执行诊断", e);
            return true;
        }
    }

    // ==================== 可复用工具方法（package-private） ====================

    /**
     * 从临时消息（本轮的原始消息对象）中提取 USER 类型的文本内容。
     *
     * @param ctx 会话上下文
     * @return 本轮用户输入文本，多段消息用空格拼接；无消息时返回空串
     */
    String extractCurrentUserInput(ConversationProcessContextBO ctx) {
        if (ctx.getTemporaryMessages() == null || ctx.getTemporaryMessages().isEmpty()) {
            return "";
        }
        String userType = MessageType.USER.getName();
        String input = ctx.getTemporaryMessages().stream()
                .filter(m -> userType.equals(m.getType()))
                .map(ConversationMemory::getContent)
                .filter(content -> content != null && !content.isEmpty())
                .collect(Collectors.joining(" "));
        log.debug("[意图识别] extractCurrentUserInput: {}", input);
        return input;
    }

    /**
     * 构建上次诊断至今的对话片段文本，用于 Step-3 LLM 的 userPrompt。
     *
     * <p>若区间跨越了上下文摘要点，则使用摘要代替被压缩部分；否则拼接区间内的
     * USER / ASSISTANT 消息。</p>
     *
     * @param ctx         会话上下文
     * @param lastDxRound 上次诊断轮次（null 表示全部历史）
     * @return 格式化的对话片段文本
     */
    String buildSegmentText(ConversationProcessContextBO ctx, Integer lastDxRound) {
        StringBuilder sb = new StringBuilder();
        var conv = ctx.getConversation();

        if (lastDxRound != null
                && conv.getContextSummaryRound() != null
                && lastDxRound < conv.getContextSummaryRound()) {
            if (conv.getAnalysisContextSummary() != null && !conv.getAnalysisContextSummary().isEmpty()) {
                sb.append(conv.getAnalysisContextSummary()).append("\n");
            }
        }

        if (ctx.getConversationHistory() != null) {
            String userType = MessageType.USER.getName();
            String assistantType = MessageType.ASSISTANT.getName();
            ctx.getConversationHistory().stream()
                    .filter(m -> lastDxRound == null || m.getRoundNum() > lastDxRound)
                    .filter(m -> userType.equals(m.getType()) || assistantType.equals(m.getType()))
                    .forEach(m -> {
                        if (m.getContent() != null) {
                            sb.append("[").append(m.getType()).append("] ")
                                    .append(m.getContent()).append("\n");
                        }
                    });
        }
        String text = sb.toString();
        log.debug("[意图识别] buildSegmentText: length={}", text.length());
        return text;
    }

    /**
     * 构建最近 3 轮情绪标签序列（最近在前），用于 Step-3 LLM 的 userPrompt。
     *
     * @param analyses 情绪分析列表
     * @return 情绪标签序列字符串，如 "焦虑 → 抑郁 → 焦虑"；无数据时返回 "无"
     */
    String buildRecentLabels(List<EmotionAnalysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return "无";
        }
        String labels = analyses.stream()
                .filter(a -> a.getEmotionLabel() != null)
                .sorted((a, b) -> Integer.compare(
                        b.getRoundNum() != null ? b.getRoundNum() : 0,
                        a.getRoundNum() != null ? a.getRoundNum() : 0))
                .limit(3)
                .map(EmotionAnalysis::getEmotionLabel)
                .collect(Collectors.joining(" → "));
        log.debug("[意图识别] buildRecentLabels: {}", labels);
        return labels;
    }

    /**
     * 获取情绪分析列表中轮次最新的一条。
     *
     * @param analyses 情绪分析列表
     * @return 最新分析记录；列表为空时返回 null
     */
    EmotionAnalysis getLatestAnalysis(List<EmotionAnalysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return null;
        }
        return analyses.stream()
                .filter(a -> a.getRoundNum() != null)
                .max(Comparator.comparingInt(EmotionAnalysis::getRoundNum))
                .orElse(null);
    }

    /**
     * 获取指定轮次及之前最新的一条情绪分析记录。
     *
     * @param analyses    情绪分析列表
     * @param targetRound 目标轮次（可为 null，此时返回 null）
     * @return 目标轮次及之前的最新记录；无匹配时返回 null
     */
    EmotionAnalysis getEmotionAtOrBeforeRound(List<EmotionAnalysis> analyses, Integer targetRound) {
        if (analyses == null || targetRound == null) {
            return null;
        }
        return analyses.stream()
                .filter(a -> a.getRoundNum() != null && a.getRoundNum() <= targetRound)
                .max((a, b) -> Integer.compare(a.getRoundNum(), b.getRoundNum()))
                .orElse(null);
    }

    /**
     * 判断情绪趋势是否为恶化方向。
     *
     * @param trend 趋势描述文本
     * @return true 表示趋势恶化或下降
     */
    boolean isWorseningTrend(String trend) {
        return "恶化".equals(trend) || "下降".equals(trend);
    }

    /**
     * 判断消息内容是否为纯应答/寒暄（无信息量的回复）。
     *
     * <p>匹配 {@link #FILLER_PATTERNS} 中的语气词和短应答模式。</p>
     *
     * @param content 消息内容
     * @return true 表示该消息可被视为空/无意义
     */
    boolean isEmptyResponse(String content) {
        if (content == null) {
            return true;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        return matchesAnyPattern(FILLER_PATTERNS, trimmed);
    }

    /**
     * 通用正则匹配工具：输入是否命中任意 Pattern。
     *
     * @param patterns Pattern 列表
     * @param input    输入文本
     * @return true 表示命中
     */
    static boolean matchesAnyPattern(List<Pattern> patterns, CharSequence input) {
        return patterns.stream().anyMatch(p -> p.matcher(input).matches());
    }

    // ==================== NodeExecutionSummary ====================

    @Override
    public Object inputSummary(OverAllState state) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Optional<ConversationProcessContextBO> ctxOpt = state.value(ConversationProcessContextBO.NAME);
        ctxOpt.ifPresent(ctx -> {
            summary.put("currentRound", ctx.getConversation().getCurrentRound());
            summary.put("hasDiagnosis", ctx.getEmotionDiagnosis() != null);
            summary.put("hasAnalyses", ctx.getEmotionAnalyses() != null
                    && !ctx.getEmotionAnalyses().isEmpty());
        });
        return summary.isEmpty() ? null : summary;
    }

    @Override
    public Object outputSummary(OverAllState state) {
        return null;
    }
}