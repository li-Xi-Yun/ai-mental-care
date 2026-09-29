package com.speech.protocol;

// 音频信息类
public class AudioInfo {
    public final int sampleRate;
    public final int channels;
    public final int bitsPerSample;

    public AudioInfo(int sampleRate, int channels, int bitsPerSample) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
    }
}
