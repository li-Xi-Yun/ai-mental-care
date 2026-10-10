/**
 * 语音对话编排（独立语音页专用）
 *
 * 进入页面即自动持续收音（无"开始语音输入"按钮）：
 *   ticket/WS → lifecycle(AUDIO in / TEXT+AUDIO out) → 订阅 → 历史 → 音频上下文 → 采集
 * 上行：16k PCM 帧（VAD 自动断句 + 静音补帧）；下行：24k PCM 流式播放；
 * 用户开口时本地立即打断 TTS（barge-in）；AI 文本空闲 2s 后与 memory 对账。
 */
import { ref } from "vue";
import { deleteConversationLifecycle, initConversationLifecycle } from "@/portal/api/conversation/lifecycle";
import { fetchRoundMemory, parsePendingActionPush } from "@/portal/api/conversation/dialogue";
import { useStompConversation } from "@/portal/composables/useStompConversation";
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
  | "initializing" // 生命周期与历史
  | "requesting-mic" // 申请麦克风
  | "listening" // 持续聆听
  | "speech" // 用户说话/识别中
  | "replying" // AI 回复中
  | "error"; // 失败（可重试）

/** AI 文本空闲判定（最后一个 chunk 后超过该时长视为回复结束） */
const AI_IDLE_MS = 2000;
/** memory 对账重试间隔与最大次数 */
const RECONCILE_RETRY_MS = 800;
const RECONCILE_MAX_RETRY = 3;
/** barge-in 后丢弃在途 TTS 帧的窗口（服务端已合成的音频可能在通道中） */
const BARGE_IN_DISCARD_MS = 1200;
/** 离开页面的清理超时（避免卡住路由导航） */
const TEARDOWN_TIMEOUT_MS = 1500;

export interface UseAudioConversationOptions {
  /** URL query 中的会话 ID（空则新建） */
  initialConversationId: string;
  /** 同会话此前已被文本页绑定（URL query lifecycleBound=1）：需先释放再按 AUDIO 重建 */
  lifecycleBound: boolean;
  /** 生命周期 init 返回真实会话 ID 后回调（用于回写 URL） */
  onConversationIdChange?: (id: string) => void;
}

export function useAudioConversation(options: UseAudioConversationOptions) {
  const userStore = useUserStore();
  const conversationStore = useConversationStore();

  /* ==================== 状态 ==================== */
  const phase = ref<AudioPhase>("idle");
  const errorText = ref("");
  /** AudioContext 被浏览器挂起，需要一次用户点击继续 */
  const needResume = ref(false);
  const volume = ref(0);
  const messages = ref<MemoryMessage[]>([]);
  const pendingActions = ref<PendingActionItem[]>([]);
  /** 当前识别中的临时用户气泡（ASR 中间结果） */
  const asrDraft = ref("");
  /** 已定型的用户话（等待 memory 对账替换） */
  const asrFinal = ref("");
  /** AI 流式回复（等待 memory 对账替换） */
  const aiStream = ref("");
  const conversationId = ref(options.initialConversationId || "");
  const conversationName = ref("");

  let booting = false;
  let tornDown = false;
  let aiIdleTimer: number | null = null;
  let reconcileTimer: number | null = null;
  let reconcileAttempts = 0;
  let baselineAssistantCount = 0;
  let discardAudioUntil = 0;

  /* ==================== 音频 ==================== */
  const player = new PcmStreamPlayer({ sampleRate: 24000 });

  const stomp = useStompConversation({
    onTransportClose: () => handleTransportError("与服务器的连接已断开"),
    onStompError: (detail) => handleTransportError(detail || "实时通道异常"),
  });

  const recorder = new VadRecorder({
    onFrame: (pcm) => {
      if (tornDown || !conversationId.value) return;
      // 说话中的真实帧 + 结束后的静音补帧都上行；空闲期不发帧（省流量）
      stomp.publishAudioFrame(pcm16ToBase64(pcm), conversationId.value);
    },
    onSpeechStart: () => {
      // 用户开口：本地立即打断 TTS（服务端 barge-in 为最终权威），并丢弃在途迟到帧
      player.bargeIn();
      discardAudioUntil = performance.now() + BARGE_IN_DISCARD_MS;
      asrDraft.value = "";
      phase.value = "speech";
    },
    onSpeechEnd: () => {
      if (phase.value === "speech") phase.value = aiStream.value ? "replying" : "listening";
    },
    onVolume: (v) => {
      volume.value = v;
    },
    onError: (err) => {
      console.warn("[audio] 采集过程出错", err);
    },
  });

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

  const streamHandlers: ConversationStreamHandlers = {
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
      asrDraft.value = text;
    },
  };

  /* ==================== 启动 ==================== */

  /** 启动语音会话（进入页面自动调用；登录后也会自动重跑） */
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

      // 2. 生命周期：同会话此前可能以 TEXT 类型绑定（文本页），后端不支持热切换 → 先释放再按 AUDIO 重建
      phase.value = "initializing";
      const realId = await initAudioLifecycle();
      if (!realId) throw new Error("会话初始化失败");
      conversationId.value = realId;
      conversationStore.setCurrentConversation(realId);
      options.onConversationIdChange?.(realId);
      if (tornDown) return;

      // 3. 先订阅再收音（AI 回复流可能早于任何后续请求到达）
      stomp.bind(realId, streamHandlers);

      // 4. 历史消息与待处理卡片
      const memory = await fetchRoundMemory(realId);
      messages.value = memory.messages;
      pendingActions.value = memory.pendingActions;
      baselineAssistantCount = memory.messages.filter((m) => m.type === "assistant").length;

      // 5. 播放上下文就绪（自动播放策略兜底：挂起时由页面遮罩引导点击）
      phase.value = "requesting-mic";
      needResume.value = !(await player.ensureReady());

      // 6. 持续收音：进入页面即开始，无需任何按钮
      await recorder.start();
      volume.value = 0;
      phase.value = "listening";
    } catch (e) {
      setError(micErrorMessage(e) || (e instanceof Error ? e.message : "语音对话启动失败，请重试"));
    } finally {
      booting = false;
    }
  }

  /**
   * 初始化语音生命周期（inputTypes=AUDIO / outputTypes=TEXT+AUDIO）。
   * 带旧会话时先按需释放 TEXT 绑定再重建；
   * 边界兜底：若旧会话是「无消息的空会话」，lifecycle/end 时后端会一并清理它，
   * 导致带旧 ID init 报「会话不存在」——此时回退为新建会话（旧空会话本就无内容，等效）。
   */
  async function initAudioLifecycle(): Promise<string> {
    const existingId = conversationId.value;
    if (existingId) {
      if (options.lifecycleBound) {
        await deleteConversationLifecycle(existingId).catch((e) => {
          console.warn("[audio] 释放旧生命周期失败（忽略）", e);
        });
      }
      try {
        const res = await initConversationLifecycle({
          conversationId: existingId,
          inputTypes: ["AUDIO"],
          outputTypes: ["TEXT", "AUDIO"],
        });
        const id = String(res?.data?.data?.conversationId ?? "");
        if (id) return id;
      } catch (e) {
        console.warn("[audio] 复用既有会话初始化失败，回退为新建会话", e);
      }
    }
    const fresh = await initConversationLifecycle({ inputTypes: ["AUDIO"], outputTypes: ["TEXT", "AUDIO"] });
    return String(fresh?.data?.data?.conversationId ?? "");
  }

  /** 失败后重试：停止采集/播放，释放可能已建立的管道，再完整重跑 boot */
  async function retry(): Promise<void> {
    if (tornDown) return;
    await stopCapture();
    player.bargeIn();
    needResume.value = false;
    if (conversationId.value) {
      await deleteConversationLifecycle(conversationId.value).catch(() => {});
    }
    await boot();
  }

  /** 兜底恢复 AudioContext（"点击继续"遮罩） */
  async function resumeAudio(): Promise<void> {
    const ok = await player.ensureReady();
    needResume.value = !ok;
  }

  /* ==================== 流式事件 ==================== */

  function handleAiChunk(chunk: string) {
    if (!chunk) return;
    if (!aiStream.value) {
      reconcileAttempts = 0;
      baselineAssistantCount = messages.value.filter((m) => m.type === "assistant").length;
    }
    // 首字到达：ASR 临时气泡定型
    if (asrDraft.value) {
      asrFinal.value = asrDraft.value;
      asrDraft.value = "";
    }
    aiStream.value += chunk;
    phase.value = "replying";
    resetAiIdleTimer();
  }

  function resetAiIdleTimer() {
    if (aiIdleTimer !== null) window.clearTimeout(aiIdleTimer);
    aiIdleTimer = window.setTimeout(() => {
      aiIdleTimer = null;
      void reconcile();
    }, AI_IDLE_MS);
  }

  function handleAudioBinary(bytes: Uint8Array) {
    if (tornDown) return;
    // barge-in 丢弃窗口：忽略服务端在途/迟到的 TTS 帧，避免被打断的语音复播
    if (performance.now() < discardAudioUntil) return;
    player.enqueue(bytes);
  }

  function upsertPendingAction(item: PendingActionItem) {
    const idx = pendingActions.value.findIndex((p) => p.id === item.id);
    if (idx >= 0) {
      pendingActions.value[idx] = { ...pendingActions.value[idx], ...item };
    } else {
      pendingActions.value = [...pendingActions.value, item];
    }
  }

  /** AI 回复结束（文本空闲）→ memory 对账：以服务端数据整体替换本地流式展示 */
  async function reconcile(): Promise<void> {
    const id = conversationId.value;
    if (tornDown || !id) return;
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
        aiStream.value = "";
        asrFinal.value = "";
        asrDraft.value = "";
        if (phase.value === "replying") phase.value = recorder.active ? "listening" : phase.value;
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
    // 对账失败：保留本地流式展示，下次回复/推送时再对账
    if (phase.value === "replying") phase.value = recorder.active ? "listening" : phase.value;
  }

  /* ==================== 清理 ==================== */

  /**
   * 离开页面（路由守卫调用，内置 1.5s 超时兜底）：
   * 补发尾帧停采集（WS 需存活）→ 停播放 → 释放 lifecycle → 断开 STOMP。
   * 幂等，可重复调用。
   */
  async function teardown(): Promise<void> {
    if (tornDown) return;
    tornDown = true;
    clearAiIdleTimer();
    if (reconcileTimer !== null) {
      window.clearTimeout(reconcileTimer);
      reconcileTimer = null;
    }
    try {
      // 1. 若正在说话：先经 onFrame 补发静音帧，让服务端完成最终识别（此时 WS 仍存活）
      await stopCapture(true);
      // 2. 停止播放并关闭音频上下文
      await player.dispose();
      // 3. 释放后端适配器/管道（带超时，失败不阻塞导航）
      const id = conversationId.value;
      if (id) {
        await withTimeout(
          deleteConversationLifecycle(id).catch((e) => {
            console.warn("[audio] 释放生命周期失败", e);
          }),
          TEARDOWN_TIMEOUT_MS,
        );
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

  function handleTransportError(text: string) {
    if (tornDown) return;
    void stopCapture();
    player.bargeIn();
    setError(text || "实时通道异常，请重试");
  }

  function setError(text: string) {
    phase.value = "error";
    errorText.value = text;
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
    needResume,
    volume,
    messages,
    pendingActions,
    asrDraft,
    asrFinal,
    aiStream,
    conversationId,
    conversationName,
    // 动作
    boot,
    retry,
    teardown,
    resumeAudio,
  };
}
