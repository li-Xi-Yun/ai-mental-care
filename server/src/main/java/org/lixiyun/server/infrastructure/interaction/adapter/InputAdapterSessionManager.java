package org.lixiyun.server.infrastructure.interaction.adapter;

import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.InputAdapterExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.server.infrastructure.interaction.NodeEndpoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输入层会话适配器管理器
 *
 * <p>输入层唯一编排入口——负责调用 InputAdapterFactory 组装适配器、统一初始化、收集管理。
 * 对外提供注册、按类型查找、中断、销毁等生命周期操作。</p>
 *
 * <h3>在整体流程中的位置：</h3>
 * <pre>{@code
 * initSession(inputTypes, outputType)
 *     │
 *     ├── 输入层（SessionManager 统一组装和初始化）：
 *     │   ├── sessionManager.register(inputTypes, userId, cid)
 *     │   │   ├── factory.assemble(inputTypes)     ← 工厂根据 InputDataType 查找适配器
 *     │   │   └── adapter.init(userId, cid)         ← 统一初始化
 *     │   └── (后续可通过 getInputAdapter(cid, type) 按类型获取)
 *     │
 *     └── 输出层：outputPipelineSessionManager.initSession(...)
 *
 * endSession(conversationId)
 *     ├── sessionManager.endSession(conversationId)
 *     │   └── sessionAdapters.remove(cid) → 遍历 destroy()
 *     └── outputSessionManager.endSession(conversationId)
 * }</pre>
 *
 * @author lixiyun
 * @since 2026-10-02
 */
@Slf4j
@Component
public class InputAdapterSessionManager {

    private final InputAdapterFactory factory;

    /**
     * conversationId → 该会话持有的所有 Adapter 列表
     *
     * <p>支持单会话多 Adapter 组合（例如：音频输入 + 视频输入 同时启用）</p>
     */
    private final ConcurrentHashMap<Long, List<InputAdapter>> sessionAdapters = new ConcurrentHashMap<>();

    public InputAdapterSessionManager(InputAdapterFactory factory) {
        this.factory = factory;
    }

    /**
     * 根据会话ID与输入数据类型，获取会话下对应的输入适配器
     *
     * @param conversationId 会话唯一标识
     * @param type           输入数据类型
     * @return 匹配类型的输入适配器，如果该类型不存在则返回 null
     * @throws BusinessException 如果会话不存在
     */
    public InputAdapter getInputAdapter(Long conversationId, InputDataType type) {
        List<InputAdapter> adapterList = sessionAdapters.get(conversationId);
        if (adapterList == null) {
            log.error("[输入适配器管理器] 会话{}不存在，无法获取适配器", conversationId);
            throw new BusinessException(InputAdapterExceptionEnum.SESSION_NOT_FOUND);
        }
        return adapterList.stream()
                .filter(item -> item.getSupportedType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * 注册适配器——工厂组装 + 统一初始化 + 纳入会话管理
     *
     * <p><b>并发安全</b>：使用 {@code ConcurrentHashMap.compute} 对同一会话的
     * 「销毁旧适配器 → 组装新适配器 → 初始化 → 绑定」整体加 per-key 互斥，
     * 防止同一会话并发注册时旧适配器资源泄漏或新适配器被覆盖。</p>
     *
     * @param inputTypes     客户端请求的输入方式集合
     * @param userId         用户ID
     * @param conversationId 会话ID
     */
    public void register(Set<InputDataType> inputTypes, Long userId, Long conversationId) {
        sessionAdapters.compute(conversationId, (cid, oldList) -> {
            // 会话已注册：先销毁旧适配器资源，再按新输入类型重建
            if (oldList != null) {
                log.info("[输入适配器管理器] 会话{}已被注册，先销毁旧适配器资源", cid);
                for (int i = oldList.size() - 1; i >= 0; i--) {
                    try {
                        oldList.get(i).destroy();
                    } catch (Exception e) {
                        // 销毁失败不阻断重建，避免 compute 失败导致残留已销毁实例
                        log.error("[输入适配器管理器] 会话{}旧适配器销毁失败（继续重建），错误：{}", cid, e.getMessage(), e);
                    }
                }
            }

            List<InputAdapter> adapterList = factory.assemble(inputTypes);
            adapterList.forEach(item -> item.init(userId, cid));
            log.info("[输入适配器管理器] 会话{}注册完成，输入类型：{}，适配器数量：{}",
                    cid, inputTypes, adapterList.size());
            return adapterList;
        });
    }

    /**
     * 销毁会话——取出所有 Adapter 并逆序销毁，同时移除会话记录
     * <p>会话身份由各适配器 init() 时写入的实例字段提供，无需再传参。</p>
     *
     * @param conversationId 会话ID
     */
    public void endSession(Long conversationId) {
        List<InputAdapter> adapters = sessionAdapters.remove(conversationId);
        if (adapters != null) {
            for (int i = adapters.size() - 1; i >= 0; i--) {
                adapters.get(i).destroy();
            }
            log.info("[输入适配器管理器] 会话{}已销毁，释放{}个适配器资源",
                    conversationId, adapters.size());
        }
    }

    /**
     * 中断会话——中断所有 Adapter 的当前操作（不销毁资源与连接）
     * <p>会话身份由各适配器 init() 时写入的实例字段提供，无需再传参。</p>
     *
     * @param conversationId 会话ID
     */
    public void interrupt(Long conversationId) {
        List<InputAdapter> adapters = sessionAdapters.get(conversationId);
        if (adapters != null) {
            adapters.forEach(InputAdapter::interrupt);
            log.info("[输入适配器管理器] 会话{}已中断", conversationId);
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
        List<InputAdapter> adapters = sessionAdapters.get(conversationId);
        if (adapters == null) {
            log.error("[输入适配器管理器] 会话{}不存在，无法构建端点信息", conversationId);
            throw new BusinessException(InputAdapterExceptionEnum.SESSION_NOT_FOUND);
        }
        List<NodeEndpoint> list = new ArrayList<>();
        for (InputAdapter adapter : adapters) {
            list.addAll(adapter.getSocketInfo());
        }
        return list;
    }
}