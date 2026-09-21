# 诊断图意图识别节点设计文档

> 版本: v1.0  
> 作者: lixiyun  
> 日期: 2026-09-18

---

## 一、背景与问题

### 1.1 现状

当前系统在每轮用户消息处理时，都会**无条件**触发完整诊断图流程：

```
ConversationAiService.analysisAndDiagnosis()
  ├── EmotionRecognitionNode（情绪识别）
  └── DiagnosisGraph.executeGraph()（完整诊断图）
        ├── START → InputNode(InputGraph子图) → [中断检查]
        ├── KnowledgeNode(KnowledgeGraph子图)
        ├── ProcessNode(ProcessGraph子图)
        └── DiagnosisPersistNode → END
```

诊断图内部虽有 `DIAGNOSIS_INTERRUPTED` 中断机制，但那是在**已经进入图之后**才做的（基于 PreCleanNode 清洗后判断消息有效性），Graph 的 invoke 开销、checkpoint 创建/删除等成本已经产生。

### 1.2 问题

用户发送"你好"、"好的谢谢"等非心理话题消息时，完整的诊断图（含 LLM 调用、向量检索、多节点处理）仍然会被执行，造成不必要的算力和 Token 浪费。

### 1.3 目标

新增一个**意图识别节点**（IntentRecognitionNode），在诊断图执行**最早阶段**判断本轮对话是否需要触发诊断，不满足条件时直接终止，跳过后续所有诊断步骤。

---

## 二、节点定位

### 2.1 在诊断图中的位置

放于 `DiagnosisGraph` 内部，作为 `START` 后的**第一个节点**，在 `InputNode` 之前：

```
START
  │
  ▼
IntentRecognitionNode  ←─ 新增
  │
  ▼
[条件边: isDiagnosisNeeded?]
  ├── "process" → InputNode → KnowledgeNode → ProcessNode → DiagnosisPersistNode → END
  └── "end"    → END（跳过诊断）
```

### 2.2 选择放在图内部的理由

- 复用现有的条件边中断机制（`DiagnosisGraph.createInterruptConditionEdge()`）
- 诊断逻辑在图内自闭环，不污染上层 `ConversationAiService` 编排逻辑
- 异步执行，不阻塞主线程用户回复

### 2.3 节点输入数据

| 数据源 | 类型 | 说明 |
|--------|------|------|
| `ConversationProcessContextBO.temporaryMessages` | `List<ConversationMemory>` | 当前轮用户新发的原始消息（state=0，未压缩） |
| `ConversationProcessContextBO.conversationHistory` | `List<ConversationMemory>` | 历史消息列表（压缩点之后的原始消息） |
| `ConversationProcessContextBO.emotionAnalyses` | `List<EmotionAnalysis>` | 每轮情绪分析记录（标签、强度、趋势） |
| `ConversationProcessContextBO.emotionDiagnosis` | `EmotionDiagnosis` | 最近一次完整诊断结果（含风险等级） |
| `ConversationProcessContextBO.conversation` | `Conversation` | 会话元信息（当前轮次、压缩轮次、摘要等） |

### 2.4 节点输出

| 输出 | 说明 |
|------|------|
| `"process"` | 继续执行后续诊断流程 |
| `"end"` | 终止诊断，跳过所有后续节点 |

---

## 三、完整判断流程（四阶段模型）

```
IntentRecognitionNode
  │
  ├── Step 0: 危机安全门（不可绕过）
  │     ├── 命中 → "process"
  │     └── 未命中 → 继续
  │
  ├── Step 1: 动态轮次门控
  │     ├── 轮次不足 且 无特殊情况 → "end"
  │     └── 满足 → 进入 Step 2
  │
  ├── Step 2: 片段质量 + 触发信号分析
  │     ├── 明确满足 → "process"
  │     ├── 明确不满足 → "end"
  │     └── 模糊 → 进入 Step 3
  │
  └── Step 3: LLM 兜底判断
        ├── NEED_DIAGNOSIS → "process"
        └── NO_NEED → "end"
```

---

## 四、Step 0：危机安全门（最高优先级）

### 4.1 设计原则

**无论轮次间隔是否满足、事件是否"完整"，只要检测到危机信号，必须强制触发诊断。**

安全永远是第一优先级。

### 4.2 检测范围

仅扫描 `temporaryMessages`（当前轮消息）中 type=USER 的消息内容。

### 4.3 危机关键词库

| 类别 | 模式 |
|------|------|
| 自杀信号 | "不想活"、"死了算了"、"自杀"、"结束生命"、"活不下去"、"没意思"（搭配否定语境） |
| 自伤信号 | "割腕"、"自残"、"伤害自己"、"想死"、"解脱" |
| 暴力信号 | "想杀人"、"同归于尽"、"报复社会"、"弄死" |

> 注意：预警级别匹配应当安全优先，宁可误判不能漏判。后续可在 EmotionDiagnosis 中做精确评估。

### 4.4 伪代码

```java
String currentUserInput = extractCurrentUserInput(contextBO);
for (CrisisPattern pattern : CRISIS_PATTERNS) {
    if (pattern.matches(currentUserInput)) {
        log.warn("[意图识别-危机] 检测到危机关键词，强制触发诊断");
        return "process";
    }
}
```

---

## 五、Step 1：动态轮次门控

### 5.1 核心思想

两次诊断之间需要积累足够的对话轮次。但阈值不是固定的，而是根据上一次诊断的风险等级和情绪趋势**动态调整**。

### 5.2 动态阈值计算

```
threshold = baseThreshold - historyRiskOffset - emotionTrendOffset
```

| 参数 | 取值逻辑 |
|------|---------|
| `baseThreshold` | 基础间隔 = 5 轮 |
| `historyRiskOffset` | 上次诊断风险等级：HIGH/CRITICAL → 3，MEDIUM → 1，LOW → 0（缩短高风险用户诊断间隔） |
| `emotionTrendOffset` | 近期情绪趋势："恶化" → 1，其他 → 0 |

**示例**：
- 上次诊断 LOW 风险 → `threshold = 5 - 0 - 0 = 5`
- 上次诊断 HIGH 风险 + 趋势恶化 → `threshold = 5 - 3 - 1 = 1`（每轮都触达）

### 5.3 特殊情况：首次诊断

`emotionDiagnosis == null` 时，没有 `lastDiagnosisRound`：

| 条件 | 行为 |
|------|------|
| `currentRound >= 3` | 进入 Step 2（已积累足够对话） |
| `currentRound == 1` 且 当前消息字数 ≥ 50 且命中情绪关键词 | 直接进入 Step 2（用户一上来就在倾诉） |
| 其他 | `"end"`（等待积累更多信息） |

### 5.4 特殊情况：话题切换

即使轮次不满足阈值，如果检测到**话题切换**（当前轮情绪标签与上一轮有显著变化），可以提前进入 Step 2：

```java
String currentLabel = getLatestEmotionLabel(emotionAnalyses);
String previousLabel = getPreviousEmotionLabel(emotionAnalyses);
if (!currentLabel.equals(previousLabel) && isSignificantChange(currentLabel, previousLabel)) {
    // 情绪标签变化明显 → 可能是新的心理事件
    return gotoStep2;
}
```

### 5.5 需要新增的字段

必须在 `EmotionDiagnosis`（或 `Conversation`）上记录诊断时的轮次：

```
EmotionDiagnosis.diagnosisRoundNum: Integer  — 最后一次诊断对应的轮次
```

这样 `lastDiagnosisRound` 才能被准确获取。

---

## 六、Step 2：片段质量 + 触发信号分析

### 6.1 分析范围

分析 `[lastDiagnosisRound + 1, currentRound]` 区间内的对话内容。

### 6.2 文本拼接策略（处理压缩边界）

由于语义压缩的存在，区间内的消息可能被压缩边界截断：

```
时间线:
  上次诊断在轮次 8
  语义压缩在轮次 15（contextSummaryRound = 15）
  当前轮次 18

conversationHistory 中只有 [轮次15, 轮次18] 的原始消息
轮次9 ~ 14 被压缩为 conversation.analysisContextSummary
```

**拼接策略**：

```java
String buildAnalysisText() {
    StringBuilder sb = new StringBuilder();

    Integer compressRound = conversation.getContextSummaryRound();
    Integer lastDxRound = getLastDiagnosisRound(); // 来自 EmotionDiagnosis.diagnosisRoundNum

    // 1. 如果压缩边界在分析区间内，拼接压缩摘要
    if (lastDxRound != null && compressRound != null && lastDxRound < compressRound) {
        sb.append(conversation.getAnalysisContextSummary()).append("\n");
    }

    // 2. 拼接区间内的原始消息
    conversationHistory.stream()
        .filter(m -> lastDxRound == null || m.getRoundNum() > lastDxRound)
        .filter(m -> "USER".equals(m.getType()) || "ASSISTANT".equals(m.getType()))
        .forEach(m -> sb.append(m.getContent()).append("\n"));

    return sb.toString();
}
```

### 6.3 三个判断维度

#### 6.3a 语义饱和度（Semantic Saturation）

判断该段对话中用户是否有足够多的**有意义**情绪/心理内容：

| 信号 | 度量方式 |
|------|---------|
| 有意义轮次占比 | 排除纯应答（"嗯"、"好的"、"知道了"）和纯寒暄轮次后的有效轮次数 / 总轮次数 |
| 有效轮次数量 | 区间内 type=USER 且非纯应答的消息数量 |
| 平均消息长度 | 用户消息的平均字数（排除极短消息） |

```
语义饱和 = (有效轮次占比 > 60%) 或 (有效轮次数量 ≥ 3)
```

#### 6.3b 求助/反思信号（Help-seeking / Reflection Signal）

检测**当前轮**用户消息中是否包含求助意愿或自我反思：

```
关键词模式:
  求助: "我该怎么办"、"帮我"、"怎么办"、"能不能分析"、"你觉得"
  反思: "我是不是有问题"、"为什么会这样"、"我是不是太"、"怎么改变"
  结束信号: "就是这样"、"说完了"、"大概就是这样"
```

```
求助信号 = 命中任一求助关键词
```

#### 6.3c 情绪状态变化（Emotional Delta）

自上次诊断后，情绪趋势是否发生了有意义的变化：

```java
// 从 emotionAnalyses 中提取上次诊断前后的情绪数据
EmotionAnalysis lastDxEmotion = getEmotionAtRound(emotionAnalyses, lastDiagnosisRound);
EmotionAnalysis currentEmotion = getLatestEmotion(emotionAnalyses);

情绪变化显著 = (
    (currentEmotion.emotionIntensity - lastDxEmotion.emotionIntensity) > 0.3  // 强度明显变化
    || !currentEmotion.emotionLabel.equals(lastDxEmotion.emotionLabel)         // 标签切换
    || "恶化".equals(currentEmotion.emotionTrend)                              // 趋势恶化
)
```

### 6.4 Step 2 决策矩阵

```
                    求助信号: 有          求助信号: 无
                    ─────────────        ─────────────
语义饱和: 是        │  "process"        │  看情绪变化
                    │                   │  有变化→"process"
                    │                   │  无变化→模糊→Step 3
────────────────────┼───────────────────┼───────────────────
语义饱和: 否        │  "process"        │  "end"
                    │ (用户求助就响应)    │  (聊了但没实质内容)
```

---

## 七、Step 3：LLM 兜底判断

### 7.1 触发条件

仅当 Step 2 无法做出明确判断时调用（预估占比 10%-20% 的轮次）。

### 7.2 输入构造

不传入 `conversationHistory` 的原始文本，而是传入处理好的结构化数据：

```
Prompt 输入:
├── 当前用户消息: 从 temporaryMessages 提取
├── 近期情绪标签序列: 从 emotionAnalyses 提取最近3轮标签
├── 上次诊断风险等级: 从 emotionDiagnosis 提取
└── 当前轮次: conversation.currentRound
```

### 7.3 Prompt 模板

```
你是一个对话意图分析助手。请判断当前对话轮次是否需要触发心理诊断。

上一次诊断距今已过 {roundsSinceLastDx} 轮。
用户最近3轮的情绪标签序列为: [{recentEmotionLabels}]
当前用户消息: "{currentUserInput}"

请判断是否需要触发心理诊断。仅回答 "NEED_DIAGNOSIS" 或 "NO_NEED"。
```

### 7.4 模型配置

使用 `ChatModelType` 中的轻量模型（如 `qwen-turbo`），`max_tokens=5`，最小化延迟和成本。

---

## 八、完整伪代码

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRecognitionNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "intentRecognitionNode";

    // ========== 危机关键词（Step 0）==========
    private static final List<Pattern> CRISIS_PATTERNS = List.of(
        Pattern.compile(".*(自杀|不想活|死了算了|结束生命|活不下去|解脱).*"),
        Pattern.compile(".*(割腕|自残|伤害自己|想死).*"),
        Pattern.compile(".*(想杀人|同归于尽|报复社会).*")
    );

    // ========== 求助信号关键词（Step 2b）==========
    private static final List<Pattern> HELP_SEEKING_PATTERNS = List.of(
        Pattern.compile(".*(怎么办|帮我|帮帮我|能不能分析|你觉得|分析一下|评价).*"),
        Pattern.compile(".*(我是不是|为什么会这样|怎么改变|正常吗|有问题).*"),
        Pattern.compile(".*(就是这样|说完了|大概就这样|差不多).*")
    );

    // ========== 基础阈值 ==========
    private static final int BASE_THRESHOLD = 5;
    private static final int FIRST_DX_MIN_ROUND = 3;
    private static final int FIRST_DX_MIN_MESSAGE_LENGTH = 50;

    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        ConversationProcessContextBO ctx = state.value(ConversationProcessContextBO.NAME).orElse(null);
        if (ctx == null) {
            log.error("[意图识别] 会话上下文为空");
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
            return Map.of();
        }

        // Step 0: 危机安全门
        if (checkCrisis(ctx)) {
            log.warn("[意图识别-危机] 触发危机诊断");
            return Map.of();  // 不设置 DIAGNOSIS_INTERRUPTED → 条件边走 "process"
        }

        // Step 1: 动态轮次门控
        int currentRound = ctx.getConversation().getCurrentRound();
        EmotionDiagnosis lastDx = ctx.getEmotionDiagnosis();
        Integer lastDxRound = lastDx != null ? lastDx.getRoundNum() : null;  // 需要新增字段

        if (!passRoundGate(currentRound, lastDxRound, lastDx, ctx.getEmotionAnalyses())) {
            log.info("[意图识别-门控] 轮次不满足，跳过诊断");
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
            return Map.of();
        }

        // Step 2: 片段质量 + 触发信号
        String segmentText = buildSegmentText(ctx, lastDxRound);
        boolean hasHelpSignal = checkHelpSignal(ctx);
        boolean isSemanticSaturated = checkSemanticSaturation(ctx, lastDxRound);
        boolean hasEmotionDelta = checkEmotionDelta(ctx.getEmotionAnalyses(), lastDxRound);

        if (isSemanticSaturated && hasHelpSignal) {
            return Map.of();  // process
        }
        if (isSemanticSaturated && hasEmotionDelta) {
            return Map.of();  // process
        }
        if (!isSemanticSaturated && hasHelpSignal) {
            return Map.of();  // process（用户求助就响应）
        }
        if (!isSemanticSaturated && !hasEmotionDelta) {
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
            return Map.of();  // end
        }

        // Step 3: LLM 兜底
        boolean needDx = llmIntentCheck(ctx, segmentText);
        if (!needDx) {
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
        }
        return Map.of();
    }

    // ===== 各步骤实现（详见各章节说明）=====

    private boolean checkCrisis(ConversationProcessContextBO ctx) { /* ... */ }
    private boolean passRoundGate(int currentRound, Integer lastDxRound,
                                   EmotionDiagnosis lastDx,
                                   List<EmotionAnalysis> emotionAnalyses) { /* ... */ }
    private String buildSegmentText(ConversationProcessContextBO ctx, Integer lastDxRound) { /* ... */ }
    private boolean checkHelpSignal(ConversationProcessContextBO ctx) { /* ... */ }
    private boolean checkSemanticSaturation(ConversationProcessContextBO ctx, Integer lastDxRound) { /* ... */ }
    private boolean checkEmotionDelta(List<EmotionAnalysis> analyses, Integer lastDxRound) { /* ... */ }
    private boolean llmIntentCheck(ConversationProcessContextBO ctx, String segmentText) { /* ... */ }
}
```

---

## 九、需要新增/修改的依赖

### 9.1 新增字段

| 表/实体 | 字段 | 类型 | 说明 |
|---------|------|------|------|
| `EmotionDiagnosis` | `round_num` | `Integer` | 诊断时对应的会话轮次（用于计算 `lastDiagnosisRound`） |

### 9.2 修改诊断图构建

在 `DiagnosisGraph.buildGraph()` 中插入节点：

```java
// 原来:
workflow.addEdge(StateGraph.START, InputNode.NODE_NAME);
workflow.addConditionalEdges(InputNode.NODE_NAME, createInterruptConditionEdge(), ...);

// 修改为:
workflow.addEdge(StateGraph.START, IntentRecognitionNode.NODE_NAME);
workflow.addConditionalEdges(IntentRecognitionNode.NODE_NAME, createIntentConditionEdge(),
    Map.of("end", StateGraph.END,
           "process", InputNode.NODE_NAME));
workflow.addConditionalEdges(InputNode.NODE_NAME, createInterruptConditionEdge(), ...);  // 保持不变
```

### 9.3 修改持久化节点

在 `DiagnosisPersistNode` 中写入 `diagnosisRoundNum`：

```java
emotionDiagnosis.setRoundNum(contextBO.getConversation().getCurrentRound());
```

---

## 十、设计决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| 节点位置 | DiagnosisGraph 内部 | 复用条件边中断机制，诊断逻辑自闭环 |
| 文本分析范围 | `[lastDxRound+1, currentRound]` 区间 | 只分析自上次诊断后的"增量"，不分析全量历史 |
| 正则使用范围 | 仅 `temporaryMessages` + Step 0/2b | 只在可控范围内做关键词匹配，不污染全历史分析 |
| Step 2 判断方式 | 三维度（饱和度+求助+情绪变化） | 比"事件完整性"更具可操作性 |
| 轮次阈值 | 动态（基于历史风险等级） | 高风险用户需要更频繁的诊断跟踪 |
| 危机处理 | 最高优先级不可绕过 | 安全第一 |
| LLM 使用 | 仅兜底（预估 10%-20% 轮次） | 避免不必要的 LLM 调用 |

---

## 十一、待讨论项

1. `EmotionDiagnosis.diagnosisRoundNum` 的表结构变更方案
2. 首次诊断时"消息字数 ≥ 50"的阈值是否合理
3. 动态阈值公式中 `historyRiskOffset` 的具体取值是否准确
4. 话题切换检测的具体实现方式（简单标签对比 vs. 语义相似度计算）