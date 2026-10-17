<template>
  <div
    class="conversation-page"
    ref="pageRef"
    :class="{ resizing: dragState }"
    :style="{ '--portal-header-height': PORTAL_HEADER_HEIGHT + 'px' }"
  >
    <ConversationListPanel
      :conversation-list="conversationList"
      :current-conversation-id="currentConversationId"
      :loading="loadingConversations"
      :show-panel="showConversationList"
      :width="conversationListWidth"
      @select="selectConversation"
      @new="handleNewConversation"
      @command="handleItemCommand"
    />

    <div
      v-if="showConversationList"
      class="resize-bar"
      title="拖拽调整列表宽度"
      @pointerdown="startResize('list', $event)"
    ></div>

    <ChatPanel
      ref="chatPanelRef"
      :current-conversation="currentConversation"
      :loading="loadingChat"
      :display-messages="displayMessages"
      :thinking="thinking"
      :stream-text="aiStream"
      :display-pending-actions="displayPendingActions"
      :pending-act-actions="pendingActActions"
      :show-conversation-list="showConversationList"
      :show-emotion-panel="showEmotionPanel"
      v-model:input-message="inputMessage"
      @toggle-list="showConversationList = !showConversationList"
      @toggle-emotion="showEmotionPanel = !showEmotionPanel"
      @send="handleSend"
      @focus-input="focusInput"
      @scale-start="handleScaleStart"
      @scale-answered="handleScaleAnswered"
    />

    <div
      v-if="showEmotionPanel"
      class="resize-bar"
      title="拖拽调整情绪面板宽度"
      @pointerdown="startResize('emotion', $event)"
    ></div>

    <EmotionPanel
      :items="emotionItems"
      :pagination="emotionPagination"
      :expanded-analysis-id="expandedAnalysisId"
      :show-panel="showEmotionPanel"
      :latest-diagnosis-id="latestDiagnosisId"
      :width="emotionPanelWidth"
      @toggle-panel="showEmotionPanel = false"
      @toggle-analysis="(id: string) => expandedAnalysisId = expandedAnalysisId === id ? null : id"
      @load-more="loadMoreEmotions"
      @view-detail="openEmotionDetail"
      @jump-diagnosis="openLatestDiagnosis"
    />

    <EmotionAnalysisDetailDialog
      v-model="emotionDetailVisible"
      :analysis-id="emotionDetailTargetId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { storeToRefs } from "pinia";
import { onBeforeRouteLeave, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useConversationStore } from "@/portal/stores/conversation";
import { useUserStore } from "@/portal/stores/user";
import { useConversationList } from "@/portal/composables/useConversationList";
import { cmpByTime } from "@/portal/composables/useEmotionDisplay";
import { useEmotionList } from "@/portal/composables/useEmotionList";
import { useScaleCardActions } from "@/portal/composables/useScaleCardActions";
import { useStompConversation } from "@/portal/composables/useStompConversation";
import { showQuotaRejected } from "@/portal/composables/useQuotaRejected";
import {
  TEXT_MODES,
  initConversation,
  isLocalConversationId,
  releaseConversation,
  sendTextWithLifecycle,
} from "@/portal/composables/useConversationLifecycle";
import type { ConversationItem, MemoryMessage, PendingActionItem } from "@/portal/api/conversation/types";
import { fetchRoundMemory, parsePendingActionPush } from "@/portal/api/conversation/dialogue";
import { getEmotionDiagnosisListByConversation } from "@/portal/api/conversation/emotion-diagnosis";
import dayjs, { FORMAT_DATETIME } from "@/shared/utils/dayjs";
import { PORTAL_HEADER_HEIGHT, CONVERSATION_LIST_WIDTH, EMOTION_PANEL_WIDTH } from "@/shared/api/config";
import ConversationListPanel from "@/portal/components/conversation/ConversationListPanel.vue";
import ChatPanel from "@/portal/components/conversation/ChatPanel.vue";
import EmotionPanel from "@/portal/components/conversation/EmotionPanel.vue";
import EmotionAnalysisDetailDialog from "@/portal/components/conversation/EmotionAnalysisDetailDialog.vue";

/* ==================== 常量 ==================== */

const NEW_CONV_NAME = "新对话";
/** 轮询兜底参数（WebSocket 不可用/无首包时启用） */
const MAX_POLL_TIMES = 40;
const POLL_INTERVAL = 1600;
/** AI 文本空闲判定（最后一个 chunk 后超过该时长视为回复结束） */
const AI_IDLE_MS = 2000;
/** memory 对账重试间隔与最大次数 */
const RECONCILE_RETRY_MS = 800;
const RECONCILE_MAX_RETRY = 3;
/** WS 已连接但迟迟无首包的兜底时长 */
const NO_CHUNK_FALLBACK_MS = 10000;
/** 心理评估为消息处理后的异步生成：回复完成后的复检间隔与最大次数 */
const DIAGNOSIS_RECHECK_MS = 30000;
const DIAGNOSIS_RECHECK_MAX = 3;

/* ==================== 布局状态（持久化到 store，刷新不重置） ==================== */

const conversationStore = useConversationStore();
const userStore = useUserStore();
const router = useRouter();
const { showConversationList, showEmotionPanel, expandedAnalysisId, conversationListWidth, emotionPanelWidth } = storeToRefs(conversationStore);
const { token, loginSuccessVersion } = storeToRefs(userStore);

/* ==================== 左右面板拖拽调宽（最小=默认宽度，最大=页宽 1/3；宽度持久化） ==================== */

const pageRef = ref<HTMLElement | null>(null);
/** 当前正在拖拽的侧边（拖拽期间禁用面板宽度过渡、锁定全局光标/选区） */
const dragState = ref<null | "list" | "emotion">(null);
/** 最小宽度即面板当前（默认）大小：左 220 / 右 260 */
const MIN_LIST_WIDTH = CONVERSATION_LIST_WIDTH;
const MIN_EMOTION_WIDTH = EMOTION_PANEL_WIDTH;

function pageWidth(): number {
  return pageRef.value?.clientWidth ?? window.innerWidth;
}

/** 拖拽分隔条调整侧栏宽度：左栏向右拖变宽，右栏向左拖变宽 */
function startResize(side: "list" | "emotion", e: PointerEvent): void {
  if (e.button !== 0) return;
  e.preventDefault();
  const startX = e.clientX;
  const startW = side === "list" ? conversationListWidth.value : emotionPanelWidth.value;
  const max = Math.floor(pageWidth() / 3);
  // 窗口过窄（页宽 1/3 < 默认宽度）时以最大值为下界，防止反向
  const min = Math.min(side === "list" ? MIN_LIST_WIDTH : MIN_EMOTION_WIDTH, max);
  dragState.value = side;
  document.body.classList.add("is-resizing");

  const onMove = (ev: PointerEvent): void => {
    const w = side === "list" ? startW + (ev.clientX - startX) : startW + (startX - ev.clientX);
    const cw = Math.min(Math.max(w, min), max);
    if (side === "list") conversationListWidth.value = cw;
    else emotionPanelWidth.value = cw;
  };
  const onEnd = (): void => {
    dragState.value = null;
    document.body.classList.remove("is-resizing");
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", onEnd);
    window.removeEventListener("pointercancel", onEnd);
  };
  window.addEventListener("pointermove", onMove);
  window.addEventListener("pointerup", onEnd);
  window.addEventListener("pointercancel", onEnd);
}

/* ==================== 数据状态 ==================== */

const {
  conversationList,
  currentConversationId,
  loadConversationList,
  handleRename,
  handleDelete,
} = useConversationList();

const allMessages = ref<MemoryMessage[]>([]);
const pendingActions = ref<PendingActionItem[]>([]);

/** 情绪分析：按会话分页加载（数据 + 分页状态） */
const {
  items: emotionItems,
  pagination: emotionPagination,
  load: loadEmotions,
  loadMore: loadMoreEmotions,
  reset: resetEmotions,
} = useEmotionList(() => currentConversationId.value);

/** 情绪分析详情弹窗 */
const emotionDetailVisible = ref(false);
const emotionDetailTargetId = ref<string | null>(null);

/** 当前会话最新一条心理评估 id（情绪面板顶部跳转入口；无评估为 null） */
const latestDiagnosisId = ref<string | null>(null);

/** 未发送的本地新会话：不进入左侧列表，发送首条消息后才插入列表项 */
const draftConversation = ref<ConversationItem | null>(null);

const inputMessage = ref("");
const loadingConversations = ref(true);
const loadingChat = ref(false);
const thinking = ref(false);
/** AI 流式回复（WebSocket 增量累积，对账后清空） */
const aiStream = ref("");

const chatPanelRef = ref<InstanceType<typeof ChatPanel> | null>(null);

let pollTimer: number | null = null;
let pollAttempts = 0;
let aiIdleTimer: number | null = null;
let reconcileTimer: number | null = null;
let noChunkTimer: number | null = null;
let diagnosisRecheckTimer: number | null = null;
let diagnosisRecheckAttempts = 0;
let reconcileAttempts = 0;
let baselineAssistantCount = 0;
let baselinePendingCount = 0;
/** 已订阅的会话 id（同一连接内避免重复订阅；连接断开时清空以便重连后重订） */
let boundConversationId = "";
/** 本轮是否收到过 pending-action 推送（无流式文本时也据此结束等待） */
let pendingPushSeen = false;
/** 跳转语音页时不在此处释放，交由语音页唯一执行（避免双 DELETE 竞态） */
let skipReleaseOnLeave = false;

/* ==================== 情绪分析 ==================== */

/** 打开情绪分析详情弹窗 */
function openEmotionDetail(id: string) {
  emotionDetailTargetId.value = id;
  emotionDetailVisible.value = true;
}

/** 量表卡片动作（与语音对话页共用同一实现，toolId/conversationId 字符串直传保精度） */
const { pendingActActions, handleScaleStart, handleScaleAnswered } = useScaleCardActions(() => currentConversationId.value);

/* ==================== WebSocket（STOMP 推送；失败/断线回退轮询） ==================== */

const stomp = useStompConversation({
  onTransportClose: () => {
    // 连接已断开：订阅随连接失效，清空绑定标记以便重连后重新订阅
    boundConversationId = "";
    // 断线时若仍在等待回复 → 回退轮询兜底
    if (thinking.value && !aiStream.value) {
      waitForAssistant(currentConversationId.value, baselineAssistantCount);
    }
  },
  onStompError: (detail) => {
    console.warn("[stomp] 协议错误：", detail);
  },
});

/** 连接（如需）并订阅当前会话；bind 必须先于发送 */
async function ensureStompBound(conversationId: string): Promise<void> {
  if (!userStore.token || !conversationId || isLocalConversationId(conversationId)) return;
  const wasConnected = stomp.state.value === "connected";
  try {
    const ok = wasConnected ? true : await stomp.connect(8000);
    if (!ok) return;
    // 已连接且已订阅同一会话 → 跳过（避免每次发送都退订/重订）
    if (wasConnected && boundConversationId === conversationId) return;
    bindStomp(conversationId);
  } catch (e) {
    console.warn("[stomp] 连接失败，回退轮询", e);
  }
}

function bindStomp(conversationId: string): void {
  if (!conversationId || isLocalConversationId(conversationId)) return;
  boundConversationId = conversationId;
  // bind 幂等（内部先清旧订阅）
  stomp.bind(conversationId, {
    onTextReply: handleAiChunk,
    onConversationName: handleConversationNamePush,
    onPendingAction: handlePendingActionPush,
    onQuotaRejected: showQuotaRejected,
  });
}

/** AI 文本增量：累积流式展示 + 空闲 2s 后与 memory 对账 */
function handleAiChunk(chunk: string): void {
  if (!chunk) return;
  if (!aiStream.value) {
    reconcileAttempts = 0;
    captureBaseline();
    clearNoChunkTimer();
  }
  aiStream.value += chunk;
  resetAiIdleTimer();
}

/** AI 生成的会话名推送：覆盖列表项名称（本地截断命名仅作首屏占位） */
function handleConversationNamePush(name: string): void {
  if (!name) return;
  const conv = conversationList.value.find((c) => c.id === currentConversationId.value);
  if (conv) conv.name = name;
}

/** 待处理交互推送（量表卡片）：先本地 upsert，再走对账拉取权威数据 */
function handlePendingActionPush(rawJson: string): void {
  const item = parsePendingActionPush(rawJson);
  if (!item) return;
  const idx = pendingActions.value.findIndex((p) => p.id === item.id);
  if (idx >= 0) {
    pendingActions.value[idx] = { ...pendingActions.value[idx], ...item };
  } else {
    pendingActions.value = [...pendingActions.value, item];
  }
  pendingPushSeen = true;
  resetAiIdleTimer();
}

function resetAiIdleTimer(): void {
  if (aiIdleTimer !== null) window.clearTimeout(aiIdleTimer);
  aiIdleTimer = window.setTimeout(() => {
    aiIdleTimer = null;
    void reconcile();
  }, AI_IDLE_MS);
}

/** AI 回复结束（文本空闲/卡片到达）→ memory 对账：以服务端数据整体替换本地流式展示 */
async function reconcile(): Promise<void> {
  const id = currentConversationId.value;
  if (!id || isLocalConversationId(id)) return;
  if (reconcileTimer !== null) {
    window.clearTimeout(reconcileTimer);
    reconcileTimer = null;
  }
  try {
    const memory = await fetchRoundMemory(id);
    const assistantCount = memory.messages.filter((m) => m.type === "assistant").length;
    const pendingCount = memory.pendingActions.length;
    if (assistantCount > baselineAssistantCount || pendingCount > baselinePendingCount) {
      allMessages.value = memory.messages;
      pendingActions.value = memory.pendingActions;
      aiStream.value = "";
      pendingPushSeen = false;
      thinking.value = false;
      clearNoChunkTimer();
      await Promise.allSettled([refreshEmotionList(id), loadConversationList(false)]);
      refreshLatestDiagnosisAfterReply(id);
      return;
    }
    // 仅待处理卡片推送、memory 暂未体现：保留本地 upsert 结果并结束等待
    if (pendingPushSeen && !aiStream.value) {
      pendingPushSeen = false;
      thinking.value = false;
      clearNoChunkTimer();
      await Promise.allSettled([refreshEmotionList(id), loadConversationList(false)]);
      refreshLatestDiagnosisAfterReply(id);
      return;
    }
    scheduleReconcileRetry();
  } catch {
    scheduleReconcileRetry();
  }
}

function scheduleReconcileRetry(): void {
  if (reconcileAttempts < RECONCILE_MAX_RETRY) {
    reconcileAttempts += 1;
    reconcileTimer = window.setTimeout(() => {
      reconcileTimer = null;
      void reconcile();
    }, RECONCILE_RETRY_MS);
    return;
  }
  // 对账耗尽：回退轮询兜底（若仍在等待）
  if (thinking.value) {
    waitForAssistant(currentConversationId.value, baselineAssistantCount);
  }
}

/** WS 已连接但迟迟无首包 → 回退轮询 */
function startNoChunkFallback(conversationId: string): void {
  clearNoChunkTimer();
  noChunkTimer = window.setTimeout(() => {
    noChunkTimer = null;
    if (thinking.value && !aiStream.value && conversationId === currentConversationId.value) {
      waitForAssistant(conversationId, baselineAssistantCount);
    }
  }, NO_CHUNK_FALLBACK_MS);
}

/* ==================== 派生数据 ==================== */

const currentConversation = computed(() => {
  const id = currentConversationId.value;
  // 本地草稿不在列表中，由独立引用提供（保证标题/空态问候语正常）
  if (isLocalConversationId(id)) return draftConversation.value ?? undefined;
  return conversationList.value.find((c) => c.id === id);
});

const displayMessages = computed(() => {
  return allMessages.value
    .filter((m) => m.type === "user" || m.type === "assistant")
    .sort(cmpByTime);
});

const displayPendingActions = computed(() => {
  return [...pendingActions.value].sort((a, b) => b.roundNum - a.roundNum);
});

/** 统一的「当前选中会话」写入（组件 ref 与持久化 store 同步） */
function setCurrentConversation(id: string): void {
  currentConversationId.value = id;
  conversationStore.setCurrentConversation(id);
}

function captureBaseline(): void {
  baselineAssistantCount = allMessages.value.filter((m) => m.type === "assistant").length;
  baselinePendingCount = pendingActions.value.length;
}

/* ==================== API 数据加载 ==================== */

/** 加载历史消息（不触发 init：会话资源初始化只在发送时/语音首次输入时发生） */
async function loadChat(conversationId: string) {
  loadingChat.value = true;
  try {
    const { messages, pendingActions: acts } = await fetchRoundMemory(conversationId);
    allMessages.value = messages;
    pendingActions.value = acts;
    // 切换会话时同步刷新情绪分析面板（失败保留旧数据）
    resetEmotions();
    expandedAnalysisId.value = null;
    await refreshEmotionList(conversationId);
    void refreshLatestDiagnosis(conversationId);
  } catch {
    allMessages.value = [];
    pendingActions.value = [];
    ElMessage.error("对话记录加载失败");
  } finally {
    loadingChat.value = false;
  }
}

/** 刷新情绪分析首屏数据（分页第 1 页） */
async function refreshEmotionList(conversationId: string) {
  const prev = expandedAnalysisId.value;
  try {
    await loadEmotions(conversationId);
    // 保留原展开项；不存在则全部收起（默认不展开最新一轮）
    const list = emotionItems.value;
    expandedAnalysisId.value = prev && list.some((e) => e.id === prev) ? prev : null;
  } catch {
    /* 保留旧数据 */
  }
}

/** 刷新当前会话最新心理评估（评估为消息处理后的异步生成，故支持有界复检） */
async function refreshLatestDiagnosis(conversationId: string, recheck = false): Promise<void> {
  if (!conversationId || isLocalConversationId(conversationId)) {
    latestDiagnosisId.value = null;
    return;
  }
  try {
    const res = await getEmotionDiagnosisListByConversation(conversationId, { pageNum: 1, pageSize: 1 });
    // 会话已切换：丢弃过期响应
    if (currentConversationId.value !== conversationId) return;
    const id = res.data?.data?.records?.[0]?.id;
    latestDiagnosisId.value = id != null ? String(id) : null;
  } catch {
    if (currentConversationId.value === conversationId) latestDiagnosisId.value = null;
  }
  if (recheck && !latestDiagnosisId.value) scheduleDiagnosisRecheck(conversationId);
}

/** AI 回复完成：开一个新的评估复检窗口（后端评估异步生成，可能尚未落库） */
function refreshLatestDiagnosisAfterReply(conversationId: string): void {
  clearDiagnosisRecheck();
  void refreshLatestDiagnosis(conversationId, true);
}

function scheduleDiagnosisRecheck(conversationId: string): void {
  if (diagnosisRecheckTimer !== null || diagnosisRecheckAttempts >= DIAGNOSIS_RECHECK_MAX) return;
  diagnosisRecheckTimer = window.setTimeout(() => {
    diagnosisRecheckTimer = null;
    if (currentConversationId.value !== conversationId) return;
    diagnosisRecheckAttempts += 1;
    void refreshLatestDiagnosis(conversationId, true);
  }, DIAGNOSIS_RECHECK_MS);
}

function clearDiagnosisRecheck(): void {
  if (diagnosisRecheckTimer !== null) {
    window.clearTimeout(diagnosisRecheckTimer);
    diagnosisRecheckTimer = null;
  }
  diagnosisRecheckAttempts = 0;
}

/** 跳转到当前会话最新一条心理评估详情 */
function openLatestDiagnosis(id: string): void {
  const convId = currentConversationId.value;
  if (!convId || isLocalConversationId(convId)) return;
  void router.push(`/diagnosis/${convId}/${id}`);
}

/* ==================== 交互 ==================== */

async function selectConversation(id: string) {
  if (id === currentConversationId.value) return;
  cancelPending();
  aiStream.value = "";
  draftConversation.value = null;
  latestDiagnosisId.value = null;
  const prevId = currentConversationId.value;
  setCurrentConversation(id);
  // 离开的会话：销毁后端资源（本地临时 id 自动跳过）；不 init，发送时才按需初始化
  void releaseConversation(prevId);
  await ensureStompBound(id);
  await loadChat(id);
}

/* ==================== 会话管理 ==================== */

/**
 * 新建会话：销毁当前会话资源 → 展示空白页。
 * 草稿不进入左侧列表（重复点击只覆盖草稿），首次发送拿到真实 id 后才插入列表项。
 * 不调用 init（首条文本才初始化）。
 */
function handleNewConversation() {
  cancelPending();
  aiStream.value = "";
  void releaseConversation(currentConversationId.value);
  const localId = `local-${Date.now()}`;
  draftConversation.value = {
    id: localId,
    name: NEW_CONV_NAME,
    currentRound: 0,
    lastActiveTime: dayjs().format(FORMAT_DATETIME),
    draft: true,
  };
  setCurrentConversation(localId);
  allMessages.value = [];
  resetEmotions();
  expandedAnalysisId.value = null;
  latestDiagnosisId.value = null;
  focusInput();
}

async function handleSend() {
  if (!token.value) {
    userStore.openLoginDialog("login");
    return;
  }
  const text = inputMessage.value.trim();
  if (!text || thinking.value) return;
  let conv = currentConversation.value;
  if (!conv) {
    ElMessage.info("请先新建会话");
    return;
  }

  const optimistic: MemoryMessage = {
    id: `local-${Date.now()}`,
    content: text,
    type: "user",
    roundNum: conv.draft ? 0 : conv.currentRound,
    createdTime: dayjs().format(FORMAT_DATETIME),
  };
  allMessages.value = [...allMessages.value, optimistic];
  inputMessage.value = "";

  thinking.value = true;
  aiStream.value = "";
  pendingPushSeen = false;
  captureBaseline();

  const isDraft = !!conv.draft || isLocalConversationId(conv.id);
  let convId = conv.id;
  /** 本轮为新会话落地并插入列表的 id（失败时用于回滚） */
  let insertedId: string | null = null;

  try {
    // 新会话：首条文本才调用 init（后端据此创建会话并返回真实 id）
    if (isDraft) {
      const realId = await initConversation(undefined, TEXT_MODES);
      if (!realId) throw new Error("新建会话初始化失败");
      // 真正开始对话后，新会话才进入左侧列表（此前草稿不占列表）
      const item: ConversationItem = {
        id: realId,
        name: text.length > 12 ? `${text.slice(0, 12)}…` : text,
        currentRound: 0,
        lastActiveTime: dayjs().format(FORMAT_DATETIME),
        draft: false,
      };
      conversationList.value.unshift(item);
      draftConversation.value = null;
      conv = item;
      convId = realId;
      insertedId = realId;
      setCurrentConversation(realId);
    }

    // 先订阅后发送，避免丢首包
    await ensureStompBound(convId);

    const res = await sendTextWithLifecycle(convId, text, TEXT_MODES);
    const vo = (res?.data?.data ?? {}) as { conversationId?: string; currentRound?: number };
    const respId = String(vo.conversationId ?? "");
    if (respId && respId !== convId) {
      // 后端在未初始化自愈时可能重建会话，回填最新 id
      conv.id = respId;
      convId = respId;
      setCurrentConversation(respId);
      bindStomp(respId);
    }
    if (vo.currentRound != null) conv.currentRound = Number(vo.currentRound);

    // 推送可用：等待流式/对账；否则回退轮询
    if (stomp.state.value === "connected") {
      startNoChunkFallback(convId);
    } else {
      waitForAssistant(convId, baselineAssistantCount);
    }
  } catch (e) {
    // 新会话首条发送失败：撤销列表项与服务端空会话，回到空白草稿（保留输入内容可重试）
    if (insertedId) {
      conversationList.value = conversationList.value.filter((c) => c.id !== insertedId);
      void releaseConversation(insertedId);
      const localId = `local-${Date.now()}`;
      draftConversation.value = {
        id: localId,
        name: NEW_CONV_NAME,
        currentRound: 0,
        lastActiveTime: dayjs().format(FORMAT_DATETIME),
        draft: true,
      };
      setCurrentConversation(localId);
    }
    allMessages.value = allMessages.value.filter((m) => m.id !== optimistic.id);
    thinking.value = false;
    inputMessage.value = text;
    ElMessage.error((e as Error)?.message || "消息发送失败，请稍后再试");
  }
}

/** 轮询兜底：等待 AI 回复落库（WS 不可用/断线/对账耗尽时启用） */
function waitForAssistant(conversationId: string, prevAssistantCount: number) {
  pollAttempts = 0;
  if (pollTimer !== null) window.clearTimeout(pollTimer);

  const tick = async () => {
    if (pollAttempts >= MAX_POLL_TIMES) {
      thinking.value = false;
      ElMessage.info("AI 回复较慢，请稍后刷新查看");
      return;
    }
    try {
      const { messages, pendingActions: acts } = await fetchRoundMemory(conversationId);
      const assistantCount = messages.filter((m) => m.type === "assistant").length;
      // 文本回复到达或待处理交互卡片（量表推荐）新增均视为本轮回合完成
      if (assistantCount > prevAssistantCount || acts.length > baselinePendingCount) {
        allMessages.value = messages;
        pendingActions.value = acts;
        aiStream.value = "";
        thinking.value = false;
        clearNoChunkTimer();
        await Promise.allSettled([refreshEmotionList(conversationId), loadConversationList(false)]);
        refreshLatestDiagnosisAfterReply(conversationId);
        return;
      }
    } catch {
      /* 网络抖动，继续轮询 */
    }
    pollAttempts += 1;
    pollTimer = window.setTimeout(tick, POLL_INTERVAL);
  };

  tick();
}

function cancelPending() {
  thinking.value = false;
  pendingPushSeen = false;
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
  clearAiIdleTimer();
  if (reconcileTimer !== null) {
    window.clearTimeout(reconcileTimer);
    reconcileTimer = null;
  }
  clearNoChunkTimer();
  clearDiagnosisRecheck();
}

function clearAiIdleTimer(): void {
  if (aiIdleTimer !== null) {
    window.clearTimeout(aiIdleTimer);
    aiIdleTimer = null;
  }
}

function clearNoChunkTimer(): void {
  if (noChunkTimer !== null) {
    window.clearTimeout(noChunkTimer);
    noChunkTimer = null;
  }
}

function focusInput() {
  chatPanelRef.value?.focusInput();
}

/* ==================== 会话 CRUD ==================== */

async function handleItemCommand(item: typeof conversationList.value[0], cmd: string) {
  if (cmd === "rename") {
    await handleRename(item);
  } else if (cmd === "delete") {
    const ok = await handleDelete(item);
    if (ok && currentConversationId.value === item.id) {
      cancelPending();
      aiStream.value = "";
      draftConversation.value = null;
      const nextId = conversationList.value[0]?.id ?? "";
      setCurrentConversation(nextId);
      if (nextId) {
        await ensureStompBound(nextId);
        await loadChat(nextId);
      } else {
        allMessages.value = [];
        resetEmotions();
        latestDiagnosisId.value = null;
      }
    }
  }
}

/* ==================== 生命周期 ==================== */

/** bfcache 恢复（非重挂载）：重连并重绑 WS + 拉取一次 memory 对账 */
function handlePageShow(e: PageTransitionEvent) {
  if (!e.persisted) return;
  const id = currentConversationId.value;
  if (!id || isLocalConversationId(id)) return;
  void (async () => {
    await ensureStompBound(id);
    try {
      const memory = await fetchRoundMemory(id);
      allMessages.value = memory.messages;
      pendingActions.value = memory.pendingActions;
    } catch {
      /* ignore */
    }
  })();
}

onMounted(() => {
  window.addEventListener("pageshow", handlePageShow);
  if (token.value) void initPage();
  // 游客：无会话列表请求，直接结束加载态展示空态（避免骨架常驻）
  else loadingConversations.value = false;
});

watch(
  [token, loginSuccessVersion],
  ([hasToken], [_oldToken, oldSuccessVersion]) => {
    if (!hasToken) return;
    if (!oldSuccessVersion && loginSuccessVersion.value === 0) return;
    void initPage();
  },
);

async function initPage() {
  loadingConversations.value = true;
  try {
    const list = await loadConversationList(false);
    conversationStore.setConversationList(list as any);
    // 恢复持久化的选中会话；不在列表则回退第一条（不 init，发送时才初始化）
    let target = conversationStore.currentConversationId;
    if (!target || !list.some((c) => c.id === target)) {
      target = list[0]?.id ?? "";
    }
    if (target) {
      setCurrentConversation(target);
      await ensureStompBound(target);
      await loadChat(target);
    }
  } catch {
    ElMessage.error("会话列表加载失败");
  } finally {
    loadingConversations.value = false;
  }
}

onBeforeRouteLeave((to) => {
  // 经语音按钮携 fromText 跳转：资源释放交由语音页唯一执行，避免双 DELETE 竞态
  if (to.name === "PortalConversationAudio" && to.query.fromText === "1") skipReleaseOnLeave = true;
  return true;
});

onBeforeUnmount(() => {
  window.removeEventListener("pageshow", handlePageShow);
  cancelPending();
  void stomp.dispose();
  if (!skipReleaseOnLeave) {
    void releaseConversation(currentConversationId.value);
  }
});
</script>

<style scoped>
.conversation-page {
  height: calc(100vh - var(--portal-header-height));
  display: flex;
  overflow: hidden;
  background: #f8f9fc;
}

/* 左右侧栏拖拽分隔条：5px 热区，拖拽/悬停高亮 */
.resize-bar {
  width: 5px;
  flex-shrink: 0;
  cursor: col-resize;
  touch-action: none;
  background: transparent;
  transition: background 0.15s ease;
}

.resize-bar:hover {
  background: rgba(108, 99, 255, 0.28);
}

.conversation-page.resizing .resize-bar {
  background: rgba(108, 99, 255, 0.5);
}

/* 拖拽中禁用面板宽度过渡，保证实时跟随光标 */
.conversation-page.resizing :deep(.conversation-list-panel),
.conversation-page.resizing :deep(.emotion-panel) {
  transition: none !important;
}

/* 拖拽期间：禁止文本选区 + 全局拉伸光标 */
:global(body.is-resizing) {
  user-select: none;
  cursor: col-resize;
}
</style>
