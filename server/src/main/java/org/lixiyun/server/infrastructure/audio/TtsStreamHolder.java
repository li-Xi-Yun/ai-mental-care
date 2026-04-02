package org.lixiyun.server.infrastructure.audio;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯手动管理的 Disposable 存储器
 * 无缓存、无过期、仅用普通ConcurrentHashMap存储
 */
public class TtsStreamHolder {

    // 最普通的线程安全Map，无任何缓存策略
    private static final ConcurrentHashMap<Long, List<Object>> USER_TTS_DISPOSABLE = new ConcurrentHashMap<>();

    // 存储中断对象
    public static void put(Long userId, Object object) {
        List<Object> list = USER_TTS_DISPOSABLE.get(userId);
        if (list == null) {
            ArrayList<Object> disposables = new ArrayList<>();
            disposables.add(object);
            USER_TTS_DISPOSABLE.put(userId, disposables);
        } else {
            list.add(object);
        }
    }

    // 手动中断TTS流
    public static void interrupt(Long userId) {
        List<Object> list = USER_TTS_DISPOSABLE.get(userId);
        if(list != null && !list.isEmpty()){
            for (Object object : list) {
                if (object != null) {
                    if(object instanceof Disposable disposable){
                        disposable.dispose();
                    } else if(object instanceof SpeechSynthesizer synthesizer){
                        synthesizer.streamingCancel();
                    }
                }
            }
        }
        // 中断后立即移除
        USER_TTS_DISPOSABLE.remove(userId);
    }

    // 清理（流正常结束/异常时调用）
    public static void clear(Long userId) {
        USER_TTS_DISPOSABLE.remove(userId);
    }

}