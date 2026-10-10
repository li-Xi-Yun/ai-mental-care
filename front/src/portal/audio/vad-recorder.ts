/**
 * 持续语音采集器（VAD 自动断句）
 *
 * 与 temp 参考实现一致：
 * - 无 push-to-talk：start() 后一直采集，说话自动判定、静音自动结束一句；
 * - WebAudio ScriptProcessorNode(4096,1,1) 取帧，重采样到 16kHz Int16；
 * - RMS 能量阈值判定是否说话；说话结束后继续补发 N 帧全零静音（约 1.3s，覆盖服务端
 *   VAD 的 end_window_size=600ms），由服务端自动判停并返回 definite 结果；
 * - 后端没有 /audio/vad-stop 处理器（文档提及但代码不存在），因此不发送"负包"。
 */
import { downsampleTo16k, rms, zeroPcm } from "./pcm";

export interface VadFrameInfo {
  /** 该帧是否处于说话状态（true=用户语音，false=静音补帧/静音） */
  speaking: boolean;
  rms: number;
}

export interface VadRecorderOptions {
  /** 目标采样率（默认 16000） */
  targetSampleRate?: number;
  /** 采集帧大小（采样数，默认 4096） */
  frameSize?: number;
  /** RMS 能量阈值，高于视为说话（默认 0.012，同参考实现） */
  energyThreshold?: number;
  /** 说话结束后补发的静音帧数（默认 15，约 1.3s） */
  trailingSilenceFrames?: number;
  /** 每一帧回调（说话中的真实帧 + 结束后的静音补帧） */
  onFrame: (pcm16: Int16Array, info: VadFrameInfo) => void;
  /** 检测到用户开口（用于本地打断 TTS 播放） */
  onSpeechStart?: () => void;
  /** 一句说完（静音补帧已发足） */
  onSpeechEnd?: () => void;
  /** 音量 0-100（用于音量条） */
  onVolume?: (volume: number) => void;
  /** 采集/设备错误（采集过程中） */
  onError?: (err: unknown) => void;
}

export class VadRecorder {
  private readonly options: VadRecorderOptions;
  private readonly frameSize: number;
  private readonly sampleRate: number;
  private readonly energyThreshold: number;
  private readonly trailingSilenceFrames: number;

  private stream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private source: MediaStreamAudioSourceNode | null = null;
  private processor: ScriptProcessorNode | null = null;
  private sink: GainNode | null = null;

  private inSpeech = false;
  private silenceFrames = 0;
  /** 最近一帧 16kHz PCM 采样数（补发静音时复用） */
  private frameSamples = 0;

  constructor(options: VadRecorderOptions) {
    this.options = options;
    this.frameSize = options.frameSize ?? 4096;
    this.sampleRate = options.targetSampleRate ?? 16000;
    this.energyThreshold = options.energyThreshold ?? 0.012;
    this.trailingSilenceFrames = options.trailingSilenceFrames ?? 15;
  }

  get active(): boolean {
    return this.audioContext !== null;
  }

  get speaking(): boolean {
    return this.inSpeech;
  }

  /** 启动持续采集（需用户已授权麦克风；失败抛错由调用方给出友好提示） */
  async start(): Promise<void> {
    if (this.audioContext) return;
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    });
    this.stream = stream;

    const ctx = new AudioContext();
    this.audioContext = ctx;
    const source = ctx.createMediaStreamSource(stream);
    this.source = source;

    const processor = ctx.createScriptProcessor(this.frameSize, 1, 1);
    this.processor = processor;
    processor.onaudioprocess = this.handleAudioProcess;

    // 处理器需要接到 destination 才会持续触发；经零增益节点避免把麦克风声音放出来（防回声）
    const sink = ctx.createGain();
    sink.gain.value = 0;
    this.sink = sink;
    source.connect(processor);
    processor.connect(sink);
    sink.connect(ctx.destination);
  }

  private handleAudioProcess = (event: AudioProcessingEvent) => {
    const ctx = this.audioContext;
    if (!ctx) return;
    try {
      const input = event.inputBuffer.getChannelData(0);
      const pcm = downsampleTo16k(input, ctx.sampleRate);
      const energy = rms(input);
      this.frameSamples = pcm.length;
      this.options.onVolume?.(Math.min(100, Math.round(energy * 600)));

      if (energy >= this.energyThreshold) {
        this.silenceFrames = 0;
        if (!this.inSpeech) {
          this.inSpeech = true;
          this.options.onSpeechStart?.();
        }
        this.options.onFrame(pcm, { speaking: true, rms: energy });
      } else if (this.inSpeech) {
        this.silenceFrames += 1;
        if (this.silenceFrames <= this.trailingSilenceFrames) {
          // 补发全零静音帧，凑足服务端 VAD 判停窗口
          this.options.onFrame(zeroPcm(pcm.length), { speaking: false, rms: energy });
        }
        if (this.silenceFrames >= this.trailingSilenceFrames) {
          this.inSpeech = false;
          this.silenceFrames = 0;
          this.options.onSpeechEnd?.();
        }
      }
    } catch (err) {
      this.options.onError?.(err);
    }
  };

  /**
   * 停止采集。
   * @param options.trailingSilence 为 true 且当前处于说话中时，先补发 15 帧全零静音
   * （调用方必须确保此刻 STOMP 连接仍存活，让服务端完成最终识别）
   */
  async stop(options?: { trailingSilence?: boolean }): Promise<void> {
    if (options?.trailingSilence && this.inSpeech) {
      const samples = this.frameSamples || Math.round(this.sampleRate * 0.09);
      for (let i = 0; i < this.trailingSilenceFrames; i++) {
        this.options.onFrame(zeroPcm(samples), { speaking: false, rms: 0 });
      }
    }
    this.inSpeech = false;
    this.silenceFrames = 0;

    const processor = this.processor;
    if (processor) {
      processor.onaudioprocess = null;
      try {
        processor.disconnect();
      } catch {
        /* ignore */
      }
    }
    try {
      this.source?.disconnect();
    } catch {
      /* ignore */
    }
    try {
      this.sink?.disconnect();
    } catch {
      /* ignore */
    }
    if (this.stream) {
      for (const track of this.stream.getTracks()) track.stop();
    }
    const ctx = this.audioContext;
    this.processor = null;
    this.source = null;
    this.sink = null;
    this.stream = null;
    this.audioContext = null;
    if (ctx) {
      try {
        await ctx.close();
      } catch {
        /* ignore */
      }
    }
  }
}
