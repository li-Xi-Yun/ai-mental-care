# 诊断图意图识别节点设计文档 v2.0

> 版本: v2.0
> 作者: lixiyun
> 日期: 2026-09-18
> 变更: 精简 Step 0 危机处理模型，删除 `crisis_support` 概念，确立"危机关键词不主动触发诊断"的核心原则

---

## 一、背景与问题

### 1.1 现状

当前系统在每轮用户消息处理时，会**无条件**触发完整诊断图流程。诊断图内部虽有 `DIAGNOSIS_INTERRUPTED` 中断机制，但那是在已经进入图之后才做的校验（基于 PreCleanNode 清洗判断消息有效性），Graph 的 invoke 开销和 checkpoint 开销已经产生。

### 1.2 问题

用户发送"你好"、"好的谢谢"、随意敲入的测试性关键词等消息时，完整的诊断图（含 LLM 调用、向量检索、多节点处理）仍然会被执行，造成不必要的算力和 Token 浪费。

### 1.3 目标

新增**意图识别节点**（IntentRecognitionNode），在诊断图执行的最早阶段判断本轮是否需要触发诊断。

---

## 二、节点定位

### 2.1 在诊断图中的位置

放于 `DiagnosisGraph` 内部，`START` 之后的第一个节点，在 `InputNode` 之前：

```
START
  │
  ▼
IntentRecognitionNode  ←─ 新增
  │
  ▼
[条件边: isDiagnosisNeeded?]
  ├── "process" → InputNode → KnowledgeNode → ProcessNode → DiagnosisPersistNode → END
  └── "end"    → END
```

### 2.2 输入数据

| 数据源 | 类型 | 说明 |
|--------|------|------|
| `temporaryMessages` | `List<ConversationMemory>` | 当前轮用户原始消息（state=0，未压缩） |
| `conversationHistory` | `List<ConversationMemory>` | 历史消息（压缩点之后的原始消息） |
| `emotionAnalyses` | `List<EmotionAnalysis>` | 每轮情绪分析记录 |
| `emotionDiagnosis` | `EmotionDiagnosis` | 最近一次完整诊断结果 |
| `conversation` | `Conversation` | 会话元信息（当前轮次、压缩轮次、摘要字段） |

### 2.3 输出

| 输出 | 含义 |
|------|------|
| `"process"` | 继续执行后续诊断图流程 |
| `"end"` | 终止，跳过诊断图 |

---

## 三、完整判断流程

```
IntentRecognitionNode
  │
  ├── Step 0: 危机关键词处理
  │     ├── 命中 且 有近期诊断记录 → elevate_risk → "end"
  │     ├── 命中 但 无近期诊断记录 → "end"
  │     └── 未命中 → 进入 Step 1
  │
  ├── Step 1: 动态轮次门控
  │     ├── 超过最大间隔(20轮) → 强制进入 Step 2
  │     ├── 满足动态阈值下限 → 进入 Step 2
  │     └── 轮次不满足 → "end"
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

## 四、核心原则

### 4.1 危机关键词不主动触发诊断

危机关键词本身是低信噪比信号——用户可能在开玩笑、测试系统、复述他人的话。单条消息没有足够上下文确认是否为真实危机。

真正的危机判断需要多轮情绪趋势、完整诊断图的综合评估以及足够的上下文信息——这些正是诊断流程提供的。因此，**遇到危机关键词时跳过本次诊断，让 Step 1 轮次门控在合适的时机自然触发**。

### 4.2 唯一例外：已有近期诊断结果时升级风险

如果距上次诊断不足 N 轮（动态阈值），说明诊断结论还很新鲜，此时重跑诊断图是浪费。但风险等级需要立刻反映危机信号，此时直接更新已有 `EmotionDiagnosis` 记录的风险字段即可。

### 4.3 没有诊断记录时无法标记，不做处理

`EmotionDiagnosis` 不存在时，`elevate_risk` 无从谈起。直接跳过，不做任何额外操作，让后续的正常诊断流程在合适时机自行诊断。

---

## 五、Step 0：危机关键词处理

### 5.1 危机关键词库

仅扫描 `temporaryMessages`（当前轮）中 `type=USER` 的消息内容：

| 类别 | 模式 |
|------|------|
| 自杀信号 | "自杀"、"不想活"、"死了算了"、"结束生命"、"活不下去"、"解脱" |
| 自伤信号 | "割腕"、"自残"、"伤害自己"、"想死" |
| 暴力信号 | "想杀人"、"同归于尽"、"报复社会" |

### 5.2 处理逻辑

```
危机关键词命中？
  │
  ├── 是 → EmotionDiagnosis 存在 且 (currentRound - diagnosisRoundNum) < N？
  │         ├── 是 → elevate_risk → "end"
  │         │        更新 EmotionDiagnosis.riskLevel、selfHarmRisk、suicideRisk
  │         │
  │         └── 否 → "end"（无诊断记录 / 距上次诊断已过N轮，不做额外操作）
  │
  └── 否 → 进入 Step 1
```

其中 `N` = 动态阈值（见 Step 1）。

### 5.3 伪代码

```java
private boolean handleCrisisKeywords(ConversationProcessContextBO ctx,
                                      EmotionDiagnosis lastDx,
                                      int currentRound, int threshold) {
    if (!matchesCrisisKeywords(extractCurrentUserInput(ctx))) {
        return false; // 未命中，继续走 Step 1
    }

    // 命中危机关键词
    if (lastDx != null && lastDx.getRoundNum() != null
            && (currentRound - lastDx.getRoundNum()) < threshold) {
        // 有近期诊断记录 → 升级风险等级
        log.warn("[意图识别-危机] 升级诊断风险等级，conversationId={}", 
                 ctx.getConversation().getId());
        lastDx.setRiskLevel(RiskLevel.CRITICAL);
        lastDx.setSelfHarmRisk(riskScore);
        lastDx.setSuicideRisk(riskScore);
        diagnosisRepository.updateById(lastDx);
    } else {
        // 无诊断记录 或 距上次诊断较远 → 不做处理
        log.info("[意图识别-危机] 无近期诊断记录，跳过，等待自然触发诊断");
    }

    // 两种情况都不执行诊断图
    return true; // → "end"
}
```

---

## 六、Step 1：动态轮次门控

### 6.1 核心思想

轮次门控有两个边界：

- **下限（动态阈值）**：两次诊断的最短间隔，防止频繁重复诊断。根据历史风险等级动态调整——高风险用户需更频繁跟踪。
- **上限（最大间隔）**：无论情绪如何，超过此间隔必须触发诊断。确保即使是长期积极情绪的用户，也会定期建立基线评估，使后续可能的情绪变化有参照系。

### 6.2 动态阈值计算

```
下限 threshold = baseThreshold - historyRiskOffset - emotionTrendOffset
上限 = MAX_INTERVAL（固定 20 轮）
```

| 参数 | 取值逻辑 |
|------|---------|
| `baseThreshold` | 基础间隔 = 5 轮 |
| `historyRiskOffset` | HIGH/CRITICAL → 3，MEDIUM → 1，LOW → 0 |
| `emotionTrendOffset` | 近期趋势 "恶化" → 1，其他 → 0 |
| `MAX_INTERVAL` | 最大强制间隔 = 20 轮 |

**实际阈值范围**：下限 1 ~ 5 轮，上限 20 轮。

### 6.3 最大间隔的必要性

如果用户长期保持积极情绪（情绪标签始终为"平静"/"愉悦"，无求助信号、无危机关键词），当前设计的 Step 0 + Step 2 将永远无法触发诊断。这带来两个问题：

1. **缺少情绪基线**：从未诊断的用户，系统无法区分"一直积极"和"从消极变积极"。后者是治疗效果，前者是未发现问题——但二者都无法被量化记录。
2. **无法感知拐点**：如果用户第 30 轮突然出现负面情绪，此时没有近期诊断做参照，情绪变化无法计算。

因此设置 20 轮的最大间隔：无论情绪状态如何，聊了这么多轮也该做一次基线评估。此时诊断结论可以是"低风险/健康"，本身即为有价值的信息——为后续可能的情绪变化提供对比基准。

### 6.4 特殊情况

**首次诊断**（`emotionDiagnosis == null`）：

| 条件 | 行为 |
|------|------|
| `currentRound ≥ 3` | 进入 Step 2 |
| `currentRound < 3` | `"end"`（等待积累更多信息） |

**话题切换**（当前轮情绪标签与上一轮显著不同，且轮次差 ≥ 1）：

即使轮次不满足动态阈值下限，如果检测到话题切换（情绪标签发生有意义的变化），也可提前进入 Step 2。

### 6.5 伪代码

```java
private static final int MAX_INTERVAL = 20; // 最大强制间隔

private boolean passRoundGate(int currentRound, Integer lastDxRound,
                               EmotionDiagnosis lastDx,
                               List<EmotionAnalysis> emotionAnalyses) {
    // 首次诊断
    if (lastDx == null || lastDxRound == null) {
        return currentRound >= FIRST_DX_MIN_ROUND; // 3
    }

    int roundDiff = currentRound - lastDxRound;

    // 上限：超过最大间隔，强制触发诊断（无论情绪状态如何）
    if (roundDiff >= MAX_INTERVAL) {
        return true;
    }

    // 下限：动态阈值检查
    int threshold = computeDynamicThreshold(lastDx, emotionAnalyses);
    if (roundDiff >= threshold) {
        return true;
    }

    // 话题切换检查（即使轮次不足）
    if (roundDiff > 0 && hasTopicSwitch(emotionAnalyses, lastDxRound)) {
        return true;
    }

    return false;
}

private int computeDynamicThreshold(EmotionDiagnosis lastDx,
                                     List<EmotionAnalysis> analyses) {
    int threshold = BASE_THRESHOLD; // 5

    // 历史风险偏移
    switch (lastDx.getRiskLevel()) {
        case HIGH:
        case CRITICAL:
            threshold -= 3;
            break;
        case MEDIUM:
            threshold -= 1;
            break;
        default:
            break;
    }

    // 情绪趋势偏移
    EmotionAnalysis latest = getLatest(analyses);
    if (latest != null && "恶化".equals(latest.getEmotionTrend())) {
        threshold -= 1;
    }

    return Math.max(threshold, 1); // 最小1轮
}
```

---

## 七、Step 2：片段质量 + 触发信号分析

### 7.1 分析范围

分析 `[lastDxRound + 1, currentRound]` 区间的对话内容。若为首次诊断，则分析 `[1, currentRound]` 的**全部**对话。

### 7.2 文本拼接（处理压缩边界）

语义压缩可能导致区间内部分消息被压缩。需要拼接压缩摘要 + 区间内原始消息：

```java
private String buildSegmentText(ConversationProcessContextBO ctx, Integer lastDxRound) {
    StringBuilder sb = new StringBuilder();
    Conversation conv = ctx.getConversation();

    // 1. 如果压缩边界在分析区间内，拼接压缩摘要
    if (lastDxRound != null
            && conv.getContextSummaryRound() != null
            && lastDxRound < conv.getContextSummaryRound()) {
        sb.append(conv.getAnalysisContextSummary()).append("\n");
    }

    // 2. 拼接区间内的原始消息
    ctx.getConversationHistory().stream()
        .filter(m -> lastDxRound == null || m.getRoundNum() > lastDxRound)
        .filter(m -> "USER".equals(m.getType()) || "ASSISTANT".equals(m.getType()))
        .forEach(m -> sb.append(m.getContent()).append("\n"));

    return sb.toString();
}
```

### 7.3 三个判断维度

#### 7.3a 语义饱和度

判断区间内是否有足够多的有意义情绪/心理内容（排除纯应答和纯寒暄）：

```
语义饱和 = 有效轮次占比 > 60% 或 有效轮次数量 ≥ 3
```

有效轮次：`type=USER`、非纯应答（排除"嗯"、"好的"、"知道了"、"哦"、"谢谢"）、非纯寒暄（排除"你好"、"hi"）。

#### 7.3b 求助/反思信号

检测**当前轮**用户消息中是否包含求助意愿或自我反思：

```
求助信号:
  "我该怎么办"、"帮我"、"怎么办"、"能不能分析"、"你觉得"、"分析一下"、"评价"

反思信号:
  "我是不是有问题"、"为什么会这样"、"我是不是太"、"怎么改变"、"正常吗"

结束信号:
  "就是这样"、"说完了"、"大概就这样"、"差不多"
```

命中任一关键词 → 求助信号 = true。

#### 7.3c 情绪状态变化

自上次诊断后，情绪趋势是否有有意义的变化：

```java
private boolean hasEmotionDelta(List<EmotionAnalysis> analyses, Integer lastDxRound) {
    EmotionAnalysis lastDxEmotion = getEmotionAtOrBeforeRound(analyses, lastDxRound);
    EmotionAnalysis current = getLatest(analyses);

    if (lastDxEmotion == null || current == null) {
        return false; // 无法对比
    }

    return Math.abs(current.getEmotionIntensity() - lastDxEmotion.getEmotionIntensity()) > 0.3
        || !Objects.equals(current.getEmotionLabel(), lastDxEmotion.getEmotionLabel())
        || "恶化".equals(current.getEmotionTrend());
}
```

### 7.4 决策矩阵

```
                    求助信号: 有          求助信号: 无
                    ─────────────        ─────────────
语义饱和: 是        │  "process"        │  看情绪变化
                    │                   │  有变化→"process"
                    │                   │  无变化→模糊→Step 3
────────────────────┼───────────────────┼───────────────────
语义饱和: 否        │  "process"        │  "end"
                    │ (求助就响应)       │
```

---

## 八、Step 3：LLM 兜底判断

### 8.1 触发条件

仅 Step 2 无法明确判断时调用（预估覆盖 10%~20% 的轮次）。

### 8.2 输入数据

不传入 `conversationHistory` 原始文本，只传入结构化摘要：

| 输入 | 来源 |
|------|------|
| 当前用户消息 | `temporaryMessages` 提取 |
| 近期情绪标签序列 | `emotionAnalyses` 最近 3 条 |
| 上次诊断风险等级 | `emotionDiagnosis.riskLevel` |
| 距上次诊断轮次 | `currentRound - diagnosisRoundNum` |

### 8.3 Prompt

```
你是一个对话意图分析助手。请判断当前对话轮次是否需要触发心理诊断。

距上次诊断已过 {roundsSinceLastDx} 轮。
用户近期情绪标签序列: [{recentLabels}]
当前用户消息: "{currentUserInput}"

仅回答 "NEED_DIAGNOSIS" 或 "NO_NEED"。
```

### 8.4 模型选择

使用 `ChatModelType` 中的轻量模型，`max_tokens=5`。

---

## 九、完整伪代码

```java
@Slf4j
@Component
public class IntentRecognitionNode implements NodeActionWithConfig, NodeExecutionSummary {

    public static final String NODE_NAME = "intentRecognitionNode";

    // ========= 危机关键词（Step 0）=========
    private static final List<Pattern> CRISIS_PATTERNS = List.of(
        Pattern.compile(".*(自杀|不想活|死了算了|结束生命|活不下去|解脱).*"),
        Pattern.compile(".*(割腕|自残|伤害自己|想死).*"),
        Pattern.compile(".*(想杀人|同归于尽|报复社会).*")
    );

    // ========= 求助/反思信号（Step 2b）=========
    private static final List<Pattern> HELP_SEEKING_PATTERNS = List.of(
        Pattern.compile(".*(怎么办|帮我|帮帮我|分析一下|你觉得|分析|评价).*"),
        Pattern.compile(".*(我是不是|为什么会这样|怎么改变|正常吗|有问题).*"),
        Pattern.compile(".*(就是这样|说完了|大概就这样|差不多).*")
    );

    // ========= 阈值常量 ==========
    private static final int BASE_THRESHOLD = 5;
    private static final int MAX_INTERVAL = 20;
    private static final int FIRST_DX_MIN_ROUND = 3;
    private static final double EMOTION_DELTA_THRESHOLD = 0.3;

    private final AiNodeConfigManager aiNodeConfigManager;
    private final ChatModelFactory chatModelFactory;
    private final IDiagnosisRepository diagnosisRepository;

    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
        ConversationProcessContextBO ctx = state.value(ConversationProcessContextBO.NAME).orElse(null);
        if (ctx == null) {
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
            return Map.of();
        }

        int currentRound = ctx.getConversation().getCurrentRound();
        EmotionDiagnosis lastDx = ctx.getEmotionDiagnosis();
        Integer lastDxRound = (lastDx != null) ? lastDx.getRoundNum() : null;
        List<EmotionAnalysis> analyses = ctx.getEmotionAnalyses();

        // ===== Step 0: 危机关键词处理 =====
        int threshold = computeDynamicThreshold(lastDx, analyses);
        if (handleCrisisKeywords(ctx, lastDx, currentRound, threshold)) {
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
            return Map.of();
        }

        // ===== Step 1: 动态轮次门控 =====
        if (!passRoundGate(currentRound, lastDxRound, lastDx, analyses)) {
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
            return Map.of();
        }

        // ===== Step 2: 片段质量 + 触发信号 =====
        boolean hasHelpSignal = hasHelpSignal(ctx);
        boolean saturated = isSemanticSaturated(ctx, lastDxRound);
        boolean hasDelta = hasEmotionDelta(analyses, lastDxRound);

        if (saturated && hasHelpSignal) return Map.of();            // process
        if (saturated && hasDelta) return Map.of();                 // process
        if (!saturated && hasHelpSignal) return Map.of();           // process
        if (!saturated && !hasDelta) {
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
            return Map.of();                                         // end
        }

        // ===== Step 3: LLM 兜底 =====
        if (!llmIntentCheck(ctx, buildSegmentText(ctx, lastDxRound), lastDxRound)) {
            config.context().put(GraphConstant.DIAGNOSIS_INTERRUPTED, true);
        }
        return Map.of();
    }

    // ========= Step 0: 危机关键词处理 =========
    private boolean handleCrisisKeywords(ConversationProcessContextBO ctx,
                                          EmotionDiagnosis lastDx,
                                          int currentRound, int threshold) {
        if (!matchesCrisisKeywords(extractCurrentUserInput(ctx))) {
            return false;
        }
        if (lastDx != null && lastDx.getRoundNum() != null
                && (currentRound - lastDx.getRoundNum()) < threshold) {
            lastDx.setRiskLevel(RiskLevel.CRITICAL);
            lastDx.setSelfHarmRisk(riskScore);
            lastDx.setSuicideRisk(riskScore);
            diagnosisRepository.updateById(lastDx);
        }
        return true; // 无论是否升级，命中即终止
    }

    // ========= 其他方法 =========
    private String extractCurrentUserInput(ConversationProcessContextBO ctx) { /* ... */ }
    private boolean matchesCrisisKeywords(String input) { /* ... */ }
    private int computeDynamicThreshold(EmotionDiagnosis lastDx, List<EmotionAnalysis> analyses) { /* ... */ }
    private boolean passRoundGate(int round, Integer lastDxRound, EmotionDiagnosis lastDx,
                                   List<EmotionAnalysis> analyses) { /* ... */ }
    private boolean hasTopicSwitch(List<EmotionAnalysis> analyses, Integer lastDxRound) { /* ... */ }
    private String buildSegmentText(ConversationProcessContextBO ctx, Integer lastDxRound) { /* ... */ }
    private boolean hasHelpSignal(ConversationProcessContextBO ctx) { /* ... */ }
    private boolean isSemanticSaturated(ConversationProcessContextBO ctx, Integer lastDxRound) { /* ... */ }
    private boolean hasEmotionDelta(List<EmotionAnalysis> analyses, Integer lastDxRound) { /* ... */ }
    private boolean llmIntentCheck(ConversationProcessContextBO ctx, String text, Integer lastDxRound) { /* ... */ }
}
```

---

## 十、需要新增/修改的依赖

### 10.1 新增字段

| 表/实体 | 字段 | 类型 | 说明 |
|---------|------|------|------|
| `EmotionDiagnosis` | `round_num` | `Integer` | 诊断时对应会话轮次 |

### 10.2 修改诊断图构建

`DiagnosisGraph.buildGraph()` 中：

```java
// 插入 IntentRecognitionNode 作为第一个节点
workflow.addEdge(StateGraph.START, IntentRecognitionNode.NODE_NAME);
workflow.addConditionalEdges(IntentRecognitionNode.NODE_NAME, createIntentConditionEdge(),
    Map.of("end", StateGraph.END, "process", InputNode.NODE_NAME));
workflow.addConditionalEdges(InputNode.NODE_NAME, createInterruptConditionEdge(), ...); // 保持不变
```

### 10.3 修改持久化节点

`DiagnosisPersistNode` 中写入诊断轮次：

```java
emotionDiagnosis.setRoundNum(contextBO.getConversation().getCurrentRound());
```

---

## 十一、设计决策记录

| # | 决策 | 理由 |
|---|------|------|
| 1 | 节点放在 DiagnosisGraph 内部 | 复用条件边中断机制，诊断逻辑自闭环 |
| 2 | 文本分析仅对区间增量做，不分析全量历史 | 避免压缩摘要污染，聚焦自上次诊断后的新信息 |
| 3 | 轮次阈值动态调整 | 高风险用户需更频繁跟踪，低风险用户可拉长间隔 |
| 4 | 危机关键词不主动触发诊断 | 单条消息信噪比低，真正的危机判断需要上下文和综合评估 |
| 5 | 危机命中 + 有近期诊断 → 直接升降已有记录风险 | 避免重复跑昂贵的诊断图，诊断结论仍然有效仅风险升级 |
| 6 | 危机命中 + 无诊断记录 → 不做处理跳过 | 无法标记；让 Step 1 轮次门控在合适时机自然触发 |
| 7 | Step 2 三维度（饱和度 + 求助 + 情绪变化） | 比单一"事件完整性"更具可操作性和可量化性 |
| 8 | LLM 仅兜底 | 避免不必要调用，预估覆盖 10%~20% 轮次 |
| 9 | 最大间隔(20轮)强制诊断 | 防止长期积极情绪用户永不触发诊断；建立情绪基线，为后续可能的情绪变化提供对比基准 |