package org.lixiyun.server.ai.rag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG文件解析线程仓库（静态内存存储），只适用于单机环境，多机环境请使用Redis存储
 * @author lixiyun
 * @since 2026-05-02 17:17
 */
public class RagFileThreadHolder {

    /**
     * 线程安全的Map：key=文件ID，value=正在执行的解析线程
     */
    private static final Map<Long, Thread> THREAD_MAP = new ConcurrentHashMap<>();

    /**
     * 保存线程
     */
    public static void put(Long fileId, Thread thread) {
        THREAD_MAP.put(fileId, thread);
    }

    /**
     * 获取线程
     */
    public static Thread get(Long fileId) {
        return THREAD_MAP.get(fileId);
    }

    /**
     * 移除线程
     */
    public static void remove(Long fileId) {
        THREAD_MAP.remove(fileId);
    }

    /**
     * 中断线程并移除
     */
    public static boolean interrupt(Long fileId) {
        Thread thread = get(fileId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            remove(fileId);
            return true;
        }
        return false;
    }
}