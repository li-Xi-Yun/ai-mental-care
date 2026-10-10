/**
 * PCM / Base64 / 重采样基础工具（纯函数，无副作用）
 *
 * 音频约定（与后端一致）：
 * - 上行 ASR：裸 PCM16LE 单声道 16kHz（base64 放入 STOMP JSON 的 audioMessage 字段）；
 * - 下行 TTS：裸 PCM16LE 单声道 24kHz（STOMP 二进制帧）。
 */

/** Float32 单样本 → Int16 */
function floatToInt16(sample: number): number {
  return sample < 0 ? sample * 0x8000 : sample * 0x7fff;
}

/** Float32 采样转 Int16 */
export function float32ToInt16(input: Float32Array): Int16Array {
  const out = new Int16Array(input.length);
  for (let i = 0; i < input.length; i++) out[i] = floatToInt16(input[i]);
  return out;
}

/** 线性抽点降采样到 16kHz（输入任意采样率单声道 Float32，输出 Int16） */
export function downsampleTo16k(input: Float32Array, inputRate: number): Int16Array {
  const ratio = Math.max(1, inputRate / 16000);
  if (ratio === 1) return float32ToInt16(input);
  const outLen = Math.floor(input.length / ratio);
  const out = new Int16Array(outLen);
  for (let i = 0; i < outLen; i++) {
    out[i] = floatToInt16(input[Math.floor(i * ratio)]);
  }
  return out;
}

/** 均方根能量（用于 VAD 说话判定与音量表） */
export function rms(input: Float32Array): number {
  if (!input.length) return 0;
  let energy = 0;
  for (let i = 0; i < input.length; i++) energy += input[i] * input[i];
  return Math.sqrt(energy / input.length);
}

/** 全零 PCM 帧（静音补帧用） */
export function zeroPcm(samples: number): Int16Array {
  return new Int16Array(samples);
}

/**
 * Int16 PCM → base64（小端字节序）。
 * 注意：分块拼接，禁止 String.fromCharCode(...bytes) 展开（大帧会爆栈）。
 */
export function pcm16ToBase64(pcm: Int16Array): string {
  const bytes = new Uint8Array(pcm.buffer, pcm.byteOffset, pcm.byteLength);
  const CHUNK = 8192;
  let binary = "";
  for (let i = 0; i < bytes.length; i += CHUNK) {
    const end = Math.min(i + CHUNK, bytes.length);
    let part = "";
    for (let j = i; j < end; j++) part += String.fromCharCode(bytes[j]);
    binary += part;
  }
  return btoa(binary);
}

/** 裸 PCM16LE 字节流 → Float32 归一化采样（显式按小端读取，不依赖平台字节序） */
export function int16leToFloat32(bytes: Uint8Array): Float32Array {
  const sampleCount = Math.floor(bytes.byteLength / 2);
  const out = new Float32Array(sampleCount);
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  for (let i = 0; i < sampleCount; i++) {
    out[i] = view.getInt16(i * 2, true) / 32768;
  }
  return out;
}
