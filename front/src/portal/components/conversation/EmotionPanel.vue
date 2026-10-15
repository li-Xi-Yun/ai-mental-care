<template>
  <aside class="emotion-panel" :style="emotionPanelStyle">
    <div class="ep-header">
      <span class="ep-title">情绪分析</span>
      <span class="ep-close" title="收起面板" @click="emit('togglePanel')">
        <el-icon><Close /></el-icon>
      </span>
    </div>

    <!-- 该会话已有心理评估：直达最新一条评估详情 -->
    <div
      v-if="latestDiagnosisId"
      class="ep-jump"
      title="查看该会话最新的心理评估"
      @click="emit('jumpDiagnosis', latestDiagnosisId)"
    >
      <span>查看最新评估</span>
      <span class="ep-jump-arrow">→</span>
    </div>

    <div class="ep-body" ref="bodyRef" @scroll.passive="onScroll">
      <el-empty
        v-if="!items.length && !pagination.loading"
        description="暂无情绪分析，多聊几句试试"
        :image-size="72"
      />

      <!-- 设计稿 3-conversation.html：每轮一条的可展开列表 -->
      <div
        v-for="(item, index) in items"
        :key="item.id"
        class="emo-item"
        :class="{ expanded: expandedAnalysisId === item.id }"
        @click="emit('toggleAnalysis', item.id)"
      >
        <div class="ei-header">
          <span class="ei-round">第{{ item.roundNum }}轮</span>
          <span class="emotion-tag" :style="tagStyle(item, index)">{{ labelText(item) }}</span>
          <span v-if="item.emotionSubLabel" class="emo-sub-tag">{{ item.emotionSubLabel }}</span>
          <span class="ei-time">{{ msgTime(item.createdTime) }}</span>
          <span class="ei-arrow">▼</span>
        </div>

        <div class="ei-body" v-show="expandedAnalysisId === item.id">
          <!-- 情绪倾向分布 -->
          <div class="emo-section-title">情绪倾向分布</div>
          <div class="emo-ratio-wrap">
            <div class="emo-ratio-bar">
              <span
                v-for="seg in ratioSegsOf(item)"
                :key="seg.label"
                class="emo-ratio-seg"
                :style="{ flex: seg.flex, background: seg.color }"
                :title="`${seg.label} ${seg.pct}%`"
              ></span>
            </div>
            <div class="emo-ratio-labels">
              <span v-for="seg in ratioSegsOf(item)" :key="seg.label" :style="{ color: seg.color }">
                {{ seg.label }} {{ seg.pct }}%
              </span>
            </div>
          </div>

          <!-- PAD 三维情绪模型 -->
          <div class="emo-section-title">PAD 三维情绪模型</div>
          <div v-for="pad in buildPadBars(item)" :key="pad.label" class="emo-pad-row">
            <span class="pad-label">{{ pad.label }}</span>
            <div class="pad-track">
              <span class="pad-center"></span>
              <span class="pad-fill" :style="padBarStyle(pad)"></span>
            </div>
            <span class="pad-val" :style="{ color: pad.color }">{{ pad.text }}</span>
          </div>

          <!-- 核心指标 -->
          <div class="emo-section-title">核心指标</div>
          <div class="emo-meta">
            <span><span class="label">置信度</span> {{ toPct(item.emotionConfidence, 0) }}</span>
            <span><span class="label">强度</span> {{ toPct(item.emotionIntensity, 0) }}</span>
            <span :class="trendMeta(item.emotionTrend).type">
              {{ trendMeta(item.emotionTrend).icon }} {{ trendMeta(item.emotionTrend).text }}
            </span>
          </div>

          <!-- 分析详情 -->
          <template v-if="item.analysisContent">
            <div class="emo-section-title">分析详情</div>
            <div class="emo-analysis">{{ item.analysisContent }}</div>
          </template>

          <div class="emo-detail-link" @click.stop="emit('viewDetail', item.id)">查看对话详情 →</div>
        </div>
      </div>

      <!-- 分页状态 -->
      <div v-if="pagination.loading" class="ep-loading">加载中…</div>
      <div v-else-if="pagination.loadingMore" class="ep-loading">加载更多…</div>
      <div v-else-if="items.length && !pagination.hasMore" class="ep-end">已加载全部</div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { Close } from "@element-plus/icons-vue";
import type { EmotionAnalysisItem, EmotionPagination } from "@/portal/composables/useEmotionList";
import {
  toEmotionKey,
  emotionKeyColor,
  displayLabel,
  trendMeta,
  toPct,
  ratioSegsOf,
  buildPadBars,
  padBarStyle,
  msgTime,
} from "@/portal/composables/useEmotionDisplay";

const props = defineProps<{
  items: EmotionAnalysisItem[];
  pagination: EmotionPagination;
  expandedAnalysisId: string | null;
  showPanel: boolean;
  /** 当前会话最新一条心理评估 id；无评估为 null（顶部跳转按钮显隐） */
  latestDiagnosisId: string | null;
  /** 面板宽度（px，可拖拽调整） */
  width: number;
}>();

const emit = defineEmits<{
  (e: "togglePanel"): void;
  (e: "toggleAnalysis", id: string): void;
  (e: "loadMore"): void;
  (e: "viewDetail", id: string): void;
  (e: "jumpDiagnosis", id: string): void;
}>();

/* ==================== 面板宽度（沿用设计稿 340px，收缩为 0） ==================== */

const emotionPanelStyle = computed(() => ({
  width: props.showPanel ? `${props.width}px` : "0px",
  opacity: props.showPanel ? 1 : 0,
}));

/* ==================== 单轮条目展示 ==================== */

/** 主标签文案：优先细分标签，回退到主情绪中文名（设计稿 label/sub_label 语义） */
function labelText(item: EmotionAnalysisItem): string {
  return displayLabel(item.emotionLabel, item.emotionSubLabel);
}

/** 标签配色：后端 emotionLabel → 设计稿视觉键 → 颜色 */
function tagStyle(item: EmotionAnalysisItem, index: number) {
  const key = toEmotionKey(item.emotionLabel);
  const color = emotionKeyColor(key, index);
  return { color, background: `${color}14`, borderColor: `${color}40` };
}

/* ==================== 滚动加载更多（分页） ==================== */

const bodyRef = ref<HTMLElement | null>(null);

function onScroll(e: Event) {
  const el = e.target as HTMLElement;
  if (!el) return;
  // 距底部 40px 内触发下一页加载
  if (el.scrollHeight - el.scrollTop - el.clientHeight < 40) {
    emit("loadMore");
  }
}
</script>

<style scoped>
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
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f1f7;
  flex-shrink: 0;
}

.ep-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e1e2e;
}

.ep-close {
  cursor: pointer;
  color: #9a9ab0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: all 0.2s;
}

.ep-close:hover {
  color: #6c63ff;
  background: #f0eeff;
}

/* 最新心理评估跳转（不随列表滚动） */
.ep-jump {
  margin: 10px 12px 0;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f0eeff;
  border: 1px solid #e0dcff;
  border-radius: 8px;
  color: #6c63ff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.ep-jump:hover {
  background: #e8e4ff;
  border-color: #d5cfff;
}

.ep-jump-arrow {
  font-weight: 700;
}

.ep-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
}

.ep-body::-webkit-scrollbar {
  width: 4px;
}

.ep-body::-webkit-scrollbar-thumb {
  background: #dddde8;
  border-radius: 4px;
}

.ep-loading,
.ep-end {
  padding: 10px 0;
  text-align: center;
  font-size: 11px;
  color: #9a9ab0;
}

/* ==================== 每轮条目（设计稿 .emo-item） ==================== */

.emo-item {
  margin-bottom: 10px;
  background: #f8f9fc;
  border-radius: 8px;
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
  border-color: #6c63ff66;
  box-shadow: 0 4px 16px rgba(79, 70, 229, 0.08);
}

.ei-header {
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  user-select: none;
  flex-wrap: wrap;
}

.ei-round {
  color: #1e1e2e;
  font-weight: 600;
  font-size: 12px;
  flex-shrink: 0;
}

.emotion-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
  border: 1px solid transparent;
  flex-shrink: 0;
}

.emo-sub-tag {
  font-size: 10px;
  color: #6b7280;
  background: #f0f1f7;
  padding: 2px 8px;
  border-radius: 10px;
  flex-shrink: 0;
}

.ei-time {
  flex: 1;
  color: #9ca3af;
  text-align: right;
  font-size: 11px;
}

.ei-arrow {
  font-size: 10px;
  color: #9ca3af;
  transition: transform 0.25s;
  flex-shrink: 0;
}

.emo-item.expanded .ei-arrow {
  transform: rotate(180deg);
}

.ei-body {
  padding: 4px 14px 14px;
  border-top: 1px solid #f0f1f7;
}

.emo-section-title {
  font-size: 10px;
  font-weight: 700;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 10px 0 6px;
}

/* 情绪倾向分布 */
.emo-ratio-bar {
  display: flex;
  height: 10px;
  border-radius: 5px;
  overflow: hidden;
  gap: 2px;
}

.emo-ratio-seg {
  min-width: 4px;
  transition: flex 0.3s ease;
}

.emo-ratio-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 10px;
  font-weight: 500;
}

/* PAD 三维情绪 */
.emo-pad-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}

.pad-label {
  width: 56px;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  color: #4b5563;
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

.pad-center {
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
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
  width: 48px;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  text-align: left;
  font-variant-numeric: tabular-nums;
}

/* 核心指标 */
.emo-meta {
  display: flex;
  gap: 16px;
  font-size: 11px;
  color: #4b5563;
  flex-wrap: wrap;
}

.emo-meta span {
  display: flex;
  align-items: center;
  gap: 3px;
}

.emo-meta .label {
  color: #9ca3af;
}

.emo-meta .up {
  color: #dc2626;
}

.emo-meta .down {
  color: #16a34a;
}

.emo-meta .stable {
  color: #9ca3af;
}

/* 分析详情 */
.emo-analysis {
  font-size: 11px;
  line-height: 1.7;
  color: #4b5563;
  background: #f8f9fc;
  border-radius: 6px;
  padding: 8px 10px;
  border-left: 3px solid #6366f1;
}

.emo-detail-link {
  display: inline-block;
  margin-top: 10px;
  font-size: 11px;
  font-weight: 600;
  color: #6c63ff;
  cursor: pointer;
  transition: opacity 0.2s;
}

.emo-detail-link:hover {
  opacity: 0.7;
}
</style>
