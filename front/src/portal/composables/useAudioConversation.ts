/**
 * 语音对话编排（独立语音页专用）
 *
 * 进入页面：建立实时通道 → 按需释放旧会话资源（从文本页进入时）→ 拉取历史 → 准备播放/采集，
 * 但**不初始化**会话生命周期；用户产生输入（语音开口 / 文本发送）时才按所选输入输出模式 init。
 * 上行：16k PCM 帧（VAD 自动断句 + 静音补帧，初始化完成前先缓冲）；下行：24k PCM 流式播放；
 * 用户开口时本地立即打断 TTS（barge-in）；AI 文本空闲 2s 后与 memory 对账。
 */
import { ref } from "vue";
import dayjs, { FORMAT_DATETIME } from "@/shared/utils/dayjs";
import { API_BASE_URL } from "@/shared/api/config";
import { getAuthHeaders } from "@/shared/api/auth";
import { fetchRoundMemory, parsePendingActionPush } from "@/portal/api/conversation/dialogue";
import {
  initConversation,
  isLocalConversationId,
  releaseConversation,
  sendTextWithLifecycle,
  type LifecycleModes,
} from "@/portal/composables/useConversationLifecycle";
import { useStompConversation } from "@/portal/composables/useStompConversation";
import { showQuotaRejected } from "@/portal/composables/useQuotaRejected";
import { useConversationStore } from "@/portal/stores/conversation";
import { useUserStore } from "@/portal/stores/user";
import { PcmStreamPlayer } from "@/portal/audio/pcm-player";
import { VadRecorder } from "@/portal/audio/vad-recorder";
import { pcm16ToBase64 } from "@/portal/audio/pcm";
import type { MemoryMessage, PendingActionItem } from "@/portal/api/conversation/types";
import type { ConversationStreamHandlers } from "@shared/ws/stomp-client";

/** 语音页阶段 */
export type AudioPhase =
  | "idle" // 未登录/未开始
  | "connecting" // 建立实时通道
  | "initializing" // 生命周期初始化（首次输入触发）
  | "ready" // 就绪，等待用户输入
  | "requesting-mic" // 申请麦克风
  | "listening" // 持续聆听
  | "speech" // 用户说话/识别中
  | "replying" // AI 回复中
  | "error" // 失败（可重试）
  | "expired"; // 空闲超时已自动退出（终止态）

/** AI 文本空闲判定（最后一个 chunk 后超过该时长视为回复结束） */
const AI_IDLE_MS = 2000;
/** memory 对账重试间隔与最大次数 */
const RECONCILE_RETRY_MS = 800;
const RECONCILE_MAX_RETRY = 3;
/** barge-in 后丢弃在途 TTS 帧的窗口（服务端已合成的音频可能在通道中） */
const BARGE_IN_DISCARD_MS = 1200;
/** 离开页面的清理超时（避免卡住路由导航） */
const TEARDOWN_TIMEOUT_MS = 1500;
/** 初始化完成前缓冲的帧上限（约 15 秒；4096/16kHz ≈ 256ms/帧，超出丢最旧） */
const MAX_BUFFER_FRAMES = 60;
/** 发送/说话后无首包的兜底时长 */
const NO_CHUNK_FALLBACK_MS = 10000;
/** 说话结束后的「无识别结果」看门狗时长 */
const WATCHDOG_TIMEOUT_MS = 10000;
/** 轻提示展示时长 */
const NOTICE_DURATION_MS = 6000;
/** 短重试耗尽后的最终对账延迟（覆盖"流式只推了一半、落库较晚"的场景） */
const FINAL_RECONCILE_DELAY_MS = 5000;
/** 会话空闲超时（毫秒）：无任何消息上行/下行持续超过该时长 → 提示并返回文本对话页 */
const IDLE_TIMEOUT_MS = 5 * 60 * 1000;

export interface UseAudioConversationOptions {
  /** URL query 中的会话 ID（空则新建） */
  initialConversationId: string;
  /** 输入类型（AUDIO / TEXT） */
  inputTypes: string[];
  /** 输出类型（TEXT / TEXT+AUDIO / +AVATAR_2D） */
  outputTypes: string[];
  /** 从文本页进入：进入时释放旧的 TEXT 生命周期（由本页唯一执行，避免双 DELETE 竞态） */
  releaseOnBoot: boolean;
  /** 生命周期 init 返回真实会话 ID 后回调（用于回写 URL） */
  onConversationIdChange?: (id: string) => void;
  /** 虚拟形象表情指令（原始 JSON 字符串，输出模式含 AVATAR_2D 时订阅并透传；由页面转发给渲染组件） */
  onAvatar2dExpression?: (rawJson: string) => void;
  /** 虚拟形象动作指令（原始 JSON 字符串，输出模式含 AVATAR_2D 时订阅并透传；由页面转发给渲染组件） */
  onAvatar2dMotion?: (rawJson: string) => void;
  /** 会话空闲超时回调：生命周期已释放（前端定时器或后端 SESSION_TIMEOUT 事件触发），由页面提示并返回文本对话页 */
  onIdleTimeout?: () => void;
}

export function useAudioConversation(options: UseAudioConversationOptions) {
  const userStore = useUserStore();
  const conversationStore = useConversationStore();

  /** 当前输入/输出模式（可在语音页内动态修改） */
  let currentModes: LifecycleModes = {
    inputTypes: [...options.inputTypes],
    outputTypes: [...options.outputTypes],
  };
  /** 是否启用语音输入（响应式：模板按此显示音量条/文本输入行） */
  const isAudioInput = ref(currentModes.inputTypes.includes("AUDIO"));

  /* ==================== 状态 ==================== */
  const phase = ref<AudioPhase>("idle");
  const errorText = ref("");
  /** AudioContext 被浏览器挂起，需要一次用户点击继续 */
  const needResume = ref(false);
  const volume = ref(0);
  const messages = ref<MemoryMessage[]>([]);
  const pendingActions = ref<PendingActionItem[]>([]);
  /** 本轮用户文本（ASR 中间结果渐进更新 / 文本输入直接设置） */
  const userText = ref("");
  /** 本轮用户文本是否已定型（ASR 结束或文本发送） */
  const userTextFinal = ref(false);
  /** 本轮 AI 回复（流式渐进追加；落库后用权威文本回填） */
  const aiStream = ref("");
  /** 轻提示（非阻塞，如 ASR 超时 / 通道断开），自动消失 */
  const notice = ref("");
  const conversationId = ref(options.initialConversationId || "");
  const conversationName = ref("");

  let booting = false;
  let tornDown = false;
  /** teardown 重入保护：tornDown 需等尾帧补发后才置位，期间禁止二次进入清理 */
  let teardownStarted = false;
  let aiIdleTimer: number | null = null;
  let reconcileTimer: number | null = null;
  let noChunkTimer: number | null = null;
  let watchdogTimer: number | null = null;
  let noticeTimer: number | null = null;
  let reconcileAttempts = 0;
  /** 是否已安排"最终对账"（避免重复安排） */
  let finalReconcileScheduled = false;
  let baselineAssistantCount = 0;
  let discardAudioUntil = 0;
  /** 已完成 init 且已订阅的会话 id（首次输入前为空） */
  let activeConversationId = "";
  let activePromise: Promise<string> | null = null;
  /** 初始化前的帧缓冲（仅说话期帧 + 静音尾帧） */
  const frameBuffer: string[] = [];
  /** 缓冲冲刷中：期间新帧继续入队，保证上行顺序 */
  let flushing = false;
  /** 发布失败重连限频时间戳 */
  let publishRetryAt = 0;
  /** 会话空闲超时倒计时（无上行/下行活动到期后释放并返回文本页） */
  let idleTimer: number | null = null;

  /* ==================== 音频 ==================== */
  const playerPlaying = ref(false);
  // let：超时自动退出后「重新进入」需换掉已 dispose 的播放器；所有闭包捕获此绑定，替换后自动指向新实例
  let player = new PcmStreamPlayer({
    sampleRate: 24000,
    onPlaybackStateChange: (playing) => {
      playerPlaying.value = playing;
    },
  });

  const stomp = useStompConversation({
    onTransportClose: () => handleTransportError("与服务器的连接已断开"),
    onStompError: (detail) => handleTransportError(detail || "实时通道异常"),
  });

  const recorder = new VadRecorder({
    onFrame: (pcm) => {
      if (tornDown) return;
      const base64 = pcm16ToBase64(pcm);
      if (!activeConversationId || flushing) {
        // 尚未初始化或正在冲刷历史缓冲：先入队，保证上行顺序（避免丢用户第一句）
        frameBuffer.push(base64);
        if (frameBuffer.length > MAX_BUFFER_FRAMES) frameBuffer.shift();
        return;
      }
      publishFrame(base64);
    },
    onSpeechStart: () => {
      // 用户开口：新一轮开始 → 清空上一轮展示文本；本地立即打断 TTS（服务端 barge-in 为最终权威）
      player.bargeIn();
      discardAudioUntil = performance.now() + BARGE_IN_DISCARD_MS;
      userText.value = "";
      userTextFinal.value = false;
      aiStream.value = "";
      finalReconcileScheduled = false;
      clearReconcileTimer();
      reconcileAttempts = 0;
      if (!activeConversationId) {
        // 首次输入才初始化（幂等重建）；缓冲期间不显示"识别中"
        phase.value = "initializing";
        void ensureActive()
          .then(() => {
            void flushFrameBuffer();
            phase.value = "speech";
          })
          .catch((e) => setError(micErrorMessage(e) || (e instanceof Error ? e.message : "会话初始化失败，请重试")));
        return;
      }
      phase.value = "speech";
    },
    onSpeechEnd: () => {
      if (phase.value === "speech") phase.value = aiStream.value ? "replying" : "listening";
      // 说话结束但迟迟没有任何识别/回复 → 提示（不再静默无反应）
      startWatchdog();
    },
    onVolume: (v) => {
      volume.value = v;
    },
    onThreshold: (threshold) => {
      console.warn(`[audio] VAD 自适应阈值=${threshold.toFixed(4)}`);
    },
    onError: (err) => {
      console.warn("[audio] 采集过程出错", err);
    },
  });

  /** 发送单帧；失败时限频重连并提示（原先静默丢弃） */
  function publishFrame(base64: string): void {
    const id = activeConversationId;
    if (!id) return;
    if (!stomp.publishAudioFrame(base64, id)) handlePublishFailure();
    // 说话上行即活跃：重置空闲超时倒计时
    resetIdleTimer();
  }

  /** 发布失败（通道未连接）：限频重连 + 轻提示；重连失败则进入错误态 */
  function handlePublishFailure(): void {
    const now = performance.now();
    if (now - publishRetryAt < 3000) return;
    publishRetryAt = now;
    console.warn("[audio] 音频帧发送失败：实时通道未连接，尝试重连");
    setNotice("实时通道已断开，正在重连…");
    void stomp.connect(8000).then((reconnected) => {
      if (!reconnected) setError("实时通道连接失败，请点击重试");
    });
  }

  /** 有序冲刷初始化前缓冲的帧（约 1 帧/80ms，降低服务端首帧竞态窗口；期间新帧继续入队） */
  async function flushFrameBuffer(): Promise<void> {
    if (flushing || !activeConversationId) return;
    flushing = true;
    try {
      let sent = 0;
      while (frameBuffer.length) {
        const frame = frameBuffer.shift();
        if (!frame) continue;
        const ok = stomp.publishAudioFrame(frame, activeConversationId);
        if (!ok) {
          handlePublishFailure();
          break;
        }
        sent += 1;
        if (frameBuffer.length) await sleep(80);
      }
      if (sent) console.warn(`[audio] 已冲刷缓冲音频帧 ${sent} 帧`);
    } finally {
      flushing = false;
    }
  }

  function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
  }

  /** 说话结束后的「无结果」看门狗：10s 内无识别文本也无回复 → 轻提示 */
  function startWatchdog(): void {
    clearWatchdog();
    watchdogTimer = window.setTimeout(() => {
      watchdogTimer = null;
      if (!userText.value && !aiStream.value && phase.value !== "replying") {
        console.warn("[audio] 看门狗：说话结束 10s 内未收到识别结果或回复");
        setNotice("没有收到识别结果，请检查麦克风或网络后重试");
      }
    }, WATCHDOG_TIMEOUT_MS);
  }

  function clearWatchdog(): void {
    if (watchdogTimer !== null) {
      window.clearTimeout(watchdogTimer);
      watchdogTimer = null;
    }
  }

  /* ==================== 会话空闲超时 ==================== */

  /** 重置空闲超时倒计时（有任何上行/下行活动时调用）；到期后释放生命周期并返回文本页 */
  function resetIdleTimer(): void {
    clearIdleTimer();
    idleTimer = window.setTimeout(() => {
      idleTimer = null;
      void fireIdleTimeout();
    }, IDLE_TIMEOUT_MS);
  }

  function clearIdleTimer(): void {
    if (idleTimer !== null) {
      window.clearTimeout(idleTimer);
      idleTimer = null;
    }
  }

  /**
   * 空闲超时处理：提示 → 释放生命周期资源（幂等）→ 页面进入终止态（"expired"）→ 回调页面弹窗提示。
   * 前端定时器触发与后端 SESSION_TIMEOUT 事件共用此入口（后端兜底先释放时前端重复 teardown 无害）。
   */
  async function fireIdleTimeout(): Promise<void> {
    if (tornDown || phase.value === "error") return;
    clearIdleTimer();
    console.warn("[audio] 会话空闲超时，释放生命周期并停留在语音页提示");
    setNotice("会话已超时，请重新进入继续对话");
    await teardown();
    // 无论用户后续选择「重新进入」还是关闭弹窗，页面都已是终止态（不再假装在聆听）
    phase.value = "expired";
    options.onIdleTimeout?.();
  }

  /** 解析 /user/queue/error 的 Result{code,msg} 报文，返回可读文案 */
  function parsePipelineError(raw: string): string {
    if (!raw) return "";
    try {
      const body = JSON.parse(raw) as { code?: number; msg?: string };
      if (body?.msg) return body.msg;
    } catch {
      /* 非 JSON，按纯文本处理 */
    }
    return raw;
  }

  /** 轻提示（非阻塞，自动消失） */
  function setNotice(text: string): void {
    notice.value = text;
    if (noticeTimer !== null) window.clearTimeout(noticeTimer);
    noticeTimer = window.setTimeout(() => {
      notice.value = "";
      noticeTimer = null;
    }, NOTICE_DURATION_MS);
  }

  // 开发/自动化测试调试钩子（仅 DEV 注入，生产构建不生效）：
  // 后端 TTS 依赖外部服务，可用合成 PCM 验证播放排队与 barge-in 中断
  if (import.meta.env.DEV) {
    (window as unknown as { __audioDebug?: unknown }).__audioDebug = {
      player,
      recorder,
      /** 向播放器注入一段 440Hz 合成 PCM（秒） */
      enqueueTestPcm: (seconds = 1) => {
        const samples = Math.round(24000 * seconds);
        const pcm = new Int16Array(samples);
        for (let i = 0; i < samples; i++) {
          pcm[i] = Math.round(Math.sin((2 * Math.PI * 440 * i) / 24000) * 8000);
        }
        player.enqueue(new Uint8Array(pcm.buffer));
      },
    };
  }

  /** 构建会话流处理器；虚拟形象（AVATAR_2D）通道仅在输出模式包含时注入订阅（与后端"仅含 AVATAR_2D 才推送"对齐） */
  function buildStreamHandlers(): ConversationStreamHandlers {
    const handlers: ConversationStreamHandlers = {
      onTextReply: (chunk) => handleAiChunk(chunk),
      onConversationName: (name) => {
        if (name) conversationName.value = name;
      },
      onPendingAction: (raw) => {
        const item = parsePendingActionPush(raw);
        if (item) upsertPendingAction(item);
      },
      onAudioBinary: (bytes) => handleAudioBinary(bytes),
      onAsrIntermediate: (text) => {
        // ASR 中间结果：渐进更新用户文本（定型前）
        userText.value = text;
        userTextFinal.value = false;
        clearWatchdog();
        // ASR 中间结果下行即活跃：重置空闲超时倒计时
        resetIdleTimer();
      },
      onPipelineError: (message) => {
        // 服务端业务/管道异常（如 ASR 会话失效、适配器不存在）：轻提示，避免静默无反应。
        // 后端报文为 Result{code,msg}，解析后展示可读文案（避免直接把 JSON 抛给用户）
        console.warn("[audio] 服务端管道异常：", message);
        const text = parsePipelineError(message);
        if (text) setNotice(text);
      },
      onSessionTimeout: () => {
        // 后端兜底判定空闲超时并已释放资源：前端不再依赖本地定时器，主动回到文本对话页
        void fireIdleTimeout();
      },
      onQuotaRejected: (rawJson) => {
        // AI 配额拒绝（语音识别/合成/形象播报超限或停用）：轻提示对应降级行为
        showQuotaRejected(rawJson);
      },
    };
    if (currentModes.outputTypes.includes("AVATAR_2D")) {
      handlers.onAvatar2dExpression = (raw) => {
        if (raw) options.onAvatar2dExpression?.(raw);
      };
      handlers.onAvatar2dMotion = (raw) => {
        if (raw) options.onAvatar2dMotion?.(raw);
      };
    }
    return handlers;
  }

  /* ==================== 启动 ==================== */

  /** 启动语音会话（进入页面自动调用；登录后也会自动重跑）——不 init，等待用户输入 */
  async function boot(): Promise<void> {
    if (booting || tornDown) return;
    if (!userStore.token) {
      phase.value = "idle";
      return;
    }
    booting = true;
    errorText.value = "";
    try {
      // 1. 实时通道（一次性 ticket + 原生 WebSocket + STOMP）
      phase.value = "connecting";
      const ok = await stomp.connect(8000);
      if (!ok) throw new Error("无法建立实时语音通道");
      if (tornDown) return;

      // 2. 从文本页进入：释放旧的 TEXT 生命周期（唯一释放者；不 init）
      phase.value = "initializing";
      const existingId = conversationId.value;
      if (options.releaseOnBoot && existingId) {
        await releaseConversation(existingId);
      }
      if (tornDown) return;

      // 3. 历史消息与待处理卡片（不依赖 init，可直接拉取）
      if (existingId && !isLocalConversationId(existingId)) {
        try {
          const memory = await fetchRoundMemory(existingId);
          messages.value = memory.messages;
          pendingActions.value = memory.pendingActions;
          baselineAssistantCount = memory.messages.filter((m) => m.type === "assistant").length;
        } catch {
          /* 历史加载失败不阻塞 */
        }
      }

      // 4. 输入就绪
      if (isAudioInput.value) {
        phase.value = "requesting-mic";
        const playerReady = currentModes.outputTypes.includes("AUDIO") ? await player.ensureReady() : true;
        // 持续收音：仅在启用语音输入时启动；帧在 init 前先缓冲
        await recorder.start();
        const recorderReady = await recorder.resume();
        needResume.value = !playerReady || !recorderReady;
        volume.value = 0;
        phase.value = "listening";
      } else {
        // 文本输入模式：不申请麦克风；仅在输出含语音时准备音频上下文
        needResume.value = currentModes.outputTypes.includes("AUDIO") && !(await player.ensureReady());
        phase.value = "ready";
      }
      // 进入聆听/就绪即开始空闲倒计时（首次输入 init 后由 ensureActive 重新 reset）
      resetIdleTimer();
      if (tornDown) return;
    } catch (e) {
      setError(micErrorMessage(e) || (e instanceof Error ? e.message : "语音对话启动失败，请重试"));
    } finally {
      booting = false;
    }
  }

  /**
   * 首次输入触发的懒初始化（幂等，缓存在 activePromise）。
   * init → 回写会话 id → 订阅（bind 先于任何帧发送）。
   */
  function ensureActive(): Promise<string> {
    if (activeConversationId) return Promise.resolve(activeConversationId);
    if (activePromise) return activePromise;
    activePromise = (async () => {
      const ok = stomp.state.value === "connected" ? true : await stomp.connect(8000);
      if (!ok) throw new Error("无法建立实时语音通道");
      const id = conversationId.value && !isLocalConversationId(conversationId.value) ? conversationId.value : undefined;
      let realId: string;
      try {
        realId = await initConversation(id, currentModes);
      } catch (e) {
        // 既有会话已失效（如空会话已被后端清理，业务码 1400）：回退为新建会话，不阻塞用户
        if (!id) throw e;
        console.warn("[audio] 既有会话初始化失败，回退新建会话", e);
        setNotice("原会话已失效，已为你新建会话");
        realId = await initConversation(undefined, currentModes);
      }
      if (!realId) throw new Error("会话初始化失败");
      conversationId.value = realId;
      conversationStore.setCurrentConversation(realId);
      options.onConversationIdChange?.(realId);
      stomp.bind(realId, buildStreamHandlers());
      baselineAssistantCount = messages.value.filter((m) => m.type === "assistant").length;
      activeConversationId = realId;
      // 生命周期建立后重新起算空闲超时（以 init 时刻为准，避免历史会话 lastActiveTime 过期即触发）
      resetIdleTimer();
      return realId;
    })().catch((e) => {
      activePromise = null;
      frameBuffer.length = 0;
      throw e;
    });
    return activePromise;
  }

  /** 文本输入模式：发送文本（懒初始化；后续复用 2802 自愈） */
  async function sendText(text: string): Promise<void> {
    const value = text.trim();
    if (!value || tornDown || !userStore.token) return;
    // 新一轮开始：展示文本重置为本次输入
    userText.value = value;
    userTextFinal.value = true;
    aiStream.value = "";
    finalReconcileScheduled = false;
    clearReconcileTimer();
    reconcileAttempts = 0;
    clearWatchdog();
    const optimistic: MemoryMessage = {
      id: `local-${Date.now()}`,
      content: value,
      type: "user",
      roundNum: 0,
      createdTime: dayjs().format(FORMAT_DATETIME),
    };
    messages.value = [...messages.value, optimistic];
    phase.value = "replying";
    // 文本上行即活跃：重置空闲超时倒计时
    resetIdleTimer();
    try {
      const id = await ensureActive();
      baselineAssistantCount = messages.value.filter((m) => m.type === "assistant").length;
      await sendTextWithLifecycle(id, value, currentModes);
      startNoChunkFallback();
    } catch (e) {
      messages.value = messages.value.filter((m) => m.id !== optimistic.id);
      setError((e as Error)?.message || "发送失败，请重试");
    }
  }

  /**
   * 语音页内切换输入/输出模式：
   * - 关闭语音输入时先补尾帧停采集；
   * - 若会话已激活：释放旧管道 → 重置激活态 → 立即按新模式重建（后端 init 幂等）；
   * - 启用语音输入时确保音频上下文与采集就绪。
   */
  async function setModes(inputTypes: string[], outputTypes: string[]): Promise<void> {
    if (tornDown) return;
    const nextAudio = inputTypes.includes("AUDIO");
    // 1. 关闭语音输入：先补发尾帧停采集（此刻 WS 与旧管道仍存活）
    if (!nextAudio && recorder.active) {
      await stopCapture(true);
    }
    currentModes = { inputTypes: [...inputTypes], outputTypes: [...outputTypes] };
    isAudioInput.value = nextAudio;

    // 2. 已激活：释放旧管道并按新模式重建（重建会打断在途回复，清空流式展示）
    const wasActive = !!activeConversationId;
    if (wasActive) {
      const id = activeConversationId;
      await releaseConversation(id);
      activeConversationId = "";
      activePromise = null;
      frameBuffer.length = 0;
      aiStream.value = "";
      userText.value = "";
      userTextFinal.value = false;
      await ensureActive();
      await flushFrameBuffer();
    }

    // 3. 采集/阶段就绪
    if (nextAudio) {
      const playerReady = currentModes.outputTypes.includes("AUDIO") ? await player.ensureReady() : true;
      try {
        if (!recorder.active) await recorder.start();
        const recorderReady = await recorder.resume();
        needResume.value = !playerReady || !recorderReady;
        volume.value = 0;
        phase.value = "listening";
      } catch (e) {
        // 麦克风不可用：给出提示并保持错误态（可再改回文本输入恢复）
        setError(micErrorMessage(e) || "无法开启麦克风，请检查权限后重试");
      }
    } else {
      aiStream.value = "";
      userText.value = "";
      userTextFinal.value = false;
      phase.value = "ready";
    }
    // 切换模式属于用户主动操作：重新起算空闲超时倒计时
    resetIdleTimer();
  }

  /** 强制重试：释放可能已建立的管道后完整重跑 boot */
  async function retry(): Promise<void> {
    if (tornDown) return;
    await stopCapture();
    player.bargeIn();
    needResume.value = false;
    frameBuffer.length = 0;
    flushing = false;
    activeConversationId = "";
    activePromise = null;
    clearIdleTimer();
    clearWatchdog();
    clearNoChunkTimer();
    notice.value = "";
    userText.value = "";
    userTextFinal.value = false;
    aiStream.value = "";
    if (conversationId.value) {
      await releaseConversation(conversationId.value);
    }
    await boot();
  }

  /**
   * 超时后的页面内恢复：「重新进入」入口。
   * 空闲超时已 teardown（释放 lifecycle / 断 STOMP / dispose 播放器），
   * 此处重置可恢复状态并重跑 boot，保留 conversationId 续聊同一会话。
   */
  async function reboot(): Promise<void> {
    if (!tornDown && !teardownStarted) {
      await boot();
      return;
    }

    console.warn("[audio] 用户选择重新进入，重建语音会话");
    // dispose 后的播放器不可复用，必须创建新实例；其余闭包会捕获新的 let 绑定
    await player.dispose().catch(() => {});
    player = new PcmStreamPlayer({
      sampleRate: 24000,
      onPlaybackStateChange: (playing) => {
        playerPlaying.value = playing;
      },
    });
    if (import.meta.env.DEV) {
      const debug = (window as unknown as { __audioDebug?: { player?: PcmStreamPlayer } }).__audioDebug;
      if (debug) debug.player = player;
    }

    // 复位关闭/激活状态，确保不会复用已释放的通道和生命周期 Promise
    tornDown = false;
    teardownStarted = false;
    booting = false;
    activeConversationId = "";
    activePromise = null;
    frameBuffer.length = 0;
    flushing = false;
    discardAudioUntil = 0;
    publishRetryAt = 0;

    // 复位对账状态，避免旧会话基线影响重新进入后的回复对账
    baselineAssistantCount = 0;
    reconcileAttempts = 0;
    finalReconcileScheduled = false;

    // 复位页面展示状态；boot 会按 conversationId 重新拉取历史
    messages.value = [];
    pendingActions.value = [];
    errorText.value = "";
    needResume.value = false;
    volume.value = 0;
    userText.value = "";
    userTextFinal.value = false;
    aiStream.value = "";
    notice.value = "";

    clearAiIdleTimer();
    clearIdleTimer();
    clearNoChunkTimer();
    clearWatchdog();
    clearReconcileTimer();

    await boot();
  }

  /** 兜底恢复 AudioContext（"点击继续"遮罩） */
  async function resumeAudio(): Promise<void> {
    const playerReady = currentModes.outputTypes.includes("AUDIO") ? await player.ensureReady() : true;
    const recorderReady = isAudioInput.value ? await recorder.resume() : true;
    needResume.value = !playerReady || !recorderReady;
  }

  /* ==================== 流式事件 ==================== */

  function handleAiChunk(chunk: string) {
    if (!chunk) return;
    if (!aiStream.value) {
      reconcileAttempts = 0;
      finalReconcileScheduled = false;
      baselineAssistantCount = messages.value.filter((m) => m.type === "assistant").length;
      clearNoChunkTimer();
    }
    // 首字到达：本轮用户文本定型（ASR 临时结果固化为最终展示）
    if (userText.value) userTextFinal.value = true;
    clearWatchdog();
    aiStream.value += chunk;
    phase.value = "replying";
    // AI 文本下行即活跃：重置空闲超时倒计时
    resetIdleTimer();
    resetAiIdleTimer();
  }

  function resetAiIdleTimer() {
    if (aiIdleTimer !== null) window.clearTimeout(aiIdleTimer);
    aiIdleTimer = window.setTimeout(() => {
      aiIdleTimer = null;
      void reconcile();
    }, AI_IDLE_MS);
  }

  function startNoChunkFallback() {
    clearNoChunkTimer();
    noChunkTimer = window.setTimeout(() => {
      noChunkTimer = null;
      if (phase.value === "replying" && !aiStream.value) void reconcile();
    }, NO_CHUNK_FALLBACK_MS);
  }

  function clearNoChunkTimer() {
    if (noChunkTimer !== null) {
      window.clearTimeout(noChunkTimer);
      noChunkTimer = null;
    }
  }

  function handleAudioBinary(bytes: Uint8Array) {
    if (tornDown) return;
    // barge-in 丢弃窗口：忽略服务端在途/迟到的 TTS 帧，避免被打断的语音复播
    if (performance.now() < discardAudioUntil) return;
    player.enqueue(bytes);
    // TTS 音频下行即活跃：重置空闲超时倒计时
    resetIdleTimer();
  }

  function upsertPendingAction(item: PendingActionItem) {
    const idx = pendingActions.value.findIndex((p) => p.id === item.id);
    if (idx >= 0) {
      pendingActions.value[idx] = { ...pendingActions.value[idx], ...item };
    } else {
      pendingActions.value = [...pendingActions.value, item];
    }
  }

  function clearReconcileTimer(): void {
    if (reconcileTimer !== null) {
      window.clearTimeout(reconcileTimer);
      reconcileTimer = null;
    }
  }

  /** AI 回复结束（文本空闲）→ memory 对账：以服务端数据整体替换本地流式展示 */
  async function reconcile(): Promise<void> {
    const id = conversationId.value;
    if (tornDown || !id || isLocalConversationId(id)) return;
    if (reconcileTimer !== null) {
      window.clearTimeout(reconcileTimer);
      reconcileTimer = null;
    }
    try {
      const memory = await fetchRoundMemory(id);
      const assistantCount = memory.messages.filter((m) => m.type === "assistant").length;
      if (assistantCount > baselineAssistantCount) {
        messages.value = memory.messages;
        pendingActions.value = memory.pendingActions;
        // 语音页不渲染历史：落库后保留本轮展示（不清空）。
        // 流式推送可能只到部分文本（后端按句/TTS 分段），以落库的权威文本覆盖更长的内容
        const lastAssistant = [...memory.messages].reverse().find((m) => m.type === "assistant");
        if (lastAssistant?.content && lastAssistant.content.length > aiStream.value.length) {
          aiStream.value = lastAssistant.content;
        }
        if (userText.value) userTextFinal.value = true;
        finalReconcileScheduled = false;
        clearNoChunkTimer();
        clearWatchdog();
        if (phase.value === "replying") phase.value = recorder.active ? "listening" : "ready";
        return;
      }
      scheduleReconcileRetry();
    } catch {
      scheduleReconcileRetry();
    }
  }

  function scheduleReconcileRetry() {
    if (reconcileAttempts < RECONCILE_MAX_RETRY) {
      reconcileAttempts += 1;
      reconcileTimer = window.setTimeout(() => {
        reconcileTimer = null;
        void reconcile();
      }, RECONCILE_RETRY_MS);
      return;
    }
    // 短重试耗尽但已有流式文本：落库可能更晚，保留"回复中"并延迟再对账一次，
    // 以便用权威文本覆盖只推了一半的流式内容（标志由新一轮/成功后重置，避免无限循环）
    if (!finalReconcileScheduled && aiStream.value) {
      finalReconcileScheduled = true;
      reconcileTimer = window.setTimeout(() => {
        reconcileTimer = null;
        reconcileAttempts = 0;
        void reconcile();
      }, FINAL_RECONCILE_DELAY_MS);
      return;
    }
    // 最终仍无结果：保留本地流式展示，下次回复/推送时再对账
    if (phase.value === "replying") phase.value = recorder.active ? "listening" : "ready";
  }

  /* ==================== 清理 ==================== */

  /**
   * 离开页面（路由守卫调用，内置 1.5s 超时兜底）：
   * 补发尾帧停采集（WS 需存活）→ 停播放 → 释放 lifecycle → 断开 STOMP。
   * 幂等，可重复调用。
   */
  async function teardown(): Promise<void> {
    // teardownStarted 负责重入保护（tornDown 需等尾帧补发完才置位，不能作为重入锁）
    if (tornDown || teardownStarted) return;
    teardownStarted = true;
    // 先补发尾帧停采集（此时 tornDown 尚未置位，onFrame 仍会放行静音帧），
    // 让服务端完成最终识别后再整体置位清理标志
    try {
      // 1. 若正在说话：先经 onFrame 补发静音帧，让服务端完成最终识别（此时 WS 仍存活）
      await stopCapture(true);
    } catch {
      /* ignore */
    }
    try {
      // 给服务端静音判停窗口留出时间，避免尾句尚未返回 definite ASR 就释放连接
      await sleep(900);
    } catch {
      /* ignore */
    }
    tornDown = true;
    frameBuffer.length = 0;
    flushing = false;
    clearAiIdleTimer();
    clearIdleTimer();
    clearNoChunkTimer();
    clearWatchdog();
    if (noticeTimer !== null) {
      window.clearTimeout(noticeTimer);
      noticeTimer = null;
    }
    if (reconcileTimer !== null) {
      window.clearTimeout(reconcileTimer);
      reconcileTimer = null;
    }
    try {
      // 2. 停止播放并关闭音频上下文
      await player.dispose();
      // 3. 释放后端适配器/管道（带超时，失败不阻塞导航）
      const id = conversationId.value;
      if (id && !isLocalConversationId(id)) {
        await withTimeout(releaseConversation(id), TEARDOWN_TIMEOUT_MS);
      }
      // 4. 断开实时通道
      await withTimeout(stomp.dispose(), TEARDOWN_TIMEOUT_MS);
    } catch (e) {
      console.warn("[audio] 页面清理异常", e);
    }
  }

  async function stopCapture(trailingSilence = false): Promise<void> {
    try {
      await recorder.stop({ trailingSilence });
    } catch {
      /* ignore */
    }
  }

  /**
   * 页面卸载兜底（关闭标签页 / 刷新 / 跨文档离开不缓存时由 pagehide 调用）：
   * fire-and-forget 轻量释放，不等待。teardown() 的 900ms 尾帧等待在卸载场景不可行，
   * 这里直接用原生 fetch(keepalive) 保证请求在页面销毁后仍发出。
   * 幂等可重复调用；不置 tornDown（正常的 teardown 仍由路由取消链路负责）。
   */
  function disposeOnUnload(): void {
    // 空闲超时已释放（tornDown）：pagehide/刷新不再重复发幂等 DELETE，静默跳过
    if (tornDown) return;
    const id = conversationId.value;
    if (id && !isLocalConversationId(id)) {
      const headers = getAuthHeaders("user");
      const url = `${API_BASE_URL}/user/conversation/lifecycle/${id}`;
      fetch(url, { method: "DELETE", keepalive: true, headers }).catch(() => {
        /* 卸载兜底失败可接受：服务端靠 TCP 断开 / 心跳回收 */
      });
    }
    void player.dispose();
    void stomp.dispose();
    clearIdleTimer();
  }

  function handleTransportError(text: string) {
    if (tornDown) return;
    void stopCapture();
    player.bargeIn();
    setError(text || "实时通道异常，请重试");
  }

  function setError(text: string) {
    phase.value = "error";
    errorText.value = text;
    clearIdleTimer();
  }

  function clearAiIdleTimer() {
    if (aiIdleTimer !== null) {
      window.clearTimeout(aiIdleTimer);
      aiIdleTimer = null;
    }
  }

  function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T | void> {
    return Promise.race([
      promise,
      new Promise<void>((resolve) => {
        window.setTimeout(resolve, ms);
      }),
    ]);
  }

  /** 麦克风相关错误 → 友好文案（非麦克风错误返回空串） */
  function micErrorMessage(err: unknown): string {
    if (err instanceof DOMException) {
      switch (err.name) {
        case "NotAllowedError":
        case "SecurityError":
          return "未获得麦克风权限，无法开始语音对话";
        case "NotFoundError":
        case "OverconstrainedError":
          return "未检测到可用的麦克风设备";
        case "NotReadableError":
        case "AbortError":
          return "麦克风被其他程序占用，无法读取";
        default:
          return "";
      }
    }
    return "";
  }

  return {
    // 状态
    phase,
    errorText,
    /** 轻提示（非阻塞，自动消失） */
    notice,
    needResume,
    volume,
    messages,
    pendingActions,
    /** 本轮用户文本（渐进；userTextFinal 标识是否已定型） */
    userText,
    userTextFinal,
    aiStream,
    conversationId,
    conversationName,
    playerPlaying,
    isAudioInput,
    // 动作
    boot,
    reboot,
    retry,
    teardown,
    disposeOnUnload,
    resumeAudio,
    sendText,
    setModes,
  };
}
