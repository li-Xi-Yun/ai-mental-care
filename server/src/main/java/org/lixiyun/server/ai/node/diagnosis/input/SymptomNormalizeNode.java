package org.lixiyun.server.ai.node.diagnosis.input;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.common.json.utils.JsonUtils;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.InputResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomNormalizeResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomOriginalItem;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.normalization.SymptomTerm;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.CoreInfoExtractResult;
import org.lixiyun.pojo.bo.conversation.diagnosis.input.structure.SymptomRawItem;
import org.lixiyun.pojo.entity.conversation.SymptomDict;
import org.lixiyun.server.ai.model.ChatModelFactory;
import org.lixiyun.server.ai.model.diagnosis.input.SymptomNormalizeModel;
import org.lixiyun.server.mapper.SymptomDictMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 输入侧-症状语义归一化处理节点
 * <p>
 * 将核心信息清单提取出的症状表述原文列表，通过规则词典匹配与轻量模型语义归一化，
 * 转化为标准化症状标签列表与术语-原文映射对照表。
 * <p>
 * 处理流程：
 * <ol>
 *   <li>步骤1：输入预处理与清洗 - 对症状表述原文列表执行文本预处理与清洗操作</li>
 *   <li>步骤2：空数据校验 - 清洗后数据为空则直接结束；存在有效数据则继续</li>
 *   <li>步骤3：规则词典前置匹配 - 基于预构建的口语化症状-标准术语映射词典做字符串精准/模糊匹配</li>
 *   <li>步骤4：匹配结果分支判断 - 全部匹配成功跳转聚合环节；未全部匹配则进入模型语义归一化</li>
 *   <li>步骤5：轻量模型语义归一化 - 仅处理规则匹配失败的表述，严格控制模型成本</li>
 *   <li>步骤6：结果聚合与统计 - 整合词典匹配、模型归一化两部分输出，完成结果汇总统计</li>
 *   <li>步骤7：标准化输出封装 - 对聚合统计后的数据做标准化封装</li>
 * </ol>
 * <p>
 * 输入：核心信息清单提取出的症状表述原文列表（{@link SymptomRawItem}）
 * <p>
 * 输出：标准化症状标签列表（{@link SymptomTerm}）+ 术语-原文映射对照表（{@link SymptomOriginalItem}）
 *
 * @author lixiyun
 * @since 2026-08-10 17:26
 * @see SymptomNormalizeResult
 * @see SymptomTerm
 * @see SymptomOriginalItem
 * @see SymptomNormalizeModel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SymptomNormalizeNode implements NodeActionWithConfig {

    /**
     * 规则词典匹配的默认置信度，精确匹配视为完全可信
     */
    private static final BigDecimal RULE_MATCH_CONFIDENCE = BigDecimal.ONE;

    /**
     * 纯噪声文本匹配模式：仅包含空白、标点或符号字符的文本
     */
    private static final Pattern NOISE_PATTERN = Pattern.compile("^[\\s\\p{P}\\p{S}]+$");

    /**
     * 常见中文语气词/无意义重复词集合，用于噪声过滤
     */
    private static final Set<String> MODAL_WORDS = Set.of(
            "嗯", "哦", "啊", "哈", "呀", "哎", "唉",
            "嗯嗯", "哦哦", "嗯嗯嗯", "哈哈", "呵呵"
    );

    private final SymptomNormalizeModel symptomNormalizeModel;
    private final ChatModelFactory chatModelFactory;
    private final SymptomDictMapper symptomDictMapper;
    private final ChatModel chatModel = chatModelFactory.getDeepSeekChatModel();

    /**
     * 节点主执行方法，编排完整的症状语义归一化处理流程
     * <p>
     * 执行顺序：
     * 1. 从全局状态中获取输入侧聚合结果
     * 2. 提取核心信息中的症状原文列表
     * 3. 依次执行预处理清洗、规则匹配、模型归一化、结果聚合
     * 4. 将归一化结果回写到输入侧聚合结果中
     *
     * @param state  全局状态，包含输入侧聚合结果 {@link InputResult}
     * @param config 运行时配置
     * @return 空Map（结果通过state传递）
     * @throws BusinessException 当输入侧聚合结果不存在时抛出 {@link ConversationExceptionEnum#INPUT_RESULT_NOT_EXIST}
     */
    @Override
    public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
        log.info("输入侧-语义归一化处理-开始");

        Optional<InputResult> inputResultOpt = state.value(InputResult.NAME);
        if (inputResultOpt.isEmpty()) {
            log.error("输入侧-语义归一化处理-输入侧聚合结果为空");
            throw new BusinessException(ConversationExceptionEnum.INPUT_RESULT_NOT_EXIST);
        }
        InputResult inputResult = inputResultOpt.get();
        CoreInfoExtractResult coreInfoExtractResult = inputResult.getCoreInfoExtractResult();
        if (coreInfoExtractResult == null || coreInfoExtractResult.getSymptomOriginalList() == null
                || coreInfoExtractResult.getSymptomOriginalList().isEmpty()) {
            log.info("输入侧-语义归一化处理-症状原文列表为空，跳过归一化处理");
            return Map.of();
        }

        List<SymptomRawItem> rawItemList = coreInfoExtractResult.getSymptomOriginalList();
        log.info("输入侧-语义归一化处理-症状原文条数：{}", rawItemList.size());

        List<SymptomRawItem> cleanedList = preprocessAndClean(rawItemList);
        log.info("输入侧-语义归一化处理-清洗后有效条数：{}", cleanedList.size());

        if (cleanedList.isEmpty()) {
            log.info("输入侧-语义归一化处理-清洗后数据为空，结束流程");
            return Map.of();
        }

        List<SymptomDict> dictList = loadEnabledSymptomDicts();
        log.info("输入侧-语义归一化处理-加载规则词典条数：{}", dictList.size());

        Map<String, List<SymptomRawItem>> groupedByCleanedText = groupByCleanedText(cleanedList);

        Map<String, SymptomDict> ruleMatchedMap = new LinkedHashMap<>();
        List<String> unmatchedTexts = new ArrayList<>();
        matchByDictionary(groupedByCleanedText, dictList, ruleMatchedMap, unmatchedTexts);
        log.info("输入侧-语义归一化处理-规则匹配成功：{}，待模型处理：{}", ruleMatchedMap.size(), unmatchedTexts.size());

        Map<String, ModelMatchEntry> modelMatchedMap = new LinkedHashMap<>();
        List<String> stillUnmatchedTexts = new ArrayList<>();
        if (!unmatchedTexts.isEmpty()) {
            normalizeByModel(unmatchedTexts, dictList, modelMatchedMap, stillUnmatchedTexts);
            log.info("输入侧-语义归一化处理-模型匹配成功：{}，未匹配保留原文：{}", modelMatchedMap.size(), stillUnmatchedTexts.size());
        }

        SymptomNormalizeResult result = aggregateAndBuildResult(
                groupedByCleanedText, ruleMatchedMap, modelMatchedMap, stillUnmatchedTexts);

        inputResult.setSymptomNormalizeResult(result);
        log.info("输入侧-语义归一化处理-完成，标准化标签数：{}", result.getTermList().size());
        return Map.of();
    }

    /**
     * 步骤1：输入预处理与清洗
     * <p>
     * 对症状原文列表逐条执行清洗操作：
     * <ul>
     *   <li>文本格式归一（去除首尾空格、统一标点、全角转半角）</li>
     *   <li>纯噪声过滤（剔除纯语气词、无意义重复字符等无效内容）</li>
     *   <li>清洗后为空的条目直接丢弃</li>
     * </ul>
     *
     * @param rawItemList 症状原文列表
     * @return 清洗后的有效症状条目列表
     */
    private List<SymptomRawItem> preprocessAndClean(List<SymptomRawItem> rawItemList) {
        List<SymptomRawItem> cleanedList = new ArrayList<>();
        for (SymptomRawItem item : rawItemList) {
            String cleaned = cleanText(item.getOriginalText());
            if (cleaned == null || cleaned.isEmpty()) {
                continue;
            }
            if (isNoiseText(cleaned)) {
                continue;
            }
            cleanedList.add(SymptomRawItem.builder()
                    .roundNum(item.getRoundNum())
                    .originalText(cleaned)
                    .build());
        }
        return cleanedList;
    }

    /**
     * 文本清洗核心方法
     * <p>
     * 执行顺序：去除首尾空格 → 全角转半角 → 标点归一化 → 合并多余空白
     *
     * @param text 待清洗的原始文本
     * @return 清洗后的文本，若清洗后为空则返回null
     */
    private String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim();
        cleaned = fullWidthToHalfWidth(cleaned);
        cleaned = normalizePunctuation(cleaned);
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 全角字符转半角
     * <p>
     * 将全角字母/数字/符号（U+FF01~U+FF5E）转为对应半角字符，
     * 全角空格（U+3000）转为半角空格，消除格式差异，减少 token 花销
     *
     * @param text 包含全角字符的文本
     * @return 全部转为半角后的文本
     */
    private String fullWidthToHalfWidth(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\u3000') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 中文标点转英文标点
     * <p>
     * 将常见中文标点（逗号、句号、感叹号等）统一转为英文半角标点，
     * 消除因输入法差异导致的匹配失败
     *
     * @param text 包含中文标点的文本
     * @return 标点归一化后的文本
     */
    private String normalizePunctuation(String text) {
        return text
                .replace('\uFF0C', ',')
                .replace('\u3002', '.')
                .replace('\uFF01', '!')
                .replace('\uFF1F', '?')
                .replace('\uFF1A', ':')
                .replace('\uFF1B', ';')
                .replace("\u201C", "\"")
                .replace("\u201D", "\"")
                .replace("\u2018", "'")
                .replace("\u2019", "'")
                .replace('\uFF08', '(')
                .replace('\uFF09', ')');
    }

    /**
     * 噪声文本判断
     * <p>
     * 依次检查以下噪声特征：
     * <ol>
     *   <li>仅包含空白/标点/符号字符</li>
     *   <li>去除标点后内容为空</li>
     *   <li>属于预设的语气词集合（如"嗯"、"哦"、"哈哈"等）</li>
     *   <li>属于无意义重复字符（如"啊啊"、"111"等3字符以内的纯重复）</li>
     * </ol>
     *
     * @param text 待判断的文本
     * @return true表示为噪声文本，应过滤掉
     */
    private boolean isNoiseText(String text) {
        if (NOISE_PATTERN.matcher(text).matches()) {
            return true;
        }
        String stripped = text.replaceAll("[\\s,.!?;:'\"()]", "");
        if (stripped.isEmpty()) {
            return true;
        }
        if (MODAL_WORDS.contains(stripped)) {
            return true;
        }
        return isRepetitiveNoise(stripped);
    }

    /**
     * 无意义重复字符判断
     * <p>
     * 检测2~3个字符长度的纯重复文本（如"啊啊"、"111"），
     * 超过3个字符的文本不判定为重复噪声，避免误过滤有效短句
     *
     * @param text 去除标点后的文本
     * @return true表示为无意义重复字符
     */
    private boolean isRepetitiveNoise(String text) {
        if (text.length() <= 1) {
            return false;
        }
        if (text.length() <= 3) {
            char first = text.charAt(0);
            for (int i = 1; i < text.length(); i++) {
                if (text.charAt(i) != first) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 加载启用的症状标准术语字典
     * <p>
     * 从数据库中查询所有状态为启用（{@link SymptomDict#STATUS_ENABLED}）的术语记录，
     * 作为规则匹配的基准数据
     *
     * @return 启用状态的术语字典列表
     */
    private List<SymptomDict> loadEnabledSymptomDicts() {
        return symptomDictMapper.selectList(new LambdaQueryWrapper<SymptomDict>()
                .eq(SymptomDict::getStatus, SymptomDict.STATUS_ENABLED));
    }

    /**
     * 按清洗后的文本内容分组
     * <p>
     * 将完全一致的清洗后表述合并到同一组，实现完全重复内容去重，
     * 同时保留每条原始记录的轮次信息，便于后续统计出现次数与对应轮次
     *
     * @param cleanedList 清洗后的症状条目列表
     * @return key=清洗后文本，value=该文本对应的所有原始条目（含不同轮次）
     */
    private Map<String, List<SymptomRawItem>> groupByCleanedText(List<SymptomRawItem> cleanedList) {
        return cleanedList.stream()
                .collect(Collectors.groupingBy(
                        SymptomRawItem::getOriginalText,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * 步骤3：规则词典前置匹配
     * <p>
     * 遍历清洗后的症状原文分组，逐一匹配词典中的口语表述集合：
     * <ul>
     *   <li>匹配成功的，直接关联对应标准术语，标记为「规则匹配」，存入ruleMatchedMap</li>
     *   <li>匹配失败的个性化/小众表述，归入「待模型处理集合」unmatchedTexts</li>
     * </ul>
     *
     * @param groupedByCleanedText 按清洗文本分组的症状条目
     * @param dictList             规则词典列表
     * @param ruleMatchedMap       输出参数：规则匹配成功的映射（key=清洗后文本，value=匹配到的字典记录）
     * @param unmatchedTexts       输出参数：规则匹配失败的文本列表，待模型处理
     */
    private void matchByDictionary(Map<String, List<SymptomRawItem>> groupedByCleanedText,
                                   List<SymptomDict> dictList,
                                   Map<String, SymptomDict> ruleMatchedMap,
                                   List<String> unmatchedTexts) {
        Map<String, SymptomDict> synonymIndex = buildSynonymIndex(dictList);

        for (Map.Entry<String, List<SymptomRawItem>> entry : groupedByCleanedText.entrySet()) {
            String cleanedText = entry.getKey();
            SymptomDict matched = matchSingleText(cleanedText, dictList, synonymIndex);
            if (matched != null) {
                ruleMatchedMap.put(cleanedText, matched);
            } else {
                unmatchedTexts.add(cleanedText);
            }
        }
    }

    /**
     * 构建同义词精准匹配索引
     * <p>
     * 将每条字典记录的标准术语名称及其所有同义口语表述，
     * 全部作为key映射到对应的字典记录，用于O(1)精准查找。
     * <p>
     * 索引结构示例：
     * <pre>
     * "睡不着"              → SymptomDict(入睡困难)
     * "躺床上翻来覆去睡不着" → SymptomDict(入睡困难)
     * "入睡困难"            → SymptomDict(入睡困难)
     * </pre>
     *
     * @param dictList 规则词典列表
     * @return key=标准术语或同义口语表述，value=对应的字典记录
     */
    private Map<String, SymptomDict> buildSynonymIndex(List<SymptomDict> dictList) {
        Map<String, SymptomDict> index = new HashMap<>();
        for (SymptomDict dict : dictList) {
            if (dict.getSymptomTerm() != null) {
                index.put(dict.getSymptomTerm(), dict);
            }
            if (dict.getSynonymWords() != null && !dict.getSynonymWords().isBlank()) {
                List<String> synonyms = JsonUtils.parseArray(dict.getSynonymWords(), String.class);
                if (synonyms != null) {
                    for (String synonym : synonyms) {
                        if (synonym != null && !synonym.isBlank()) {
                            String normalized = cleanText(synonym);
                            if (normalized != null) {
                                index.put(normalized, dict);
                            }
                        }
                    }
                }
            }
        }
        return index;
    }

    /**
     * 单条文本的规则匹配
     * <p>
     * 采用两级匹配策略：
     * <ol>
     *   <li>精准匹配：通过同义词索引O(1)查找，文本与索引key完全一致时命中</li>
     *   <li>模糊匹配：遍历词典，判断文本是否包含标准术语或任一同义口语表述（子串包含）</li>
     * </ol>
     * 精准匹配优先返回，模糊匹配按词典顺序返回首个命中项
     *
     * @param text          待匹配的清洗后文本
     * @param dictList      规则词典列表（用于模糊匹配）
     * @param synonymIndex  同义词精准匹配索引
     * @return 匹配到的字典记录，未匹配返回null
     */
    private SymptomDict matchSingleText(String text, List<SymptomDict> dictList,
                                        Map<String, SymptomDict> synonymIndex) {
        SymptomDict exactMatch = synonymIndex.get(text);
        if (exactMatch != null) {
            return exactMatch;
        }
        for (SymptomDict dict : dictList) {
            if (dict.getSymptomTerm() != null && text.contains(dict.getSymptomTerm())) {
                return dict;
            }
            if (dict.getSynonymWords() != null && !dict.getSynonymWords().isBlank()) {
                List<String> synonyms = JsonUtils.parseArray(dict.getSynonymWords(), String.class);
                if (synonyms != null) {
                    for (String synonym : synonyms) {
                        if (synonym != null && !synonym.isBlank() && text.contains(synonym)) {
                            return dict;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 步骤5：轻量模型语义归一化
     * <p>
     * 仅处理规则匹配失败的表述，不做全量调用，严格控制模型成本。
     * <p>
     * 处理逻辑：
     * <ol>
     *   <li>构建包含标准症状库（含ID）与待匹配文本的用户提示词</li>
     *   <li>调用 {@link SymptomNormalizeModel} 进行语义归一化</li>
     *   <li>解析模型输出的 {@link SymptomNormalizeModel.SymptomNormalizeModelResult}，从 termList 中提取映射关系与置信度</li>
     *   <li>通过 matchedTermId 关联真实的 {@link SymptomDict} 记录</li>
     *   <li>模型也无法匹配的极小众表述，保留原文作为临时标签，标记「未归一」</li>
     * </ol>
     * <p>
     * 异常兜底：模型调用失败时，全部未匹配文本保留原文，不强行归类
     *
     * @param unmatchedTexts      规则匹配失败的文本列表
     * @param dictList            规则词典列表（用于构建模型prompt与校验模型输出）
     * @param modelMatchedMap     输出参数：模型匹配成功的映射（key=原文，value=携带字典记录与置信度的匹配条目）
     * @param stillUnmatchedTexts 输出参数：模型也无法匹配的文本列表，保留原文作为临时标签
     */
    private void normalizeByModel(List<String> unmatchedTexts,
                                 List<SymptomDict> dictList,
                                 Map<String, ModelMatchEntry> modelMatchedMap,
                                 List<String> stillUnmatchedTexts) {
        String userPrompt = buildModelUserPrompt(unmatchedTexts, dictList);
        try {
            SymptomNormalizeModel.SymptomNormalizeModelResult modelOutput = symptomNormalizeModel.callForResult(chatModel, userPrompt);

            if (modelOutput == null || modelOutput.getTermList() == null) {
                log.warn("输入侧-语义归一化处理-模型输出为空，全部保留原文");
                stillUnmatchedTexts.addAll(unmatchedTexts);
                return;
            }

            Map<Long, SymptomDict> idToDict = dictList.stream()
                    .collect(Collectors.toMap(SymptomDict::getId, d -> d, (a, b) -> a));

            Set<String> matchedOriginalTexts = new HashSet<>();
            for (SymptomOriginalItem item : modelOutput.getTermList()) {
                if (item.getOriginalText() == null) {
                    continue;
                }
                if (item.getMatchedTermId() == null) {
                    continue;
                }
                SymptomDict matchedDict = idToDict.get(item.getMatchedTermId());
                if (matchedDict != null) {
                    modelMatchedMap.put(item.getOriginalText(),
                            new ModelMatchEntry(matchedDict, item.getMatchConfidence()));
                    matchedOriginalTexts.add(item.getOriginalText());
                }
            }

            for (String text : unmatchedTexts) {
                if (!matchedOriginalTexts.contains(text)) {
                    stillUnmatchedTexts.add(text);
                }
            }
        } catch (Exception e) {
            log.error("输入侧-语义归一化处理-模型调用异常，全部保留原文", e);
            stillUnmatchedTexts.addAll(unmatchedTexts);
        }
    }

    /**
     * 构建模型语义归一化的用户提示词
     * <p>
     * 提示词包含两部分：
     * <ul>
     *   <li>标准症状库：列出所有启用状态的标准术语（含ID、名称、大类），供模型选择匹配</li>
     *   <li>待匹配文本：规则匹配失败的用户原始表述列表</li>
     * </ul>
     *
     * @param unmatchedTexts 待匹配的用户症状表述列表
     * @param dictList       标准症状字典列表
     * @return 构建完成的用户提示词
     */
    private String buildModelUserPrompt(List<String> unmatchedTexts, List<SymptomDict> dictList) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下用户症状表述映射到标准症状术语。\n\n");

        sb.append("## 标准症状库\n");
        for (SymptomDict dict : dictList) {
            sb.append("- ID:").append(dict.getId())
                    .append(", 名称:").append(dict.getSymptomTerm());
            if (dict.getSymptomCategory() != null) {
                sb.append(", 大类:").append(dict.getSymptomCategory());
            }
            sb.append("\n");
        }

        sb.append("\n## 待匹配的用户症状表述\n");
        for (String text : unmatchedTexts) {
            sb.append("- ").append(text).append("\n");
        }

        return sb.toString();
    }

    /**
     * 步骤6+7：结果聚合与统计 + 标准化输出封装
     * <p>
     * 整合词典匹配、模型归一化两部分输出结果，完成结果汇总统计：
     * <ol>
     *   <li>按标准标签做分组聚合：同一标签的多条原文合并，汇总所有对应轮次、出现总次数</li>
     *   <li>标记每个标签的匹配来源（规则/模型/未归一），方便后续排查优化词典</li>
     *   <li>记录每个标签的匹配置信度：规则匹配为1.0，模型匹配取模型返回值</li>
     *   <li>按出现次数降序排序，高频核心症状在前</li>
     *   <li>未匹配兜底：对于模型也无法匹配的极小众表述，保留原文作为临时标签，标记「未归一」，不强行归类</li>
     * </ol>
     * <p>
     * 未匹配项的termKey使用 -1 * hashCode 生成合成key，避免与真实termId冲突
     *
     * @param groupedByCleanedText 按清洗文本分组的症状条目
     * @param ruleMatchedMap       规则匹配成功的映射
     * @param modelMatchedMap      模型匹配成功的映射（携带置信度）
     * @param stillUnmatchedTexts  模型也无法匹配的文本列表
     * @return 标准化封装的归一化结果
     */
    private SymptomNormalizeResult aggregateAndBuildResult(
            Map<String, List<SymptomRawItem>> groupedByCleanedText,
            Map<String, SymptomDict> ruleMatchedMap,
            Map<String, ModelMatchEntry> modelMatchedMap,
            List<String> stillUnmatchedTexts) {

        Map<Long, List<SymptomOriginalItem>> termOriginalMapping = new LinkedHashMap<>();
        Map<Long, SymptomTermAggregator> aggregators = new LinkedHashMap<>();

        for (Map.Entry<String, List<SymptomRawItem>> entry : groupedByCleanedText.entrySet()) {
            String cleanedText = entry.getKey();
            List<SymptomRawItem> items = entry.getValue();

            SymptomDict matchedDict;
            int matchSource;
            BigDecimal confidence;

            if (ruleMatchedMap.containsKey(cleanedText)) {
                matchedDict = ruleMatchedMap.get(cleanedText);
                matchSource = SymptomTerm.MATCH_SOURCE_RULE;
                confidence = RULE_MATCH_CONFIDENCE;
            } else if (modelMatchedMap.containsKey(cleanedText)) {
                ModelMatchEntry modelEntry = modelMatchedMap.get(cleanedText);
                matchedDict = modelEntry.dict();
                matchSource = SymptomTerm.MATCH_SOURCE_MODEL;
                confidence = modelEntry.confidence();
            } else {
                matchedDict = null;
                matchSource = SymptomTerm.MATCH_SOURCE_RAW;
                confidence = null;
            }

            List<SymptomOriginalItem> originalItems = items.stream()
                    .map(item -> SymptomOriginalItem.builder()
                            .roundNum(item.getRoundNum())
                            .originalText(item.getOriginalText())
                            .matchedTermId(matchedDict != null ? matchedDict.getId() : null)
                            .matchConfidence(confidence)
                            .build())
                    .toList();

            Long termKey = matchedDict != null ? matchedDict.getId() : -1L * cleanedText.hashCode();

            termOriginalMapping.computeIfAbsent(termKey, k -> new ArrayList<>()).addAll(originalItems);

            SymptomTermAggregator aggregator = aggregators.computeIfAbsent(
                    termKey, k -> new SymptomTermAggregator(matchedDict, matchSource));
            aggregator.appearCount += items.size();
            if (confidence != null) {
                for (int i = 0; i < items.size(); i++) {
                    aggregator.confidence.add(confidence);
                }
            }
            for (SymptomRawItem item : items) {
                if (item.getRoundNum() != null) {
                    aggregator.roundList.add(item.getRoundNum());
                }
            }
        }

        List<SymptomTerm> termList = aggregators.values().stream()
                .map(agg -> SymptomTerm.builder()
                        .symptomDict(agg.symptomDict)
                        .appearCount(agg.appearCount)
                        .roundList(agg.roundList.stream().distinct().sorted().collect(Collectors.toList()))
                        .matchSource(agg.matchSource)
                        .matchConfidence(
                                agg.confidence.stream()
                                        .max(BigDecimal::compareTo)
                                        .orElse(null)
                        )
                        .build()
                )
                .sorted(Comparator.comparing(SymptomTerm::getAppearCount).reversed())
                .collect(Collectors.toList());

        return SymptomNormalizeResult.builder()
                .termList(termList)
                .termOriginalMapping(termOriginalMapping)
                .build();
    }

    /**
     * 模型匹配结果条目
     * <p>
     * 携带匹配到的字典记录与模型返回的置信度，
     * 用于在 {@link #normalizeByModel} 与 {@link #aggregateAndBuildResult} 之间传递模型匹配信息
     *
     * @param dict       匹配到的标准术语字典记录
     * @param confidence 模型返回的语义匹配置信度，范围0-1
     */
    private record ModelMatchEntry(SymptomDict dict, BigDecimal confidence) {
    }

    /**
     * 症状术语聚合器
     * <p>
     * 用于在结果聚合阶段，将同一标准术语下的多条原文的统计信息进行累加汇总，
     * 最终转换为 {@link SymptomTerm} 输出
     */
    private static class SymptomTermAggregator {

        /**
         * 关联的标准术语字典记录，未匹配时为null
         */
        final SymptomDict symptomDict;

        /**
         * 该术语在全会话中的累计出现次数
         */
        int appearCount;

        /**
         * 该术语出现的所有对应轮次号列表（可能含重复，最终输出时去重排序）
         */
        final List<Integer> roundList;

        /**
         * 匹配来源
         *
         * @see SymptomTerm#MATCH_SOURCE_RULE
         * @see SymptomTerm#MATCH_SOURCE_MODEL
         * @see SymptomTerm#MATCH_SOURCE_RAW
         */
        int matchSource;

        /**
         * 匹配置信度，规则匹配默认1.0，模型匹配输出语义相似度分数（0-1）
         */
        List<BigDecimal> confidence;

        /**
         * @param symptomDict  关联的标准术语字典记录
         * @param matchSource  匹配来源
         */
        SymptomTermAggregator(SymptomDict symptomDict, int matchSource) {
            this.symptomDict = symptomDict;
            this.appearCount = 0;
            this.roundList = new ArrayList<>();
            this.matchSource = matchSource;
            this.confidence = new ArrayList<>();
        }
    }
}