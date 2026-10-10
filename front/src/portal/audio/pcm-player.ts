/**
 * 流式 PCM 播放器（TTS 下行）
 *
 * - 下行帧为裸 PCM16LE 单声道 24kHz（STOMP 二进制帧），按序无缝排队播放（nextPlayTime 时间轴）；
 * - bargeIn()：用户开口时本地立即停止全部在播/排队音频（服务端 barge-in 为最终权威，
 *   前端只保证"开口即静音"）；
 * - dispose() 后 enqueue 变为 no-op（避免离开页面后仍收到迟到帧）。
 */
import { int16leToFloat32 } from "./pcm";

export interface PcmPlayerOptions {
  /** PCM 采样率（默认 24000，与后端 TTS 配置一致） */
  sampleRate?: number;
}

type AudioContextCtor = typeof AudioContext;

function getAudioContextCtor(): AudioContextCtor {
  const w = window as unknown as { AudioContext?: AudioContextCtor; webkitAudioContext?: AudioContextCtor };
  return w.AudioContext ?? w.webkitAudioContext ?? (window as unknown as AudioContextCtor);
}

/** 队列积压上限（秒）：超过视为异常（如长时间未消费），重置时间轴避免越播越迟 */
const MAX_BACKLOG_SECONDS = 3;

export class PcmStreamPlayer {
  private readonly sampleRate: number;
  private ctx: AudioContext | null = null;
  private nextPlayTime = 0;
  private readonly activeSources = new Set<AudioBufferSourceNode>();
  private disposed = false;

  constructor(options: PcmPlayerOptions = {}) {
    this.sampleRate = options.sampleRate ?? 24000;
  }

  /** AudioContext 状态（uninitialized=尚未创建） */
  get contextState(): AudioContextState | "uninitialized" {
    return this.ctx ? this.ctx.state : "uninitialized";
  }

  /** 当前在播/排队的音源数量（可用于状态展示） */
  get activeSourceCount(): number {
    return this.activeSources.size;
  }

  /** 创建并恢复 AudioContext；返回 false 表示挂起（需用户手势，如"点击继续"遮罩） */
  async ensureReady(): Promise<boolean> {
    if (this.disposed) return false;
    if (!this.ctx) {
      const Ctor = getAudioContextCtor();
      this.ctx = new Ctor();
    }
    if (this.ctx.state === "suspended") {
      try {
        await this.ctx.resume();
      } catch {
        /* 忽略：由调用方兜底提示 */
      }
    }
    return this.ctx.state === "running";
  }

  /** 入队一段 PCM16LE 字节流（≤ 若干 KB/帧），无缝隙排队播放 */
  enqueue(bytes: Uint8Array): void {
    if (this.disposed || !this.ctx || bytes.byteLength < 2) return;
    const ctx = this.ctx;
    if (ctx.state === "suspended") {
      void ctx.resume().catch(() => {});
    }
    const floats = int16leToFloat32(bytes);
    if (!floats.length) return;

    // buffer 固定 24kHz，由 AudioBufferSourceNode 自行重采样到设备采样率
    const buffer = ctx.createBuffer(1, floats.length, this.sampleRate);
    buffer.getChannelData(0).set(floats);

    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.connect(ctx.destination);

    const now = ctx.currentTime;
    if (this.nextPlayTime < now) this.nextPlayTime = now;
    if (this.nextPlayTime - now > MAX_BACKLOG_SECONDS) this.nextPlayTime = now;
    source.start(this.nextPlayTime);
    this.nextPlayTime += buffer.duration;

    this.activeSources.add(source);
    source.onended = () => {
      this.activeSources.delete(source);
      try {
        source.disconnect();
      } catch {
        /* ignore */
      }
    };
  }

  /** 本地打断：立即停止全部在播/排队音频并重置时间轴 */
  bargeIn(): void {
    for (const source of this.activeSources) {
      try {
        source.stop();
      } catch {
        /* 已自然结束的音源忽略 */
      }
      try {
        source.disconnect();
      } catch {
        /* ignore */
      }
    }
    this.activeSources.clear();
    if (this.ctx) this.nextPlayTime = this.ctx.currentTime;
  }

  /** 释放：打断 + 关闭 AudioContext（可重复调用） */
  async dispose(): Promise<void> {
    this.disposed = true;
    this.bargeIn();
    const ctx = this.ctx;
    this.ctx = null;
    if (ctx) {
      try {
        await ctx.close();
      } catch {
        /* ignore */
      }
    }
  }
}
