package org.lixiyun.server.infrastructure.audio;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.common.core.error.enums.ConversationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频数据缓存：按用户ID存储二进制音频块
 * @author lixiyun
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AudioCache {

    // 缓存结构：key=userId，value=用户音频上下文
    private static final Map<Long, AudioContext> AUDIO_CACHE = new ConcurrentHashMap<>();

    // 音频上下文：包含输出流 + 实时回调 + 超时清理
    private static class AudioContext {
        // 音频数据
        private final ByteArrayOutputStream baos;
        // 最后一次写入时间（用于定时任务进行超时清理）
        private long lastWriteTime;

        public AudioContext() {
            this.baos = new ByteArrayOutputStream();
            this.lastWriteTime = System.currentTimeMillis();
        }
    }

    /**
     * 初始化用户音频缓存（核心：指定实时回调函数）
     * @param userId 用户ID
     *
     * @return 缓存是否成功初始化
     */
    public static void initAudioCache(Long userId) {
        log.info("用户{}开始初始化音频缓存", userId);
        if (AUDIO_CACHE.containsKey(userId)) {
            log.warn("用户{}音频缓存已存在，忽略初始化", userId);
            throw new BusinessException(ConversationExceptionEnum.CANNOT_HAVE_MULTIPLE_VOICE_DIALOGS);
        }
        // 先清理旧缓存，防止上次数据还存在
        clearAudioCache(userId);
        // 初始化新上下文
        AUDIO_CACHE.put(userId, new AudioContext());
    }

    /**
     * 添加音频数据块（核心：实时回调推送）
     * @param userId 用户ID
     * @param data 音频数据块
     */
    public static void addAudioData(Long userId, byte[] data) {
        AudioContext context = AUDIO_CACHE.get(userId);
        if (context == null) {
            log.error("用户{}未初始化音频缓存，忽略音频块（音频数据长度：{}字节）", userId, data.length);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_NOT_INITIALIZED);
        }

        try {
            // 清空之前的数据，实现覆盖写入
            context.baos.reset();

            // 写入缓存
            context.baos.write(data);

            // 更新最后写入时间
            context.lastWriteTime = System.currentTimeMillis();

            log.debug("用户{}添加并推送音频块，长度：{}字节", userId, data.length);
        } catch (Exception e) {
            log.error("用户{}音频缓存写入/推送失败", userId, e);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_WRITE_FAILED);
        }
    }

    /**
     * 获取用户完整音频数据并清空缓存
     * @param userId 用户ID
     * @return 用户完整音频数据(一定不为null，但可能为空）
     */
    public static byte[] getAndClearAudioData(Long userId) {
        AudioContext context = AUDIO_CACHE.get(userId);
        if (context == null) {
            return new byte[0];
        }

        ByteArrayOutputStream baos = context.baos;
        byte[] audioData = baos.toByteArray();
        log.info("用户{}音频数据获取完成，总长度：{}字节", userId, audioData.length);

        // 刷新音频数据缓存
        baos.reset();
        return audioData;
    }

    /**
     * 清除用户音频缓存
     * @param userId 用户ID
     */
    public static void clearAudioCache(Long userId) {
        AudioContext context = AUDIO_CACHE.remove(userId);
        if (context != null) {
            closeBaos(context.baos, userId);
            log.info("用户{}音频缓存({}字节)已清除", userId, context.baos.size());
        } else {
            log.info("用户{}音频缓存已不存在", userId);
        }
    }

    // 关闭ByteArrayOutputStream
    private static void closeBaos(ByteArrayOutputStream baos, Long userId) {
        try {
            baos.close();
        } catch (Exception e) {
            log.error("用户{}音频缓存流关闭失败", userId, e);
            throw new BusinessException(ConversationExceptionEnum.AUDIO_CACHE_CLOSE_FAILED);
        }
    }

}