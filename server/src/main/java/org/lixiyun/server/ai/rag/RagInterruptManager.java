package org.lixiyun.server.ai.rag;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.redis.utils.RedisUtils;

import java.util.concurrent.TimeUnit;

/**
 * RAG文件解析中断标识管理器（基于Redis）
 * <p>
 * 使用Redis存储文件解析任务的中断标识，支持分布式环境。
 * 当用户请求中断时，在Redis中设置中断标志，工作线程定期检查该标志。
 * 
 * <p><b>使用场景：</b></p>
 * <ul>
 *     <li>文件向量加载任务的中断控制</li>
 *     <li>长时间运行的RAG处理任务取消</li>
 *     <li>分布式环境下的任务协调</li>
 * </ul>
 *
 * @author lixiyun
 * @since 2026-05-07
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RagInterruptManager {

    /**
     * Redis Key前缀：RAG文件解析中断标识
     * <p>完整Key格式：rag:interrupt:file:{fileId}</p>
     */
    private static final String RAG_INTERRUPT_KEY_PREFIX = "rag:interrupt:file:";

    /**
     * 中断标识的过期时间（秒）
     * <p>
     * 设置为30分钟，确保：
     * <ul>
     *     <li>足够长的时间覆盖正常的文件解析流程</li>
     *     <li>避免Redis中积累过多的过期键</li>
     *     <li>即使任务异常结束，标识也会自动清理</li>
     * </ul>
     */
    private static final long INTERRUPT_EXPIRE_SECONDS = 1800L;

    /**
     * 中断标识的值（固定值，仅用于判断key是否存在）
     */
    private static final String INTERRUPT_FLAG_VALUE = "1";

    /**
     * 设置文件解析任务的中断标识
     * <p>
     * 当用户请求中断时调用此方法，在Redis中设置中断标志。
     * 工作线程会定期检查该标志，发现后停止处理。
     *
     * @param fileId 文件ID
     * @return 是否成功设置中断标识
     */
    public static boolean setInterruptFlag(Long fileId) {
        if (fileId == null) {
            log.warn("设置中断标识失败：文件ID为空");
            return false;
        }

        try {
            String key = buildInterruptKey(fileId);
            // 设置中断标识，并指定过期时间
            RedisUtils.setCacheObject(key, INTERRUPT_FLAG_VALUE, INTERRUPT_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("设置文件解析中断标识成功，文件ID：{}，过期时间：{}秒", fileId, INTERRUPT_EXPIRE_SECONDS);
            return true;
        } catch (Exception e) {
            log.error("设置文件解析中断标识失败，文件ID：{}", fileId, e);
            return false;
        }
    }

    /**
     * 检查文件解析任务是否被中断
     * <p>
     * 工作线程在处理过程中定期调用此方法，检查是否有中断请求。
     * 如果Redis中存在对应的中断标识，说明用户已请求中断。
     *
     * @param fileId 文件ID
     * @return true-已被中断，false-未被中断
     */
    public static boolean isInterrupted(Long fileId) {
        if (fileId == null) {
            return false;
        }

        try {
            String key = buildInterruptKey(fileId);
            String flag = RedisUtils.getCacheObject(key);
            boolean interrupted = INTERRUPT_FLAG_VALUE.equals(flag);
            
            if (interrupted) {
                log.debug("检测到文件解析中断标识，文件ID：{}", fileId);
            }
            
            return interrupted;
        } catch (Exception e) {
            log.error("检查中断标识失败，文件ID：{}", fileId, e);
            // 发生异常时，为了安全起见，不认为被中断
            return false;
        }
    }

    /**
     * 清除文件解析任务的中断标识
     * <p>
     * 在以下场景调用：
     * <ul>
     *     <li>任务正常完成后</li>
     *     <li>任务被中断并完成清理后</li>
     *     <li>任务失败后</li>
     * </ul>
     * 虽然设置了过期时间，但主动清理可以减少Redis中的键数量。
     *
     * @param fileId 文件ID
     * @return 是否成功清除
     */
    public static boolean clearInterruptFlag(Long fileId) {
        if (fileId == null) {
            log.warn("清除中断标识失败：文件ID为空");
            return false;
        }

        try {
            String key = buildInterruptKey(fileId);
            boolean deleted = RedisUtils.deleteObject(key);
            if (deleted) {
                log.debug("清除文件解析中断标识成功，文件ID：{}", fileId);
            }
            return deleted;
        } catch (Exception e) {
            log.error("清除文件解析中断标识失败，文件ID：{}", fileId, e);
            return false;
        }
    }

    /**
     * 构建Redis Key
     *
     * @param fileId 文件ID
     * @return Redis Key字符串
     */
    private static String buildInterruptKey(Long fileId) {
        return RAG_INTERRUPT_KEY_PREFIX + fileId;
    }
}
