package org.lixiyun.server.socket.cache;

import reactor.core.Disposable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯手动管理的 Disposable 存储器
 * 无缓存、无过期、仅用普通ConcurrentHashMap存储
 */
public class TtsStreamHolder {

    // 最普通的线程安全Map，无任何缓存策略
    private static final ConcurrentHashMap<Long, Disposable> USER_TTS_DISPOSABLE = new ConcurrentHashMap<>();

    // 存储中断对象
    public static void put(Long userId, Disposable disposable) {
        USER_TTS_DISPOSABLE.put(userId, disposable);
    }

    // 手动中断TTS流
    public static void interrupt(Long userId) {
        Disposable disposable = USER_TTS_DISPOSABLE.get(userId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        // 中断后立即移除
        USER_TTS_DISPOSABLE.remove(userId);
    }

    // 清理（流正常结束/异常时调用）
    public static void clear(Long userId) {
        USER_TTS_DISPOSABLE.remove(userId);
    }

}