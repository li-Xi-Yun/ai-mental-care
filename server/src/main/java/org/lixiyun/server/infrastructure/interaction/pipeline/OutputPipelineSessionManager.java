package org.lixiyun.server.infrastructure.interaction.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.OutputPipelineExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输出层会话管道管理器
 *
 * <p>纯容器——以 conversationId 为 key 持有 OutputPipeline 实例，
 * 提供注册、获取、中断、销毁的统一入口。</p>
 *
 * <h3>在整体流程中的位置：</h3>
 * <pre>
 * initSession(inputType, outputTypes)
 *     │
 *     ├── 输入层：inputSessionManager.register(...)
 *     │
 *     └── 输出层：outputPipelineSessionManager.initSession(convId, userId, outputTypes)
 *         ├── 先移除旧管道（如果存在）并销毁
 *         ├── pipelineFactory.createPipeline(outputTypes, convId, userId)
 *         ├── pipeline.init()  → 各节点 init()
 *         └── sessionPipelines.put(conversationId, pipeline)
 *
 * ... 返回 InitSessionResponse（含各端点路径，由 pipeline.getSocketInfo() 生成） ...
 *
 * ============ 时间轮触发 ============
 *
 * ConversationMessageProcessor.processConversationMessage(convId)
 *     │
 *     ├── 装配 OutputContext（从 DB/缓存 聚合数据到 dataStore）
 *     │
 *     └── outputPipelineSessionManager.getPipeline(convId)
 *         └── pipeline.execute(context)
 *
 * ============ 中断 ============
 *
 * interrupt(conversationId)
 *     ├── inputSessionManager.interrupt(conversationId)
 *     └── outputPipelineSessionManager.interrupt(conversationId)
 *
 * ============ 销毁 ============
 *
 * endSession(conversationId)
 *     ├── inputSessionManager.endSession(conversationId)
 *     └── outputPipelineSessionManager.endSession(conversationId)
 *         └── pipeline.destroy() → 逆序调用各节点 destroy()
 * </pre>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutputPipelineSessionManager {

    /**
     * conversationId → 该会话绑定的 OutputPipeline
     *
     * <p>一个会话只关联一个管道，管道内部包含有序的节点列表</p>
     */
    private final ConcurrentHashMap<Long, OutputPipeline> sessionPipelines = new ConcurrentHashMap<>();

    /**
     * conversationId → 管道是否正在执行
     *
     * <p>用于输入适配器判断是否需要中断当前输出管道。
     * 管道开始执行时标记为 true，执行完毕（正常/异常）时恢复为 false。
     * endSession 时自动清理。</p>
     */
    private final ConcurrentHashMap<Long, Boolean> pipelineActiveStates = new ConcurrentHashMap<>();

    /**
     * conversationId → 该会话绑定的输出模式集合
     *
     * <p>供流程编排器在 OutputContext 缺失输出模式时回填（时间轮等老触发路径）。
     * initSession 时写入，endSession 时清理。</p>
     */
    private final ConcurrentHashMap<Long, Set<OutputDataType>> sessionOutputTypes = new ConcurrentHashMap<>();

    private final OutputPipelineFactory pipelineFactory;

    /**
     * 初始化会话——组装管道、初始化节点资源、绑定到会话
     *
     * <p><b>并发安全</b>：使用 {@code ConcurrentHashMap.compute} 对同一会话的
     * 「销毁旧管道 → 创建新管道 → 初始化 → 绑定」整体加 per-key 互斥，
     * 防止同一会话并发 init 时旧管道资源泄漏或新管道被覆盖。</p>
     *
     * @param conversationId 会话ID（Map 的 key）
     * @param userId         用户ID
     * @param outputTypes    前端请求的输出模式集合
     */
    public void initSession(Long conversationId, Long userId, Set<OutputDataType> outputTypes) {
        sessionPipelines.compute(conversationId, (cid, old) -> {
            // 先销毁旧管道（如果存在）
            if (old != null) {
                log.info("[管道会话管理器] 会话{}存在旧管道，先销毁", cid);
                try {
                    old.destroy();
                } catch (Exception e) {
                    // 销毁失败不阻断重建，避免 compute 失败导致残留已销毁实例
                    log.error("[管道会话管理器] 会话{}旧管道销毁失败（继续重建），错误：{}", cid, e.getMessage(), e);
                }
            }

            // 1. Factory 组装节点链 → 产出 Pipeline
            OutputPipeline pipeline = pipelineFactory.createPipeline(outputTypes, cid, userId);

            // 2. 初始化各节点资源
            pipeline.init();

            // 3. sessionOutputTypes 在 compute 内一并更新，保证原子
            sessionOutputTypes.put(cid, outputTypes);
            log.info("[管道会话管理器] 会话{}初始化完成，outputTypes={}", cid, outputTypes);
            return pipeline;
        });
    }

    /**
     * 获取会话管道——供时间轮触发后执行
     *
     * @param conversationId 会话ID
     * @return 该会话绑定的 OutputPipeline
     * @throws BusinessException 如果会话管道不存在
     */
    public OutputPipeline getPipeline(Long conversationId) {
        OutputPipeline pipeline = sessionPipelines.get(conversationId);
        if (pipeline == null) {
            log.error("[管道会话管理器] 会话{}的管道不存在，无法获取", conversationId);
            throw new BusinessException(OutputPipelineExceptionEnum.PIPELINE_NOT_FOUND);
        }
        return pipeline;
    }

    /**
     * 中断会话管道——中断所有节点的当前操作（不销毁实例和连接）
     *
     * @param conversationId 会话ID
     */
    public void interrupt(Long conversationId) {
        OutputPipeline pipeline = sessionPipelines.get(conversationId);
        if (pipeline != null) {
            pipeline.interrupt();
            log.info("[管道会话管理器] 会话{}已中断", conversationId);
        }
    }

    /**
     * 销毁会话管道——销毁所有节点资源，从 Map 中移除
     *
     * @param conversationId 会话ID
     */
    public void endSession(Long conversationId) {
        OutputPipeline pipeline = sessionPipelines.remove(conversationId);
        pipelineActiveStates.remove(conversationId);
        sessionOutputTypes.remove(conversationId);
        if (pipeline != null) {
            pipeline.destroy();
            log.info("[管道会话管理器] 会话{}已销毁", conversationId);
        }
    }

    /**
     * 获取会话绑定的输出模式集合
     *
     * <p>供流程编排器在 OutputContext 缺失输出模式时回填（时间轮等老触发路径构造的 context 无 outputTypes）。
     * 会话未走 initSession 绑定管道时返回 null，由调用方兜底默认值。</p>
     *
     * @param conversationId 会话ID
     * @return 该会话绑定的输出模式集合；未绑定时返回 null
     */
    public Set<OutputDataType> getOutputTypes(Long conversationId) {
        return sessionOutputTypes.get(conversationId);
    }

    // ==================== 管道执行状态追踪 ====================

    /**
     * 判断指定会话的输出管道是否正在执行
     *
     * <p>供输入适配器（如 {@code AudioInputAdapter}）在接收新数据时调用，
     * 判断是否需要触发输出管道中断（barge-in）。</p>
     *
     * @param conversationId 会话ID
     * @return true 表示管道正在执行中，false 表示空闲或会话不存在
     */
    public boolean isActive(Long conversationId) {
        return Boolean.TRUE.equals(pipelineActiveStates.get(conversationId));
    }

    /**
     * 标记管道开始执行（由管道调用方在 execute 前调用）
     *
     * @param conversationId 会话ID
     */
    public void markActive(Long conversationId) {
        pipelineActiveStates.put(conversationId, true);
        log.debug("[管道会话管理器] 会话{}管道标记为活跃", conversationId);
    }

    /**
     * 标记管道执行完毕（由管道调用方在 execute 后 finally 中调用）
     *
     * @param conversationId 会话ID
     */
    public void markInactive(Long conversationId) {
        pipelineActiveStates.put(conversationId, false);
        log.debug("[管道会话管理器] 会话{}管道标记为非活跃", conversationId);
    }

    /**
     * 不带状态追踪的管道执行
     *
     * <p>纯粹的执行代理——获取管道并直接调用 execute，不维护 {@link #pipelineActiveStates}。
     * 适用于不需要中断检测的场景。</p>
     *
     * @param conversationId 会话ID
     * @param context        输出上下文
     * @throws BusinessException 如果会话管道不存在
     */
    public void execute(Long conversationId, OutputContext context) {
        Set<OutputDataType> outputTypes = getOutputTypes(conversationId);
        if (outputTypes == null || outputTypes.isEmpty()) {
            log.warn("[管道会话管理器] 会话{}未绑定输出模式，无法执行", conversationId);
            throw new BusinessException(OutputPipelineExceptionEnum.OUTPUT_TYPES_NOT_FOUND);
        }
        context.setOutputTypes(outputTypes);
        getPipeline(conversationId).execute(context);
    }

    /**
     * 带状态追踪的管道执行（推荐需要中断检测时使用）
     *
     * <p>自动在 execute 前后维护 {@link #pipelineActiveStates} 状态，
     * 确保正常返回和异常抛出时都能正确恢复状态。</p>
     *
     * @param conversationId 会话ID
     * @param context        输出上下文
     * @throws BusinessException 如果会话管道不存在
     */
    public void executeWithTracking(Long conversationId, OutputContext context) {
        // ============ todo：输出管道"活跃状态"生命周期与异步执行不匹配（barge-in 失效根因） ============
        //
        // 【问题现象】
        // 用户说话期间 AI 语音（TTS）仍在播放，此时用户再次开口（barge-in），期望立即中断当前输出
        // （停止模型流式推理 / TTS 下发），但实际上一段时间内 AI 仍在继续说话，中断不生效。
        // 本次运行日志中全程没有任何 "[管道会话管理器] 会话{}已中断" / "[TtsNode] 节点中断" 记录，
        // 证明 AudioInputAdapter.handleAudioFrame 中的 isActive 判断从未为 true。
        //
        // 【根因分析】
        // 1. 本方法在执行管道（pipeline.execute → 各节点 process）之前 markActive(true)，
        //    管道返回后立刻在 finally 中 markInactive(false)。
        // 2. 但 execute() 只是"同步建立"节点处理链路：ModelChatOutputNode.process() 会调用
        //    ConversationMessageProcessor 发起 LLM 流式推理 + TTS 合成，这些实际工作是通过
        //    Reactor / 线程池异步执行的。日志可见：主线程"执行完成，耗时：31ms"返回之后，
        //    数秒后模型才在 boundedElastic-N 线程上完成流式输出、TTS 音频随后才下发。
        // 3. 因此从 execute() 返回到"模型流式 + TTS 合成真正结束"之前的整段时间内，
        //    pipelineActiveStates 已经是 false。AudioInputAdapter.handleAudioFrame 的
        //    outputPipelineSessionManager.isActive(conversationId) 永远返回 false，
        //    永远不会触发 interrupt()（conversationStreamHolder.cancelStream + ttsConnectionManager.interrupt）。
        //
        // 【正确做法（待实现）】
        //   markInactive 不应在 execute() 返回时立即执行，而应延迟到"当前输出真正结束"再复位。可选实现：
        //   - 方案A：持有 execute 返回的 Flux 流，将流的终止信号（complete / error / cancel）与
        //     pipelineActiveStates 绑定——subscribe 回调中 markInactive；interrupt() 显式取消流并同步复位。
        //   - 方案B：在 ChatMessageProcessor 的 LLM 流式 onComplete / onError、以及
        //     TTS onSynthesisComplete / SessionFinished 回调中回调通知 markInactive。
        //   - 方案C：引入"输出会话"抽象（beginOutput/endOutput 配对），TTS 播放完成才 endOutput，
        //     使 isActive 覆盖"音频仍在下发"的整段时间。
        //
        // 【验证要点】
        //   修复后需一并验证 barge-in 全链路：VAD 检测到语音 → handleAudioFrame → isActive=true
        //   → interrupt() → 模型流取消 + TTS 中断；同时保证 destroy / endSession 清理路径不受影响，
        //   且不会出现"状态残留 true 导致后续输入被误中断"的反向问题。
        // ============================================================================================
        markActive(conversationId);
        try {
            execute(conversationId, context);
        } finally {
            markInactive(conversationId);
        }
    }

    /**
     * 构建客户端所需的 Socket 端点路径
     *
     * <p>客户端根据返回的路径，直接连接对应的 WebSocket 端点收发数据。
     * 由各节点通过 getSocketInfo() 自治声明其端点信息。</p>
     *
     * @param conversationId 会话ID
     * @return 该会话管道中所有节点的端点信息汇总列表，一定不为null，至少是一个空集合
     * @throws BusinessException 如果会话管道不存在
     */
    public List<NodeEndpoint> buildEndpointInfo(Long conversationId) {
        OutputPipeline pipeline = sessionPipelines.get(conversationId);
        if (pipeline == null) {
            log.error("[管道会话管理器] 会话{}的管道不存在，无法构建端点信息", conversationId);
            throw new BusinessException(OutputPipelineExceptionEnum.PIPELINE_NOT_FOUND);
        }
        return pipeline.getSocketInfo();
    }
}