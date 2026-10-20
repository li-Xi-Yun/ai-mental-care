/**
 * 持续语音采集器（VAD 自动断句）
 *
 * 与 temp 参考实现一致：
 * - 无 push-to-talk：start() 后一直采集，说话自动判定、静音自动结束一句；
 * - WebAudio ScriptProcessorNode(4096,1,1) 取帧，重采样到 16kHz Int16；
 * - RMS 能量阈值判定是否说话（默认固定阈值 0.012；可选自适应：先采样环境噪声底，
 *   阈值 = clamp(噪声底×2.5, 下限, 0.02)）；
 *   说话结束后继续补发 N 帧全零静音（约 1.3s，覆盖服务端
 *   VAD 的 end_window_size=600ms），由服务端自动判停并返回 definite 结果；
 * - 后端没有 /audio/vad-stop 处理器（文档提及但代码不存在），因此不发送"负包"。
 * - 开口判定带连续帧门限（minSpeechFrames，约 256ms）：连续多帧越阈值才触发
 *   onSpeechStart / 本地 barge-in，避免单帧环境噪声/回声假阳性把在播 TTS 掐断；
 *   门限只延迟本地打断，所有说话帧照常上行，不影响 ASR。
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
  /**
   * RMS 能量阈值。默认固定阈值 0.012（10-12 验证可用行为）。
   * 开启自适应（adaptiveThreshold=true）时作为地板值。
   */
  energyThreshold?: number;
  /** 自适应噪声底阈值（默认 false，恢复固定阈值判定）：启动后先采样环境噪声，阈值 = clamp(噪声底×2.5, 下限, 0.02) */
  adaptiveThreshold?: boolean;
  /** 噪声采样时长（毫秒，默认 500） */
  calibrationMs?: number;
  /** 说话结束后补发的静音帧数（默认 15，约 1.3s） */
  trailingSilenceFrames?: number;
  /** 开口判定连续越阈值帧数（默认 3，约 256ms）：连续达标才触发 onSpeechStart/本地 barge-in，防单帧噪声假阳性 */
  minSpeechFrames?: number;
  /** 每一帧回调（说话中的真实帧 + 结束后的静音补帧） */
  onFrame: (pcm16: Int16Array, info: VadFrameInfo) => void;
  /** 检测到用户开口（用于本地打断 TTS 播放） */
  onSpeechStart?: () => void;
  /** 一句说完（静音补帧已发足） */
  onSpeechEnd?: () => void;
  /** 音量 0-100（用于音量条） */
  onVolume?: (volume: number) => void;
  /** 校准完成后的生效阈值（诊断用） */
  onThreshold?: (threshold: number) => void;
  /** 采集/设备错误（采集过程中） */
  onError?: (err: unknown) => void;
}

export class VadRecorder {
  private readonly options: VadRecorderOptions;
  private readonly frameSize: number;
  private readonly sampleRate: number;
  private readonly minThreshold: number;
  private readonly adaptive: boolean;
  private readonly calibrationMs: number;
  private readonly trailingSilenceFrames: number;
  private readonly minSpeechFrames: number;

  private stream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private source: MediaStreamAudioSourceNode | null = null;
  private processor: ScriptProcessorNode | null = null;
  private sink: GainNode | null = null;

  private inSpeech = false;
  private silenceFrames = 0;
  /** 连续越阈值帧数（用于开口判定门限） */
  private voicedFrames = 0;
  /** 最近一帧 16kHz PCM 采样数（补发静音时复用） */
  private frameSamples = 0;

  /* 自适应阈值：启动后采样环境噪声底，阈值 = clamp(噪声底×2.5, 下限, 0.02) */
  private threshold: number;
  private calibrating = false;
  private calibSamples: number[] = [];
  private calibElapsedMs = 0;

  constructor(options: VadRecorderOptions) {
    this.options = options;
    this.frameSize = options.frameSize ?? 4096;
    this.sampleRate = options.targetSampleRate ?? 16000;
    this.minThreshold = options.energyThreshold ?? 0.012;
    this.adaptive = options.adaptiveThreshold ?? false;
    this.calibrationMs = options.calibrationMs ?? 500;
    this.trailingSilenceFrames = options.trailingSilenceFrames ?? 15;
    this.minSpeechFrames = options.minSpeechFrames ?? 3;
    this.threshold = this.minThreshold;
  }

  /** 当前生效阈值（校准前为下限值） */
  get currentThreshold(): number {
    return this.threshold;
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

    // 重置自适应校准（每次启动重新采样环境噪声）
    this.calibrating = this.adaptive;
    this.calibSamples = [];
    this.calibElapsedMs = 0;
    this.voicedFrames = 0;
    this.threshold = this.minThreshold;

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

      // 校准期：只采样环境噪声底，不做起停判定（避免把环境噪声当成说话）
      if (this.calibrating) {
        this.calibElapsedMs += (pcm.length / this.sampleRate) * 1000;
        this.calibSamples.push(energy);
        if (this.calibElapsedMs >= this.calibrationMs) this.finishCalibration();
        return;
      }

      if (energy >= this.threshold) {
        this.silenceFrames = 0;
        this.voicedFrames += 1;
        if (!this.inSpeech && this.voicedFrames >= this.minSpeechFrames) {
          // 连续多帧越阈值才判定开口（防单帧环境噪声/回声假阳性触发 barge-in 掐断在播 TTS）
          this.inSpeech = true;
          this.options.onSpeechStart?.();
        }
        // 说话帧照常上行：门限只延迟本地 barge-in/状态切换，不影响 ASR
        this.options.onFrame(pcm, { speaking: this.inSpeech, rms: energy });
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
      } else {
        // 未入说话态：低能量帧清空连续计数（瞬时尖峰不累计）
        this.voicedFrames = 0;
      }
    } catch (err) {
      this.options.onError?.(err);
    }
  };

  /** 校准结束：取噪声样本 80 分位（抗瞬时尖峰）×2.5 作为阈值，并夹在 [下限, 0.02] 内 */
  private finishCalibration(): void {
    this.calibrating = false;
    const samples = this.calibSamples.slice().sort((a, b) => a - b);
    const noise = samples.length ? samples[Math.floor(samples.length * 0.8)] : 0;
    this.threshold = Math.min(0.02, Math.max(this.minThreshold, noise * 2.5));
    this.calibSamples = [];
    this.options.onThreshold?.(this.threshold);
  }

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
    this.voicedFrames = 0;
    this.calibrating = false;
    this.calibSamples = [];
    this.calibElapsedMs = 0;

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
