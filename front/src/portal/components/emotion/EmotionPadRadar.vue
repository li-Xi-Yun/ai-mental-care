<template>
  <section class="es-data-panel">
    <h4><span class="es-icon">🎯</span>PAD 情绪维度分析</h4>

    <div v-if="!complete" class="pad-placeholder">PAD 数据不完整</div>

    <div v-else class="pad-wrap">
      <svg viewBox="0 0 220 220" class="pad-svg">
        <!-- 网格（4 圈多边形） -->
        <polygon
          v-for="g in gridRings"
          :key="g.perc"
          :points="g.points"
          fill="none"
          stroke="#eee"
          stroke-width="0.8"
        />

        <!-- 三条轴线 -->
        <line
          v-for="ax in axesData"
          :key="`axis-${ax.name}`"
          :x1="CENTER.x"
          :y1="CENTER.y"
          :x2="pt(1, ax.angle).x"
          :y2="pt(1, ax.angle).y"
          stroke="#ddd"
          stroke-width="1"
        />

        <!-- 标准差虚线带 -->
        <line
          v-for="sb in stdBands"
          :key="`band-${sb.name}`"
          :x1="sb.band.x1"
          :y1="sb.band.y1"
          :x2="sb.band.x2"
          :y2="sb.band.y2"
          stroke="rgba(108,99,255,.3)"
          stroke-width="6"
          stroke-linecap="round"
          stroke-dasharray="4,4"
        />

        <!-- 均值三角 + 顶点 -->
        <template v-if="hasAnyMean">
          <polygon
            :points="meanPolygonPoints"
            fill="rgba(108,99,255,.12)"
            stroke="#6c63ff"
            stroke-width="2.5"
          />
          <g v-for="v in meanVertexes" :key="`vx-${v.name}`">
            <circle :cx="v.meanPt.x" :cy="v.meanPt.y" r="5" fill="#fff" stroke="#6c63ff" stroke-width="2.5" />
            <text
              :x="v.meanTextPos.x"
              :y="v.meanTextPos.y"
              text-anchor="middle"
              font-size="9"
              font-weight="600"
              fill="#6c63ff"
            >{{ v.meanText }}</text>
          </g>
        </template>
        <text v-if="!hasAnyMean" x="110" y="118" text-anchor="middle" font-size="12" fill="#999">
          无 PAD 均值数据
        </text>

        <!-- 轴端标签 -->
        <g v-for="ax in axesData" :key="`lab-${ax.name}`">
          <text
            :x="ax.labelPos.x"
            :y="ax.labelPos.y"
            text-anchor="middle"
            font-size="11"
            font-weight="600"
            :fill="axisColorOf(ax.name)"
          >{{ ax.name }}</text>
          <text
            :x="ax.labelPos.x"
            :y="ax.labelPos.y + 13"
            text-anchor="middle"
            font-size="9"
            fill="#666"
          >{{ ax.item?.label }}</text>
        </g>
      </svg>

      <div class="pad-right">
        <div class="pad-group">
          <div class="pad-group-title">📐 均值指标</div>
          <div class="pad-cards">
            <div
              class="pad-card"
              v-for="ax in axesData"
              :key="`m-${ax.name}`"
              :style="{ backgroundColor: cardBgOf(ax.name) }"
            >
              <div class="pad-label">{{ ax.item?.label }}</div>
              <div class="pad-value" :style="{ color: meanColorOf(ax.item?.mean ?? null) }">
                {{ ax.item?.meanText }}
              </div>
              <div class="pad-sub">值域 [-1, 1]</div>
            </div>
          </div>
        </div>

        <div class="pad-group">
          <div class="pad-group-title">📏 波动指标（标准差）</div>
          <div class="pad-cards">
            <div
              class="pad-card"
              v-for="ax in axesData"
              :key="`s-${ax.name}`"
              :style="{ backgroundColor: cardBgOf(ax.name) }"
            >
              <div class="pad-label">{{ ax.name }}维度波动</div>
              <div class="pad-value" style="color: #333;">{{ ax.item?.stdText }}</div>
              <div class="pad-sub">{{ ax.item?.stdSub }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";

/** PAD 三角雷达数据项（由详情页格式化后传入） */
export interface PadRadarItem {
  name: string; // "P" | "A" | "D"
  label: string; // "愉悦度 Pleasure" 等
  mean: number | null; // -1..1 或 null
  std: number | null; // 标准差或 null
  meanText: string; // 页面格式化好（如 "+0.65"）
  stdText: string; // 页面格式化好（如 "0.25"）
  stdSub: string; // 波动文案（如 "波动中等，情绪较为稳定"）
}

const props = defineProps<{ items: PadRadarItem[] }>();

/* ==================== 几何常量 ==================== */

const CENTER = { x: 110, y: 115 };
const RADIUS = 85;
const AXIS_ANGLES: Record<string, number> = { P: -90, A: 30, D: 150 };
const AXIS_ORDER = ["P", "A", "D"] as const;
const GRID_PERCENTS = [0.25, 0.5, 0.75, 1];

type Point = { x: number; y: number };

/** 轴上均值 v（-1..1）对应的坐标点 */
function pt(v: number, angleDeg: number): Point {
  const rad = (angleDeg * Math.PI) / 180;
  const r = RADIUS * ((v + 1) / 2);
  return {
    x: CENTER.x + r * Math.cos(rad),
    y: CENTER.y + r * Math.sin(rad),
  };
}

function clamp(v: number): number {
  return Math.max(-1, Math.min(1, v));
}

function axisLabelPos(angleDeg: number): Point {
  const rad = (angleDeg * Math.PI) / 180;
  return {
    x: CENTER.x + (RADIUS + 18) * Math.cos(rad),
    y: CENTER.y + (RADIUS + 18) * Math.sin(rad),
  };
}

/** 顶点数值标注位置（沿远离中心方向偏移 12px） */
function annotatePos(meanPt: Point): Point {
  return {
    x: meanPt.x + (meanPt.x > CENTER.x ? 12 : meanPt.x < CENTER.x ? -12 : 0),
    y: meanPt.y + (meanPt.y > CENTER.y ? 12 : meanPt.y < CENTER.y ? -12 : 0),
  };
}

/* ==================== 颜色 ==================== */

function axisColorOf(name: string): string {
  if (name === "A") return "#fa8c16";
  if (name === "D") return "#4ecdc4";
  return "#6c63ff";
}

function cardBgOf(name: string): string {
  if (name === "P") return "#f5f5ff";
  if (name === "A") return "#fff8f0";
  if (name === "D") return "#f5fff5";
  return "#f9f9fb";
}

function meanColorOf(v: number | null): string {
  if (v == null) return "#999";
  return v > 0 ? "#4ecdc4" : "#ff6b6b";
}

/* ==================== 派生数据 ==================== */

const byName = computed<Record<string, PadRadarItem>>(() => {
  const map: Record<string, PadRadarItem> = {};
  for (const it of props.items) map[it.name] = it;
  return map;
});

const complete = computed(
  () => props.items.length === 3 && AXIS_ORDER.every((n) => byName.value[n] != null),
);

interface AxisRender {
  name: string;
  angle: number;
  item?: PadRadarItem;
  labelPos: Point;
}

const axesData = computed<AxisRender[]>(() =>
  AXIS_ORDER.map((name) => ({
    name,
    angle: AXIS_ANGLES[name],
    item: byName.value[name],
    labelPos: axisLabelPos(AXIS_ANGLES[name]),
  })),
);

const gridRings = computed(() =>
  GRID_PERCENTS.map((k) => ({
    perc: k,
    points: AXIS_ORDER.map((n) => {
      const p = pt(2 * k - 1, AXIS_ANGLES[n]);
      return `${p.x},${p.y}`;
    }).join(" "),
  })),
);

const meanVertexes = computed(() =>
  axesData.value
    .filter((a) => a.item?.mean != null)
    .map((a) => {
      const meanPt = pt(a.item!.mean as number, a.angle);
      return {
        name: a.name,
        meanPt,
        meanText: a.item?.meanText ?? "--",
        meanTextPos: annotatePos(meanPt),
      };
    }),
);

const hasAnyMean = computed(() => meanVertexes.value.length > 0);

const meanPolygonPoints = computed(() =>
  meanVertexes.value.map((v) => `${v.meanPt.x},${v.meanPt.y}`).join(" "),
);

const stdBands = computed(() =>
  axesData.value
    .map((a) => ({ a, mean: a.item?.mean, std: a.item?.std }))
    .filter((x): x is { a: AxisRender; mean: number; std: number } => x.mean != null && x.std != null)
    .map((x) => {
      const lo = pt(clamp(x.mean - x.std), x.a.angle);
      const hi = pt(clamp(x.mean + x.std), x.a.angle);
      return {
        name: x.a.name,
        band: { x1: lo.x, y1: lo.y, x2: hi.x, y2: hi.y },
      };
    }),
);
</script>

<style scoped>
.pad-wrap {
  display: flex;
  gap: 12px;
  align-items: center;
}

.pad-svg {
  width: 200px;
  height: 200px;
  flex-shrink: 0;
}

.pad-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pad-group-title {
  font-size: 11px;
  font-weight: 600;
  color: #333;
}

.pad-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 4px;
}

.pad-card {
  padding: 8px;
  border-radius: 6px;
  text-align: center;
  min-width: 0;
}

.pad-label {
  font-size: 9px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pad-value {
  font-size: 16px;
  font-weight: 700;
  margin: 2px 0;
}

.pad-sub {
  font-size: 9px;
  color: #999;
}

.pad-placeholder {
  font-size: 12px;
  color: #999;
  text-align: center;
  padding: 30px 0;
}
</style>