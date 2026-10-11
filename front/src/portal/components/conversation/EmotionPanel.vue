<template>
  <aside class="emotion-panel" :style="emotionPanelStyle">
    <div class="ep-header">
      <span class="ep-title">情绪分析</span>
      <span class="ep-close" title="收起面板" @click="emit('togglePanel')">
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
        <section class="ep-section" v-if="(props.keywords ?? []).length">
          <div class="ep-section-title">关键词</div>
          <div class="kw-tags">
            <span v-for="(kw, i) in props.keywords ?? []" :key="kw" class="kw-tag" :style="kwTagStyle(i)">
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
          <div class="emo-item" v-for="(item, index) in emotionList" :key="item.id" :class="{ expanded: expandedAnalysisId === item.id }" @click="emit('toggleAnalysis', item.id)">
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
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Close } from "@element-plus/icons-vue";
import { EMOTION_PANEL_WIDTH } from "@/shared/api/config";
import dayjs from "@/shared/utils/dayjs";
import {
  zhOf,
  toPct,
  chipStyle,
  kwTagStyle,
  trendMeta,
  ratioSegsOf,
  buildPadBars,
  padBarStyle,
  buildRingSegments,
} from "@/portal/composables/useEmotionAnalysis";
import type { EmotionAnalysisItem, RatioSeg, RingSegment } from "@/portal/composables/useEmotionAnalysis";

const props = defineProps<{
  currentEmotion: EmotionAnalysisItem | null;
  emotionList: EmotionAnalysisItem[];
  expandedAnalysisId: string | null;
  showPanel: boolean;
  /** 当前情绪分析关键词（父组件 useEmotionAnalysis().currentKeywords 传入） */
  keywords?: string[];
}>();

const emit = defineEmits<{
  (e: "togglePanel"): void;
  (e: "toggleAnalysis", id: string): void;
}>();

const emotionPanelStyle = computed(() => ({
  width: props.showPanel ? `${EMOTION_PANEL_WIDTH}px` : "0px",
  opacity: props.showPanel ? 1 : 0,
}));

const ringSegments = computed<RingSegment[]>(() => buildRingSegments(props.currentEmotion));

const ratioSegments = computed<RatioSeg[]>(() => ratioSegsOf(props.currentEmotion));

function msgTime(v?: string): string {
  if (!v) return "";
  const d = dayjs(v);
  if (!d.isValid()) return String(v);
  const now = dayjs();
  if (d.isSame(now, "day")) return d.format("HH:mm");
  if (d.isSame(now.subtract(1, "day"), "day")) return `昨天 ${d.format("HH:mm")}`;
  return d.format("M月D日 HH:mm");
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

.ep-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
}

.ep-body::-webkit-scrollbar {
  width: 4px;
}

.ep-body::-webkit-scrollbar-thumb {
  background: #dddde8;
  border-radius: 4px;
}

.ep-section {
  margin-bottom: 20px;
}

.ep-section-title {
  font-size: 12px;
  font-weight: 600;
  color: #9a9ab0;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin-bottom: 10px;
}

.emo-current-card {
  background: linear-gradient(135deg, #fafafe, #f4f2ff);
  border: 1px solid #ede9ff;
  border-radius: 14px;
  padding: 16px 14px;
}

.emo-ring-box {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  margin-bottom: 12px;
}

.ring-svg {
  width: 110px;
  height: 110px;
  transform: rotate(-90deg);
}

.ring-center {
  position: absolute;
  text-align: center;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.ring-label {
  font-size: 10px;
  color: #9a9ab0;
}

.ring-value {
  display: block;
  font-size: 16px;
  color: #1e1e2e;
  line-height: 1.4;
}

.ring-sub {
  font-size: 10px;
  color: #6c63ff;
}

.emo-legend {
  display: flex;
  justify-content: center;
  gap: 14px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-label {
  color: #666;
}

.legend-pct {
  color: #999;
}

.emo-tag-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.emotion-chip {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 12px;
  border: 1px solid;
}

.emo-sub-tag {
  font-size: 11px;
  color: #888;
  padding: 3px 8px;
  background: #f5f6fa;
  border-radius: 10px;
}

.trend-chip {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.trend-chip.up {
  color: #ff4d4f;
  background: #fff1f0;
}

.trend-chip.down {
  color: #52c41a;
  background: #f6ffed;
}

.trend-chip.stable {
  color: #6c63ff;
  background: #f0eeff;
}

.pad-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.pad-label {
  width: 58px;
  font-size: 11px;
  color: #666;
  flex-shrink: 0;
}

.pad-track {
  flex: 1;
  height: 6px;
  background: #f0f0f6;
  border-radius: 40px;
  position: relative;
}

.pad-zero {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 2px;
  height: 12px;
  background: #ccc;
  border-radius: 1px;
}

.pad-fill {
  position: absolute;
  top: 0;
  height: 100%;
  border-radius: 40px;
}

.pad-val {
  width: 48px;
  font-size: 11px;
  text-align: right;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.ratio-bar {
  display: flex;
  height: 8px;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 6px;
}

.ratio-bar.small {
  height: 5px;
}

.ratio-seg {
  min-width: 2px;
  transition: flex 0.3s ease;
}

.ratio-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  gap: 6px;
}

.ratio-labels.small {
  font-size: 9px;
  margin-bottom: 8px;
}

.kw-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.kw-tag {
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 10px;
  font-weight: 500;
}

.emo-analysis {
  font-size: 12px;
  color: #666;
  line-height: 1.7;
}

.emo-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f1f7;
  cursor: pointer;
  transition: background 0.15s;
}

.emo-item:hover {
  background: #fafafe;
}

.ei-header {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.ei-round {
  font-size: 10px;
  color: #9a9ab0;
  background: #f0f1f7;
  padding: 1px 6px;
  border-radius: 8px;
}

.ei-sub {
  font-size: 10px;
  color: #999;
}

.ei-time {
  font-size: 10px;
  color: #bbb;
  margin-left: auto;
}

.ei-arrow {
  font-size: 10px;
  color: #ccc;
  transition: transform 0.25s;
}

.emo-item.expanded .ei-arrow {
  transform: rotate(180deg);
}

.ei-body {
  padding: 10px 4px 6px;
}

.emo-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #888;
  margin-bottom: 6px;
}

.emo-meta em {
  font-style: normal;
  color: #bbb;
}
</style>