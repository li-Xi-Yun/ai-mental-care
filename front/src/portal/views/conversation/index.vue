<template>
  <div class="conversation-page">
    <!-- 左侧：会话列表 -->
    <aside class="conversation-list-panel" :style="listPanelStyle">
      <div class="sl-header">
        <span class="sl-title">会话列表</span>
        <button class="new-btn" title="新建会话" @click="handleNewConversation">
          <el-icon :size="18"><Plus /></el-icon>
        </button>
      </div>

      <div class="sl-body">
        <template v-if="loadingConversations">
          <div class="list-skeleton">
            <div class="sk-row" v-for="i in 4" :key="i">
              <div class="sk-avatar"></div>
              <div class="sk-text">
                <div class="sk-line" style="width: 80%"></div>
                <div class="sk-line" style="width: 45%"></div>
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="conversationList.length">
          <div
            v-for="item in conversationList"
            :key="item.id"
            class="sl-item"
            :class="{ active: currentConversationId === item.id }"
            @click="selectConversation(item.id)"
          >
            <div class="sl-icon">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="sl-main">
              <div class="sl-name">{{ item.name }}</div>
              <div class="sl-meta">
                <span v-if="item.currentRound" class="sl-round">第{{ item.currentRound }}轮</span>
                <span class="sl-time">{{ listTime(item.lastActiveTime) }}</span>
              </div>
            </div>
            <el-dropdown trigger="click" @command="(cmd: string) => handleItemCommand(item, cmd)">
              <span class="sl-more" @click.stop>
                <el-icon><More /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    <el-icon><EditPen /></el-icon>重命名
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    <el-icon><Delete /></el-icon>删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>

        <div v-else class="list-empty">
          <p>还没有会话</p>
          <el-button type="primary" size="small" round @click="handleNewConversation">开启第一段倾诉</el-button>
        </div>
      </div>
    </aside>

    <!-- 中间：聊天区 -->
    <main class="chat-panel">
      <header class="ca-header">
        <div class="panel-toggle left" :class="{ hidden: showConversationList }" title="收起/展开会话列表" @click="showConversationList = !showConversationList">
          <el-icon :size="16"><Operation /></el-icon>
        </div>
        <div class="ca-header-center">
          <div class="ca-title">{{ currentConversation?.name || "AI 对话" }}</div>
          <div class="ca-disclaimer">AI 生成内容仅供参考，请勿过度依赖</div>
        </div>
        <div class="panel-toggle right" :class="{ hidden: showEmotionPanel }" title="收起/展开情绪分析" @click="showEmotionPanel = !showEmotionPanel">
          <el-icon :size="16"><DataAnalysis /></el-icon>
        </div>
      </header>

      <div class="ca-body" ref="chatBodyRef">
        <!-- 空态问候 -->
        <div v-if="!loadingChat && !displayMessages.length" class="chat-greeting">
          <div class="greeting-logo">
            <el-icon :size="26"><ChatDotRound /></el-icon>
          </div>
          <h3 class="greeting-title">{{ currentConversation?.draft ? "开始新的对话" : currentConversation?.name || "AI 心理助手" }}</h3>
          <p class="greeting-text">我是你的 AI 心理健康助手。你可以向我倾诉最近的烦恼、压力或任何心事，我会一直在这里倾听。</p>
          <p class="greeting-tip">AI 生成内容仅供参考，请勿过度依赖。</p>
          <el-button type="primary" round @click="focusInput">开始倾诉</el-button>
        </div>

        <!-- 加载骨架 -->
        <div v-if="loadingChat" class="chat-skeleton">
          <div class="sk-row ai">
            <div class="sk-avatar"></div>
            <div class="sk-line" style="width: 46%"></div>
          </div>
          <div class="sk-row user">
            <div class="sk-line" style="width: 38%"></div>
          </div>
          <div class="sk-row ai">
            <div class="sk-avatar"></div>
            <div class="sk-line" style="width: 62%"></div>
          </div>
        </div>

        <!-- 消息气泡 -->
        <div
          v-for="msg in displayMessages"
          :key="msg.id"
          class="msg"
          :class="msg.type === 'user' ? 'user' : 'ai'"
        >
          <template v-if="msg.type === 'assistant'">
            <div class="msg-avatar ai">AI</div>
            <div class="msg-content">
              <div class="msg-bubble ai">{{ msg.content }}</div>
              <div class="msg-time">{{ msgTime(msg.createdTime) }}</div>
            </div>
          </template>
          <template v-else>
            <div class="msg-content right">
              <div class="msg-bubble user">{{ msg.content }}</div>
              <div class="msg-time">{{ msgTime(msg.createdTime) }}</div>
            </div>
          </template>
        </div>

        <!-- 思考动画 -->
        <div v-if="thinking" class="msg ai">
          <div class="msg-avatar ai">AI</div>
          <div class="typing-indicator">
            <span class="typing-text">正在思考</span>
            <span class="typing-dots"><i></i><i></i><i></i></span>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="ca-input-wrapper">
        <div class="ca-input" ref="inputBoxRef">
          <div class="input-upper">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="1"
              :autosize="{ minRows: 1, maxRows: 4 }"
              resize="none"
              placeholder="输入你的想法…"
              @keydown.enter.exact.prevent="handleSend"
            />
          </div>
          <div class="input-lower">
            <button class="func-btn" title="语音对话（开发中）" @click="showComingSoon">语音对话</button>
            <button class="func-btn" title="视频对话（开发中）" @click="showComingSoon">视频对话</button>
            <span class="spacer"></span>
            <button class="send-btn" :disabled="!inputMessage.trim() || thinking" title="发送" @click="handleSend">
              <el-icon :size="18"><Promotion /></el-icon>
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- 右侧：情绪分析面板 -->
    <aside class="emotion-panel" :style="emotionPanelStyle">
      <div class="ep-header">
        <span class="ep-title">情绪分析</span>
        <span class="ep-close" title="收起面板" @click="showEmotionPanel = false">
          <el-icon><Close /></el-icon>
        </span>
      </div>

      <div class="ep-body">
        <template v-if="currentEmotion">
          <!-- 当前情绪 -->
          <section class="ep-section">
            <div class="ep-section-title">当前情绪</div>
            <div class="emo-current-card">
              <div class="emo-ring-box">
                <svg viewBox="0 0 100 100" class="ring-svg">
                  <circle cx="50" cy="50" r="40" fill="none" stroke="#f0f0f6" stroke-width="9" />
                  <circle
                    v-for="seg in ringSegments"
                    :key="seg.label"
                    cx="50"
                    cy="50"
                    r="40"
                    fill="none"
                    :stroke="seg.color"
                    stroke-width="9"
                    :stroke-dasharray="seg.dasharray"
                    :stroke-dashoffset="seg.dashoffset"
                    stroke-linecap="round"
                  />
                </svg>
                <div class="ring-center">
                  <span class="ring-label">核心情绪</span>
                  <strong class="ring-value">{{ zhOf(currentEmotion.emotionLabel) }}</strong>
                  <span class="ring-sub">{{ toPct(currentEmotion.emotionConfidence, 0) }} 置信</span>
                </div>
              </div>
              <div class="emo-legend">
                <div class="legend-item" v-for="seg in ratioSegments" :key="seg.label">
                  <span class="dot" :style="{ background: seg.color }"></span>
                  <span class="legend-label">{{ seg.label }}</span>
                  <span class="legend-pct">{{ seg.pct }}%</span>
                </div>
              </div>
            </div>

            <div class="emo-tag-row">
              <span class="emotion-chip" :style="chipStyle(currentEmotion.emotionLabel, 0)">
                {{ zhOf(currentEmotion.emotionLabel) }}
              </span>
              <span v-if="currentEmotion.emotionSubLabel" class="emo-sub-tag">{{ currentEmotion.emotionSubLabel }}</span>
              <span class="trend-chip" :class="trendMeta(currentEmotion.emotionTrend).type">
                {{ trendMeta(currentEmotion.emotionTrend).icon }} {{ trendMeta(currentEmotion.emotionTrend).text }}
              </span>
            </div>
          </section>

          <!-- PAD 三维情绪 -->
          <section class="ep-section">
            <div class="ep-section-title">PAD 三维情绪</div>
            <div class="pad-row" v-for="pad in buildPadBars(currentEmotion)" :key="pad.key">
              <span class="pad-label">{{ pad.label }}</span>
              <div class="pad-track">
                <span class="pad-zero"></span>
                <span class="pad-fill" :style="padBarStyle(pad)"></span>
              </div>
              <span class="pad-val" :style="{ color: pad.color }">{{ pad.text }}</span>
            </div>
          </section>

          <!-- 情绪分布 -->
          <section class="ep-section">
            <div class="ep-section-title">情绪倾向分布</div>
            <div class="ratio-bar">
              <span
                v-for="seg in ratioSegments"
                :key="seg.label"
                class="ratio-seg"
                :style="{ flex: seg.flex, background: seg.color }"
                :title="`${seg.label} ${seg.pct}%`"
              ></span>
            </div>
            <div class="ratio-labels">
              <span v-for="seg in ratioSegments" :key="seg.label" :style="{ color: seg.color }">
                {{ seg.label }} {{ seg.pct }}%
              </span>
            </div>
          </section>

          <!-- 关键词 -->
          <section class="ep-section" v-if="currentKeywords.length">
            <div class="ep-section-title">关键词</div>
            <div class="kw-tags">
              <span v-for="(kw, i) in currentKeywords" :key="kw" class="kw-tag" :style="kwTagStyle(i)">
                {{ kw }}
              </span>
            </div>
          </section>

          <!-- 分析详情 -->
          <section class="ep-section" v-if="currentEmotion.analysisContent">
            <div class="ep-section-title">分析详情</div>
            <div class="emo-analysis">{{ currentEmotion.analysisContent }}</div>
          </section>
        </template>

        <el-empty v-else description="暂无情绪分析，多聊几句试试" :image-size="72" />

        <!-- 历史情绪 -->
        <template v-if="emotionList.length > 1">
          <section class="ep-section">
            <div class="ep-section-title">历史情绪</div>
            <div class="emo-item" v-for="(item, index) in emotionList" :key="item.id" :class="{ expanded: expandedAnalysisId === item.id }" @click="toggleAnalysis(item.id)">
              <div class="ei-header">
                <span class="ei-round">第{{ item.roundNum }}轮</span>
                <span class="emotion-chip" :style="chipStyle(item.emotionLabel, index)">{{ zhOf(item.emotionLabel) }}</span>
                <span v-if="item.emotionSubLabel" class="ei-sub">{{ item.emotionSubLabel }}</span>
                <span class="ei-time">{{ msgTime(item.createdTime) }}</span>
                <span class="ei-arrow">▼</span>
              </div>
              <div class="ei-body" v-show="expandedAnalysisId === item.id">
                <div class="ratio-bar small">
                  <span
                    v-for="seg in ratioSegsOf(item)"
                    :key="seg.label"
                    class="ratio-seg"
                    :style="{ flex: seg.flex, background: seg.color }"
                    :title="`${seg.label} ${seg.pct}%`"
                  ></span>
                </div>
                <div class="ratio-labels small">
                  <span v-for="seg in ratioSegsOf(item)" :key="seg.label" :style="{ color: seg.color }">
                    {{ seg.label }} {{ seg.pct }}%
                  </span>
                </div>
                <div class="pad-row" v-for="pad in buildPadBars(item)" :key="pad.key">
                  <span class="pad-label">{{ pad.label }}</span>
                  <div class="pad-track">
                    <span class="pad-zero"></span>
                    <span class="pad-fill" :style="padBarStyle(pad)"></span>
                  </div>
                  <span class="pad-val" :style="{ color: pad.color }">{{ pad.text }}</span>
                </div>
                <div class="emo-meta">
                  <span><em>置信度</em> {{ toPct(item.emotionConfidence, 0) }}</span>
                  <span><em>强度</em> {{ toPct(item.emotionIntensity, 0) }}</span>
                  <span :class="trendMeta(item.emotionTrend).type">
                    {{ trendMeta(item.emotionTrend).icon }} {{ trendMeta(item.emotionTrend).text }}
                  </span>
                </div>
                <div class="emo-analysis" v-if="item.analysisContent">{{ item.analysisContent }}</div>
              </div>
            </div>
          </section>
        </template>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Operation, DataAnalysis, Close, Delete, EditPen, More, ChatDotRound, Promotion } from "@element-plus/icons-vue";
import { EMOTION_COLORS, CONVERSATION_LIST_WIDTH, EMOTION_PANEL_WIDTH } from "@/shared/api/config";
import { getConversationList, updateConversationName, deleteConversation } from "@/portal/api/conversation/conversation";
import { getConversationMemory } from "@/portal/api/conversation/dialogue";
import { getEmotionAnalysisList } from "@/portal/api/conversation/emotion-analysis";
import { sendUserMessage } from "@/portal/api/conversation/ai-chat";
import { useConversationStore } from "@/portal/stores/conversation";
import dayjs, { FORMAT_DATETIME } from "@/shared/utils/dayjs";

/* ==================== 类型 ==================== */

interface ConversationItem {
  id: string;
  name: string;
  currentRound: number;
  lastActiveTime: string;
  draft?: boolean;
}

interface MemoryMessage {
  id: string;
  content: string;
  type: string;
  roundNum: number;
  createdTime: string;
}

interface EmotionAnalysisItem {
  id: string;
  roundNum: number;
  analysisContent?: string;
  emotionLabel?: string;
  emotionSubLabel?: string;
  emotionConfidence?: number;
  emotionIntensity?: number;
  emotionTrend?: string;
  pScore?: number;
  aScore?: number;
  dScore?: number;
  negativeEmotionRatio?: number;
  neutralEmotionRatio?: number;
  positiveEmotionRatio?: number;
  createdTime?: string;
}

interface RatioSeg {
  label: string;
  flex: number;
  pct: string;
  color: string;
}

interface PadBar {
  key: string;
  label: string;
  width: number;
  left: number;
  color: string;
  text: string;
}

interface UserMessageSendVO {
  conversationId?: number;
  currentRound?: number;
}

/* ==================== 常量 ==================== */

const NEW_CONV_NAME = "新对话";
const MAX_POLL_TIMES = 40;
const POLL_INTERVAL = 1600;
const RING_C = 2 * Math.PI * 40;
const FALLBACK_PALETTE = ["#6c63ff", "#ff6b6b", "#fa8c16", "#52c41a", "#1890ff", "#13c2c2", "#722ed1", "#eb2f96"];

const LABEL_ZH: Record<string, string> = {
  anger: "愤怒",
  sadness: "悲伤",
  fear: "恐惧",
  anxiety: "焦虑",
  disgust: "厌恶",
  surprise: "惊讶",
  happy: "开心",
  neutral: "中性",
  guilt: "内疚",
  shame: "羞耻",
  hope: "希望",
  confusion: "困惑",
};

const TREND_META: Record<string, { icon: string; text: string; type: "up" | "down" | "stable" }> = {
  escalating: { icon: "↑", text: "较上轮升级", type: "up" },
  deescalating: { icon: "↓", text: "较上轮缓和", type: "down" },
  stable: { icon: "→", text: "与上轮持平", type: "stable" },
  fluctuating: { icon: "↕", text: "波动明显", type: "up" },
  initial: { icon: "·", text: "首轮无对比", type: "stable" },
};

const STOPWORDS = new Set([
  "的", "了", "我", "你", "他", "她", "它", "我们", "你们", "他们", "这个", "那个", "就是", "现在",
  "最近", "因为", "所以", "但是", "如果", "然后", "而且", "一个", "什么", "怎么", "感觉", "觉得",
  "有些", "一点", "还是", "可以", "没有", "真的", "时候", "来说", "对", "让", "把", "被", "在",
  "和", "与", "也", "都", "很", "太", "比较", "会", "想", "要", "能", "不", "是", "有", "吗", "吧",
  "啊", "呢", "哈", "嗯", "哦",
]);

/* ==================== 布局状态 ==================== */

const showConversationList = ref(true);
const showEmotionPanel = ref(true);

const listPanelStyle = computed(() => ({
  width: showConversationList.value ? `${CONVERSATION_LIST_WIDTH}px` : "0px",
  opacity: showConversationList.value ? 1 : 0,
}));

const emotionPanelStyle = computed(() => ({
  width: showEmotionPanel.value ? `${EMOTION_PANEL_WIDTH}px` : "0px",
  opacity: showEmotionPanel.value ? 1 : 0,
}));

/* ==================== 数据状态 ==================== */

const conversationStore = useConversationStore();

const conversationList = ref<ConversationItem[]>([]);
const currentConversationId = ref("");
const allMessages = ref<MemoryMessage[]>([]);
const emotionList = ref<EmotionAnalysisItem[]>([]);
const expandedAnalysisId = ref<string | null>(null);

const inputMessage = ref("");
const loadingConversations = ref(true);
const loadingChat = ref(false);
const thinking = ref(false);

const chatBodyRef = ref<HTMLElement | null>(null);
const inputBoxRef = ref<HTMLElement | null>(null);

let pollTimer: number | null = null;
let pollAttempts = 0;

/* ==================== 派生数据 ==================== */

const currentConversation = computed(() => conversationList.value.find((c) => c.id === currentConversationId.value));

const currentEmotion = computed(() => emotionList.value[0] ?? null);

const displayMessages = computed(() => {
  return allMessages.value
    .filter((m) => m.type === "user" || m.type === "assistant")
    .sort(cmpByTime);
});

const ringSegments = computed(() => {
  const e = currentEmotion.value;
  if (!e) return [];
  const values = [toNumber(e.negativeEmotionRatio), toNumber(e.neutralEmotionRatio), toNumber(e.positiveEmotionRatio)].map((v) =>
    v === null ? 0 : clamp01(v),
  );
  const total = values.reduce((a, b) => a + b, 0);
  if (total <= 0) return [];
  const metas = [
    { label: "负向", color: "#ff6b6b" },
    { label: "中性", color: "#6c63ff" },
    { label: "正向", color: "#52c41a" },
  ];
  let acc = 0;
  return metas
    .map((meta, i) => {
      const pctVal = values[i] / total;
      const len = RING_C * pctVal;
      const start = acc * RING_C;
      acc += pctVal;
      return {
        ...meta,
        dasharray: `${len} ${RING_C - len}`,
        dashoffset: -start,
        pct: (pctVal * 100).toFixed(0),
        flex: values[i],
      };
    })
    .filter((s) => s.flex > 0);
});

const ratioSegments = computed(() => ratioSegsOf(currentEmotion.value));

const currentKeywords = computed(() => {
  const e = currentEmotion.value;
  let texts = allMessages.value
    .filter((m) => m.type === "user" && (e && e.roundNum ? m.roundNum === e.roundNum : true))
    .map((m) => m.content);
  if (!texts.length) {
    texts = allMessages.value.filter((m) => m.type === "user").slice(-3).map((m) => m.content);
  }
  return extractKeywords(texts, 8);
});

watch(displayMessages, () => scrollToBottom());

/* ==================== 数值工具 ==================== */

function toNumber(v?: number | null): number | null {
  if (v === null || v === undefined) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function clamp01(v: number): number {
  return Math.min(1, Math.max(0, v));
}

function toPct(v?: number | null, digits = 0): string {
  const n = toNumber(v);
  return n === null ? "--" : `${(n * 100).toFixed(digits)}%`;
}

function cmpByTime(a: MemoryMessage, b: MemoryMessage): number {
  const ta = dayjs(a.createdTime);
  const tb = dayjs(b.createdTime);
  const va = ta.isValid() ? ta.valueOf() : 0;
  const vb = tb.isValid() ? tb.valueOf() : 0;
  return va - vb;
}

/* ==================== 时间格式化 ==================== */

function listTime(v?: string): string {
  if (!v) return "刚刚";
  const d = dayjs(v);
  if (!d.isValid()) return String(v);
  const now = dayjs();
  if (d.isSame(now, "day")) return d.format("HH:mm");
  if (d.isSame(now.subtract(1, "day"), "day")) return "昨天";
  return d.format("M月D日");
}

function msgTime(v?: string): string {
  if (!v) return "";
  const d = dayjs(v);
  if (!d.isValid()) return String(v);
  const now = dayjs();
  if (d.isSame(now, "day")) return d.format("HH:mm");
  if (d.isSame(now.subtract(1, "day"), "day")) return `昨天 ${d.format("HH:mm")}`;
  return d.format("M月D日 HH:mm");
}

/* ==================== 情绪展示工具 ==================== */

function zhOf(label?: string): string {
  if (!label) return "—";
  return LABEL_ZH[label] ?? label;
}

function emotionColor(label?: string, index = 0): string {
  if (!label) return FALLBACK_PALETTE[index % FALLBACK_PALETTE.length];
  const zh = LABEL_ZH[label] ?? label;
  return EMOTION_COLORS[zh] ?? FALLBACK_PALETTE[index % FALLBACK_PALETTE.length];
}

function chipStyle(label?: string, index = 0) {
  const color = emotionColor(label, index);
  return { color, background: `${color}1a`, borderColor: `${color}38` };
}

function kwTagStyle(index: number) {
  const colors = ["#6c63ff", "#1890ff", "#13c2c2", "#52c41a", "#fa8c16", "#722ed1", "#eb2f96", "#ff6b6b"];
  const color = colors[index % colors.length];
  return { color, background: `${color}12` };
}

function trendMeta(trend?: string): { icon: string; text: string; type: "up" | "down" | "stable" } {
  if (!trend) return { icon: "·", text: "暂无趋势", type: "stable" };
  return TREND_META[trend] ?? { icon: "·", text: trend, type: "stable" };
}

function ratioSegsOf(e: EmotionAnalysisItem | null): RatioSeg[] {
  if (!e) return [];
  const values = [toNumber(e.negativeEmotionRatio), toNumber(e.neutralEmotionRatio), toNumber(e.positiveEmotionRatio)].map((v) =>
    v === null ? 0 : clamp01(v),
  );
  const total = values.reduce((a, b) => a + b, 0) || 1;
  const metas = [
    { label: "负向", color: "#ff6b6b" },
    { label: "中性", color: "#6c63ff" },
    { label: "正向", color: "#52c41a" },
  ];
  return metas.map((meta, i) => {
    const flex = total > 0 ? values[i] : 0;
    return { ...meta, flex, pct: ((flex / total) * 100).toFixed(0) };
  });
}

function buildPadBars(e: EmotionAnalysisItem | null): PadBar[] {
  if (!e) return [];
  const specs = [
    { key: "pScore", label: "愉悦度 P", posColor: "#6c63ff", negColor: "#ff6b6b" },
    { key: "aScore", label: "唤醒度 A", posColor: "#fa8c16", negColor: "#fa8c16" },
    { key: "dScore", label: "支配度 D", posColor: "#52c41a", negColor: "#ff4d4f" },
  ] as const;
  return specs.map((s) => {
    const n = toNumber(e[s.key]);
    if (n === null) return { key: s.key, label: s.label, width: 0, left: 50, color: "#dfe3ea", text: "—" };
    const width = Math.min(50, Math.abs(n) * 50);
    return {
      key: s.key,
      label: s.label,
      width,
      left: n >= 0 ? 50 : 50 - width,
      color: n >= 0 ? s.posColor : s.negColor,
      text: `${n > 0 ? "+" : ""}${n.toFixed(2)}`,
    };
  });
}

function padBarStyle(pad: PadBar) {
  return { left: `${pad.left}%`, width: `${pad.width}%`, backgroundColor: pad.color };
}

function extractKeywords(texts: string[], limit = 8): string[] {
  const freq = new Map<string, number>();
  for (const text of texts) {
    const tokens = text
      .replace(/[，。！？、；：,.!?;:"'“”‘’（）()【】\[\]·\s\n]/g, " ")
      .split(" ")
      .map((s) => s.trim())
      .filter((s) => s.length >= 2 && !STOPWORDS.has(s));
    for (const w of tokens) {
      freq.set(w, (freq.get(w) ?? 0) + 1);
    }
  }
  return [...freq.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map((entry) => entry[0]);
}

/* ==================== API 数据加载 ==================== */

function unwrapList(res: any): any[] {
  const body = res?.data?.data;
  if (Array.isArray(body)) return body;
  if (body && Array.isArray(body.records)) return body.records;
  return [];
}

async function fetchMemory(conversationId: string): Promise<MemoryMessage[]> {
  const res = await getConversationMemory(conversationId, { pageNum: 1, pageSize: 200 });
  return unwrapList(res).map((m: any) => ({
    id: String(m.id),
    content: String(m.content ?? ""),
    type: String(m.type ?? "").toLowerCase(),
    roundNum: Number(m.roundNum ?? 0),
    createdTime: m.createdTime ?? "",
  }));
}

async function fetchEmotions(conversationId: string): Promise<EmotionAnalysisItem[]> {
  const res = await getEmotionAnalysisList(conversationId, { pageNum: 1, pageSize: 100 });
  return unwrapList(res).map((e: any) => ({
    id: String(e.id),
    roundNum: Number(e.roundNum ?? 0),
    analysisContent: e.analysisContent ?? "",
    emotionLabel: e.emotionLabel ?? "",
    emotionSubLabel: e.emotionSubLabel ?? "",
    emotionConfidence: toNumber(e.emotionConfidence) ?? undefined,
    emotionIntensity: toNumber(e.emotionIntensity) ?? undefined,
    emotionTrend: e.emotionTrend ?? "",
    pScore: toNumber(e.pScore) ?? undefined,
    aScore: toNumber(e.aScore) ?? undefined,
    dScore: toNumber(e.dScore) ?? undefined,
    negativeEmotionRatio: toNumber(e.negativeEmotionRatio) ?? undefined,
    neutralEmotionRatio: toNumber(e.neutralEmotionRatio) ?? undefined,
    positiveEmotionRatio: toNumber(e.positiveEmotionRatio) ?? undefined,
    createdTime: e.createdTime ?? "",
  }));
}

async function loadConversationList(selectFirst: boolean) {
  loadingConversations.value = true;
  try {
    const res = await getConversationList({ pageNum: 1, pageSize: 100 });
    conversationList.value = unwrapList(res).map((c: any) => ({
      id: String(c.id),
      name: String(c.name ?? NEW_CONV_NAME),
      currentRound: Number(c.currentRound ?? 0),
      lastActiveTime: c.lastActiveTime ?? c.createdTime ?? "",
      draft: false,
    }));
    conversationStore.setConversationList(conversationList.value as any);

    if (selectFirst) {
      let target = conversationStore.currentConversationId;
      if (!target || !conversationList.value.some((c) => c.id === target)) {
        target = conversationList.value[0]?.id ?? "";
      }
      if (target) {
        currentConversationId.value = target;
        await loadChat(target);
      } else {
        currentConversationId.value = "";
        allMessages.value = [];
        emotionList.value = [];
      }
    } else if (currentConversationId.value && !conversationList.value.some((c) => c.id === currentConversationId.value)) {
      const nextId = conversationList.value[0]?.id ?? "";
      currentConversationId.value = nextId;
      if (nextId) await loadChat(nextId);
      else {
        allMessages.value = [];
        emotionList.value = [];
      }
    }
  } catch {
    ElMessage.error("会话列表加载失败");
  } finally {
    loadingConversations.value = false;
  }
}

async function loadChat(conversationId: string) {
  loadingChat.value = true;
  try {
    allMessages.value = await fetchMemory(conversationId);
    await refreshEmotionList(conversationId);
  } catch {
    allMessages.value = [];
    ElMessage.error("对话记录加载失败");
  } finally {
    loadingChat.value = false;
    scrollToBottom();
  }
}

async function refreshEmotionList(conversationId: string) {
  try {
    emotionList.value = await fetchEmotions(conversationId);
    expandedAnalysisId.value = emotionList.value[0]?.id ?? null;
  } catch {
    /* 保留旧数据 */
  }
}

/* ==================== 交互 ==================== */

async function selectConversation(id: string) {
  if (id === currentConversationId.value) return;
  cancelPending();
  currentConversationId.value = id;
  conversationStore.setCurrentConversation(id);
  await loadChat(id);
}

function handleNewConversation() {
  cancelPending();
  const draft: ConversationItem = {
    id: `local-${Date.now()}`,
    name: NEW_CONV_NAME,
    currentRound: 0,
    lastActiveTime: dayjs().format(FORMAT_DATETIME),
    draft: true,
  };
  conversationList.value.unshift(draft);
  currentConversationId.value = draft.id;
  conversationStore.setCurrentConversation(draft.id);
  allMessages.value = [];
  emotionList.value = [];
  expandedAnalysisId.value = null;
  focusInput();
}

async function handleSend() {
  const text = inputMessage.value.trim();
  if (!text || thinking.value) return;
  const conv = currentConversation.value;
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
  scrollToBottom();

  thinking.value = true;
  const prevAssistantCount = allMessages.value.filter((m) => m.type === "assistant").length;

  try {
    const res = await sendUserMessage({
      message: text,
      ...(conv.draft ? {} : { conversationId: conv.id }),
    });
    const vo = (res?.data?.data ?? {}) as UserMessageSendVO;
    const realId = String(vo.conversationId ?? "");

    if (conv.draft && realId) {
      conv.id = realId;
      conv.draft = false;
      conv.currentRound = Number(vo.currentRound ?? 0);
      if (conv.name === NEW_CONV_NAME) {
        conv.name = text.length > 12 ? `${text.slice(0, 12)}…` : text;
      }
      conversationStore.setCurrentConversation(realId);
    } else if (vo.currentRound != null && !conv.draft) {
      conv.currentRound = Number(vo.currentRound);
    }

    if (conv.draft && !realId) {
      thinking.value = false;
      ElMessage.info("已发送，稍后可在会话列表查看");
      await loadConversationList(true);
      return;
    }

    waitForAssistant(realId || conv.id, prevAssistantCount);
  } catch {
    allMessages.value = allMessages.value.filter((m) => m.id !== optimistic.id);
    thinking.value = false;
    inputMessage.value = text;
    ElMessage.error("消息发送失败，请稍后再试");
  }
}

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
      const mem = await fetchMemory(conversationId);
      const assistantCount = mem.filter((m) => m.type === "assistant").length;
      if (assistantCount > prevAssistantCount) {
        allMessages.value = mem;
        thinking.value = false;
        scrollToBottom();
        await Promise.allSettled([refreshEmotionList(conversationId), loadConversationList(false)]);
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
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function toggleAnalysis(id: string) {
  expandedAnalysisId.value = expandedAnalysisId.value === id ? null : id;
}

function focusInput() {
  nextTick(() => {
    inputBoxRef.value?.querySelector("textarea")?.focus();
  });
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatBodyRef.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

function showComingSoon() {
  ElMessage.info("该功能即将上线，敬请期待");
}

/* ==================== 会话管理 ==================== */

async function handleItemCommand(item: ConversationItem, cmd: string) {
  if (cmd === "rename") await handleRename(item);
  else if (cmd === "delete") await handleDelete(item);
}

async function handleRename(item: ConversationItem) {
  if (item.draft) return;
  let newName = "";
  try {
    const { value } = await ElMessageBox.prompt("请输入新的会话名称", "重命名会话", {
      confirmButtonText: "保存",
      cancelButtonText: "取消",
      inputValue: item.name,
      inputValidator: (v: string) => (v && v.trim() ? true : "名称不能为空"),
    });
    newName = value.trim();
  } catch {
    return;
  }
  try {
    await updateConversationName(item.id, newName);
    item.name = newName;
    ElMessage.success("会话已重命名");
  } catch {
    ElMessage.error("重命名失败，请稍后重试");
  }
}

async function handleDelete(item: ConversationItem) {
  if (item.draft) {
    removeConversationLocally(item.id);
    return;
  }
  try {
    await ElMessageBox.confirm("删除后该会话及关联数据不可恢复，确定删除吗？", "删除会话", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
    });
  } catch {
    return;
  }
  try {
    await deleteConversation(item.id);
    removeConversationLocally(item.id);
    ElMessage.success("会话已删除");
  } catch {
    ElMessage.error("删除失败，请稍后重试");
  }
}

async function removeConversationLocally(id: string) {
  conversationList.value = conversationList.value.filter((c) => c.id !== id);
  conversationStore.setConversationList(conversationList.value as any);
  if (currentConversationId.value === id) {
    const nextId = conversationList.value[0]?.id ?? "";
    currentConversationId.value = nextId;
    if (nextId) {
      conversationStore.setCurrentConversation(nextId);
      await loadChat(nextId);
    } else {
      conversationStore.setCurrentConversation("");
      allMessages.value = [];
      emotionList.value = [];
    }
  }
}

/* ==================== 生命周期 ==================== */

onMounted(() => {
  loadConversationList(true);
});

onBeforeUnmount(() => {
  cancelPending();
});
</script>

<style scoped>
.conversation-page {
  height: calc(100vh - v-bind(PORTAL_HEADER_HEIGHT + 'px'));
  display: flex;
  overflow: hidden;
  background: #f8f9fc;
}

/* ==================== 会话列表 ==================== */

.conversation-list-panel {
  background: #fff;
  border-right: 1px solid #e5e7f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
  transition: width 0.25s ease, opacity 0.25s ease;
}

.sl-header {
  padding: 16px 16px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f1f7;
  flex-shrink: 0;
}

.sl-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e1e2e;
}

.new-btn {
  cursor: pointer;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #6c63ff, #3f3d9e);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  font-family: inherit;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(108, 99, 255, 0.3);
}

.new-btn:hover {
  transform: rotate(90deg) scale(1.05);
  box-shadow: 0 4px 12px rgba(108, 99, 255, 0.4);
}

.sl-body {
  flex: 1;
  overflow-y: auto;
  padding: 6px 10px 10px;
}

.sl-body::-webkit-scrollbar {
  width: 4px;
}

.sl-body::-webkit-scrollbar-thumb {
  background: #dddde8;
  border-radius: 4px;
}

.sl-item {
  padding: 10px 10px 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 2px;
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  transition: background 0.18s ease, box-shadow 0.18s ease;
}

.sl-item:hover {
  background: #f5f6fa;
}

.sl-item.active {
  background: linear-gradient(135deg, #f0eeff 0%, #f7f6ff 100%);
  box-shadow: inset 3px 0 0 #6c63ff;
}

.sl-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #6c63ff, #818cf8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.sl-main {
  flex: 1;
  min-width: 0;
}

.sl-name {
  font-size: 13px;
  font-weight: 500;
  color: #1e1e2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sl-item.active .sl-name {
  color: #6c63ff;
  font-weight: 600;
}

.sl-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
}

.sl-round {
  font-size: 11px;
  color: #6c63ff;
  background: #f0eeff;
  padding: 0 6px;
  border-radius: 8px;
}

.sl-time {
  font-size: 11px;
  color: #9a9ab0;
}

.sl-more {
  opacity: 0;
  cursor: pointer;
  color: #9a9ab0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.15s ease, background 0.15s ease, color 0.15s ease;
  flex-shrink: 0;
}

.sl-item:hover .sl-more {
  opacity: 1;
}

.sl-more:hover {
  background: #e4e6f0;
  color: #1e1e2e;
}

.list-empty {
  text-align: center;
  padding: 40px 8px;
  color: #9a9ab0;
  font-size: 13px;
}

.list-empty p {
  margin-bottom: 12px;
}

.list-skeleton {
  padding: 8px 4px;
}

.list-skeleton .sk-row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 0;
}

.list-skeleton .sk-avatar {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: #f0f1f7;
}

.list-skeleton .sk-text {
  flex: 1;
}

.list-skeleton .sk-line {
  height: 10px;
  border-radius: 5px;
  background: linear-gradient(90deg, #f0f0f6, #fafafd, #f0f0f6);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
  margin: 4px 0;
}

/* ==================== 聊天区 ==================== */

.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #f8f9fc;
  position: relative;
}

.ca-header {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e5e7f0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  padding: 0 56px;
}

.panel-toggle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 40px;
  background: transparent;
  border: none;
  cursor: pointer;
  color: #9a9ab0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: all 0.2s ease;
  font-size: 13px;
}

.panel-toggle.left {
  left: 8px;
}

.panel-toggle.right {
  right: 8px;
}

.panel-toggle:hover {
  color: #6c63ff;
  background: #f0eeff;
}

.panel-toggle.hidden {
  color: #6c63ff;
}

.ca-header-center {
  text-align: center;
  min-width: 0;
}

.ca-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e1e2e;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 50vw;
}

.ca-disclaimer {
  font-size: 11px;
  color: #9a9ab0;
  margin-top: 2px;
  letter-spacing: 0.2px;
}

.ca-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  scroll-behavior: smooth;
}

.ca-body::-webkit-scrollbar {
  width: 5px;
}

.ca-body::-webkit-scrollbar-thumb {
  background: #d8dae6;
  border-radius: 5px;
}

.chat-greeting {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #9a9ab0;
}

.greeting-logo {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: linear-gradient(135deg, #6c63ff, #818cf8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  box-shadow: 0 8px 24px rgba(108, 99, 255, 0.3);
}

.greeting-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e1e2e;
  margin: 0 0 8px;
}

.greeting-text {
  font-size: 13px;
  line-height: 1.8;
  max-width: 360px;
  margin: 0 0 6px;
}

.greeting-tip {
  font-size: 12px;
  margin: 0 0 20px;
}

.chat-skeleton {
  padding-top: 8px;
}

.chat-skeleton .sk-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.chat-skeleton .sk-row.user {
  justify-content: flex-end;
}

.chat-skeleton .sk-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #818cf8, #6c63ff);
  flex-shrink: 0;
}

.chat-skeleton .sk-line {
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(90deg, #eef0f6, #fafbfe, #eef0f6);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* 消息气泡 */

.msg {
  display: flex;
  margin-bottom: 20px;
  animation: msgIn 0.3s ease;
}

@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.msg.user {
  justify-content: flex-end;
}

.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.msg-avatar.ai {
  background: linear-gradient(135deg, #818cf8, #6c63ff);
  color: #fff;
  margin-right: 10px;
  box-shadow: 0 2px 8px rgba(108, 99, 255, 0.25);
}

.msg-content {
  max-width: 68%;
  min-width: 0;
}

.msg-content.right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.msg-bubble {
  display: inline-block;
  padding: 12px 18px;
  font-size: 14px;
  line-height: 1.72;
  word-break: break-word;
  text-align: left;
  white-space: pre-wrap;
}

.msg-bubble.ai {
  background: #fff;
  border: 1px solid #e5e7f0;
  border-radius: 18px 18px 18px 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.06);
  color: #1e1e2e;
}

.msg-bubble.user {
  background: linear-gradient(135deg, #6c63ff, #4338ca);
  border-radius: 18px 18px 6px 18px;
  box-shadow: 0 4px 14px rgba(108, 99, 255, 0.22);
  color: #fff;
}

.msg-time {
  font-size: 11px;
  color: #9a9ab0;
  margin-top: 5px;
  padding: 0 6px;
}

.msg-content.right .msg-time {
  text-align: right;
}

/* 思考动画 */

.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border: 1px solid #e5e7f0;
  border-radius: 18px 18px 18px 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding: 12px 18px;
  animation: msgIn 0.3s ease;
}

.typing-text {
  font-size: 13px;
  color: #6c63ff;
  font-weight: 500;
}

.typing-dots {
  display: flex;
  gap: 4px;
}

.typing-dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6c63ff;
  animation: dotBounce 1.4s infinite ease-in-out both;
}

.typing-dots i:nth-child(2) {
  animation-delay: 0.16s;
}

.typing-dots i:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes dotBounce {
  0%,
  80%,
  100% {
    transform: scale(0.55);
    opacity: 0.35;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* 输入区 */

.ca-input-wrapper {
  padding: 0 28px 20px;
  flex-shrink: 0;
}

.ca-input {
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid #e5e7f0;
  overflow: hidden;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ca-input:focus-within {
  border-color: #6c63ff;
  box-shadow: 0 0 0 3px rgba(108, 99, 255, 0.1), 0 8px 32px rgba(0, 0, 0, 0.08);
}

.input-upper {
  padding: 12px 16px 6px;
}

.input-upper :deep(.el-textarea__inner) {
  border: none;
  background: transparent;
  box-shadow: none;
  font-size: 14px;
  line-height: 1.65;
  color: #1e1e2e;
  padding: 0;
  resize: none;
}

.input-upper :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.input-lower {
  display: flex;
  align-items: center;
  padding: 6px 14px 12px;
  gap: 8px;
}

.func-btn {
  cursor: pointer;
  height: 30px;
  padding: 0 12px;
  border: none;
  background: transparent;
  color: #9a9ab0;
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.2s ease;
  font-family: inherit;
}

.func-btn:hover {
  background: #f5f6fa;
  color: #6c63ff;
}

.spacer {
  flex: 1;
}

.send-btn {
  cursor: pointer;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #6c63ff, #4338ca);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(108, 99, 255, 0.3);
}

.send-btn:hover {
  transform: scale(1.06);
  box-shadow: 0 4px 16px rgba(108, 99, 255, 0.4);
}

.send-btn:active {
  transform: scale(0.94);
}

.send-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* ==================== 情绪分析面板 ==================== */

.emotion-panel {
  background: #fff;
  border-left: 1px solid #e5e7f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
  transition: width 0.25s ease, opacity 0.25s ease;
}

.ep-header {
  padding: 16px 16px 12px;
  border-bottom: 1px solid #f0f1f7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.ep-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e1e2e;
}

.ep-close {
  font-size: 15px;
  color: #9a9ab0;
  cursor: pointer;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.ep-close:hover {
  background: #f5f6fa;
  color: #1e1e2e;
}

.ep-body {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.ep-body::-webkit-scrollbar {
  width: 4px;
}

.ep-body::-webkit-scrollbar-thumb {
  background: #dddde8;
  border-radius: 4px;
}

.ep-section {
  margin-bottom: 16px;
}

.ep-section-title {
  font-size: 12px;
  font-weight: 700;
  color: #9a9ab0;
  letter-spacing: 0.3px;
  margin-bottom: 8px;
}

.emo-current-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #f8f9fc;
  border-radius: 12px;
  padding: 14px;
}

.emo-ring-box {
  position: relative;
  width: 92px;
  height: 92px;
  flex-shrink: 0;
}

.ring-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.ring-label {
  font-size: 10px;
  color: #9a9ab0;
}

.ring-value {
  font-size: 15px;
  font-weight: 700;
  color: #1e1e2e;
  margin-top: 2px;
}

.ring-sub {
  font-size: 10px;
  font-weight: 600;
  color: #6c63ff;
  margin-top: 2px;
}

.emo-legend {
  flex: 1;
  min-width: 0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #5a5a72;
  padding: 3px 0;
}

.legend-label {
  flex: 1;
}

.legend-pct {
  font-weight: 600;
  color: #1e1e2e;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.emo-tag-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.emotion-chip {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  border: 1px solid transparent;
}

.emo-sub-tag {
  font-size: 11px;
  color: #9a9ab0;
  background: #f5f6fa;
  padding: 2px 8px;
  border-radius: 10px;
}

.trend-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

.trend-chip.up {
  color: #dc2626;
  background: #fef2f2;
}

.trend-chip.down {
  color: #16a34a;
  background: #f0fdf4;
}

.trend-chip.stable {
  color: #9a9ab0;
  background: #f5f6fa;
}

.pad-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.pad-label {
  width: 62px;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  color: #5a5a72;
  text-align: right;
}

.pad-track {
  flex: 1;
  height: 6px;
  background: #e5e7f0;
  border-radius: 3px;
  position: relative;
  overflow: hidden;
}

.pad-zero {
  position: absolute;
  left: 50%;
  top: -2px;
  bottom: -2px;
  width: 1px;
  background: #c4c6d4;
  z-index: 1;
}

.pad-fill {
  position: absolute;
  top: 0;
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
  z-index: 2;
  min-width: 2px;
}

.pad-val {
  width: 42px;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  text-align: left;
}

.ratio-bar {
  display: flex;
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
  gap: 2px;
  margin-bottom: 4px;
}

.ratio-bar.small {
  height: 8px;
}

.ratio-seg {
  min-width: 3px;
  transition: flex 0.3s ease;
}

.ratio-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  font-weight: 500;
  margin-bottom: 2px;
}

.ratio-labels.small {
  margin-bottom: 10px;
}

.kw-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.kw-tag {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 999px;
}

.emo-analysis {
  font-size: 11px;
  line-height: 1.7;
  color: #5a5a72;
  background: #f8f7ff;
  border-left: 3px solid #6c63ff;
  border-radius: 0 8px 8px 0;
  padding: 8px 10px;
}

/* 历史情绪条目 */

.emo-item {
  margin-bottom: 8px;
  background: #f8f9fc;
  border-radius: 10px;
  border: 1px solid transparent;
  transition: all 0.2s ease;
  cursor: pointer;
}

.emo-item:hover {
  border-color: #d8daf0;
  background: #fff;
}

.emo-item.expanded {
  background: #fff;
  border-color: #6c63ff;
  box-shadow: 0 4px 16px rgba(108, 99, 255, 0.12);
}

.ei-header {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  user-select: none;
}

.ei-round {
  font-size: 11px;
  font-weight: 600;
  color: #1e1e2e;
}

.ei-sub {
  font-size: 10px;
  color: #9a9ab0;
  background: #f5f6fa;
  padding: 1px 6px;
  border-radius: 8px;
  flex-shrink: 0;
}

.ei-time {
  flex: 1;
  color: #9a9ab0;
  font-size: 10px;
  text-align: right;
  white-space: nowrap;
}

.ei-arrow {
  font-size: 9px;
  color: #9a9ab0;
  transition: transform 0.2s ease;
}

.emo-item.expanded .ei-arrow {
  transform: rotate(180deg);
}

.ei-body {
  padding: 10px 12px 12px;
  border-top: 1px solid #f0f1f7;
}

.emo-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #5a5a72;
  margin: 10px 0;
  flex-wrap: wrap;
}

.emo-meta span {
  display: flex;
  align-items: center;
  gap: 3px;
}

.emo-meta em {
  font-style: normal;
  color: #9a9ab0;
}

.emo-meta .up {
  color: #dc2626;
}

.emo-meta .down {
  color: #16a34a;
}

.emo-meta .stable {
  color: #9a9ab0;
}
</style>
