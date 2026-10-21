package org.lixiyun.server.infrastructure.audio;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lixiyun.server.infrastructure.conversation.ConversationWebSocketManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * TTS音频预滚缓冲（后端缓存队列，让下行更平滑）
 *
 * <p><b>需求语义（预滚缓冲 / jitter buffer）</b>：
 * <ol>
 *   <li>前 N 个数据片段先入会话缓冲<b>不外发</b>（PREFILL 预滚期），给播放端制造领先量；</li>
 *   <li>当「片段数 ≥ 阈值 ‖ 累积字节 ≥ 阈值 ‖ 预滚超时」任一触发，立即把已攒片段
 *       <b>连续推给前端</b>并进入 STREAMING 流水期；</li>
 *   <li>STREAMING 期每来一片<b>立即转发一片</b>（稳态持续流出），直到合成结束/中断/销毁时 flush 残余。</li>
 * </ol>
 * 这样开头几片不外发，后续即使某一帧到晚了，前端仍有提前量可放，不断音。</p>
 *
 * <p><b>触发条件</b>（三个参数均由 yaml {@code tts.push-buffer.*} 提取，可调整）：
 * <ul>
 *   <li>{@code prefill-frames}：预滚片段数阈值（默认 3）</li>
 *   <li>{@code prefill-bytes}：预滚字节阈值（默认 18000 ≈ 0.375s @24kHz·16bit·mono，防单片段过大首字延迟失控）</li>
 *   <li>{@code prefill-timeout-ms}：预滚超时强制开播（默认 1000ms，防首字永远出不来）</li>
 * </ul>
 * 另有两个非参数触发：合成结束仍在预滚期 → 发掉已攒片段（尾部不丢）；节点销毁 → flush + 清理。</p>
 *
 * <p><b>线程安全</b>：同一会话所有 append 均来自 TTS 分发单线程，天然有序。
 * 临界区只做「计数 + 指针交换」（O(1)），实际 STOMP 发送在锁外执行；
 * 不同会话位于不同 {@code SessionBuffer} 对象上，互不竞争，无跨用户争用。</p>
 *
 * @author lixiyun
 * @since 2026-10-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TtsAudioPushBuffer {

    /** 预滚片段数阈值（默认3：攒够3片即开播） */
    @Value("${tts.push-buffer.prefill-frames:3}")
    private int prefillFrames;
    /** 预滚字节阈值（默认18000 ≈ 0.375s @24kHz·16bit·mono；防单片段过大时只攒1~2片首字延迟失控） */
    @Value("${tts.push-buffer.prefill-bytes:18000}")
    private int prefillBytes;
    /** 预滚超时（毫秒，默认1000：预滚期长时间无后续片段时强制开播） */
    @Value("${tts.push-buffer.prefill-timeout-ms:1000}")
    private long prefillTimeoutMs;

    private final ConversationWebSocketManager conversationWebSocketManager;

    /** 会话ID → 会话级预滚缓冲 */
    private final Map<Long, SessionBuffer> buffers = new ConcurrentHashMap<>();
    /** 会话ID → 预滚超时任务（强制开播用，每会话仅一个一次性延迟任务） */
    private final Map<Long, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    /** 预滚超时任务调度器（非周期冲洗，仅每会话一个延迟任务） */
    private ScheduledExecutorService timeoutScheduler;

    /** 会话级预滚缓冲状态机（PREFILL → STREAMING） */
    private static final class SessionBuffer {
        final Long userId;
        /** 预滚期积攒的片段（开播后置 null 释放） */
        List<byte[]> prefill = new ArrayList<>();
        /** 预滚期累积字节数 */
        int accumulatedBytes;
        /** 是否已开播（PREFILL → STREAMING） */
        boolean started;

        SessionBuffer(Long userId) {
            this.userId = userId;
        }
    }

    @PostConstruct
    void start() {
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tts-prefill-timeout");
            t.setDaemon(true);
            return t;
        });
        log.info("[TTS音频预滚缓冲] 启动：预滚片段阈值={}，预滚字节阈值={}，预滚超时={}ms",
                prefillFrames, prefillBytes, prefillTimeoutMs);
    }

    /**
     * 追加一段TTS音频
     * <p>预滚期入缓冲不外发；满足触发条件时连续推送已攒片段并进入流水期；
     * 流水期每来一片立即转发。</p>
     *
     * @param conversationId 会话ID
     * @param userId         所属用户ID（冲刷发送时使用）
     * @param audioData      音频分片（裸 PCM16LE）
     */
    public void append(Long conversationId, Long userId, byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return;
        }
        SessionBuffer buf = buffers.computeIfAbsent(conversationId, id -> new SessionBuffer(userId));

        List<byte[]> toSend = null;
        boolean prefillCompleted = false;
        synchronized (buf) {
            if (!buf.started) {
                // ===== PREFILL：只入缓冲，不外发 =====
                buf.prefill.add(audioData);
                buf.accumulatedBytes += audioData.length;
                if (shouldStartStreaming(buf)) {
                    // 开播：指针交换，锁外发送
                    toSend = buf.prefill;
                    buf.prefill = null;
                    buf.started = true;
                    prefillCompleted = true;
                }
            } else {
                // ===== STREAMING（含超时已开播）：进一片立即转发一片 =====
                toSend = List.of(audioData);
            }
        }

        if (toSend != null) {
            cancelTimeout(conversationId);
            sendAll(conversationId, buf.userId, toSend);
            if (prefillCompleted) {
                log.info("[TTS音频预滚缓冲] 会话{}预滚完成，开播推送{}段，共{}字节",
                        conversationId, toSend.size(), totalBytes(toSend));
            }
        } else {
            // 仍在预滚：安排一次性超时任务兜底强制开播
            scheduleTimeoutIfNeeded(conversationId);
        }
    }

    /** 是否满足开播条件：片段数 ≥ 阈值 ‖ 累积字节 ≥ 阈值 */
    private boolean shouldStartStreaming(SessionBuffer buf) {
        return buf.prefill.size() >= prefillFrames || buf.accumulatedBytes >= prefillBytes;
    }

    /**
     * 预滚超时兜底：超时仍不足阈值则强制开播（首字不永远卡在预滚期）。
     * <p>一次性延迟任务；回调内通过 {@link #buffers} 重取会话缓冲以读取权威状态
     * （此时会话可能已被 flush/remove 清空，不能依赖捕获的旧引用）。</p>
     */
    private void scheduleTimeoutIfNeeded(Long conversationId) {
        if (timeoutTasks.containsKey(conversationId)) {
            return;
        }
        ScheduledFuture<?> future = timeoutScheduler.schedule(() -> {
            timeoutTasks.remove(conversationId);
            SessionBuffer b = buffers.get(conversationId);
            if (b == null) {
                return;
            }
            List<byte[]> toSend = null;
            synchronized (b) {
                if (!b.started && !b.prefill.isEmpty()) {
                    toSend = b.prefill;
                    b.prefill = null;
                    b.started = true;
                }
            }
            if (toSend != null) {
                sendAll(conversationId, b.userId, toSend);
                log.info("[TTS音频预滚缓冲] 会话{}预滚超时（{}ms），强制开播推送{}段",
                        conversationId, prefillTimeoutMs, toSend.size());
            }
        }, prefillTimeoutMs, TimeUnit.MILLISECONDS);
        timeoutTasks.put(conversationId, future);
    }

    private void cancelTimeout(Long conversationId) {
        ScheduledFuture<?> future = timeoutTasks.remove(conversationId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 冲洗指定会话剩余音频（合成完成/中断时调用，保证尾部不丢）
     * <p>仍在预滚期 → 发掉已攒片段（合成提前结束条件）；流水期 → 预滚已空，通常无操作。</p>
     *
     * @param conversationId 会话ID
     */
    public void flush(Long conversationId) {
        SessionBuffer buf = buffers.get(conversationId);
        if (buf == null) {
            return;
        }
        cancelTimeout(conversationId);
        List<byte[]> toSend = null;
        synchronized (buf) {
            if (!buf.prefill.isEmpty()) {
                toSend = buf.prefill;
                buf.prefill = null;
                buf.started = true;
            }
        }
        if (toSend != null) {
            sendAll(conversationId, buf.userId, toSend);
            log.info("[TTS音频预滚缓冲] 会话{}合成结束，冲洗预滚残留{}段", conversationId, toSend.size());
        }
    }

    /**
     * 节点销毁：冲洗残余并移除会话缓冲（防内存泄漏）
     *
     * @param conversationId 会话ID
     */
    public void remove(Long conversationId) {
        cancelTimeout(conversationId);
        SessionBuffer buf = buffers.remove(conversationId);
        if (buf == null) {
            return;
        }
        List<byte[]> toSend = null;
        synchronized (buf) {
            if (!buf.prefill.isEmpty()) {
                toSend = buf.prefill;
                buf.prefill = null;
            }
        }
        if (toSend != null) {
            sendAll(conversationId, buf.userId, toSend);
            log.info("[TTS音频预滚缓冲] 会话{}销毁，冲洗预滚残留{}段", conversationId, toSend.size());
        }
    }

    /** 连续发送一批片段（STOMP 保序由 WebSocketConfig.setPreservePublishOrder(true) 保证） */
    private void sendAll(Long conversationId, Long userId, List<byte[]> segments) {
        for (byte[] seg : segments) {
            conversationWebSocketManager.sendAudioBinary(userId, conversationId, seg);
        }
    }

    private int totalBytes(List<byte[]> segments) {
        int total = 0;
        for (byte[] seg : segments) {
            total += seg.length;
        }
        return total;
    }

    @PreDestroy
    void shutdown() {
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdownNow();
        }
        buffers.clear();
        timeoutTasks.clear();
        log.info("[TTS音频预滚缓冲] 已关闭");
    }
}