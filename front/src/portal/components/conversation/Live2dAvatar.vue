<template>
  <div ref="hostRef" class="live2d-avatar"></div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useElementSize } from "@vueuse/core";
import type { Application } from "pixi.js";
import type { Live2DModel } from "pixi-live2d-display/cubism4";

/**
 * Live2D 虚拟形象渲染组件（语音页中央语音球的替代物）
 *
 * 职责（纯渲染 + 指令消费，不做业务拉取）：
 * - 懒加载 Cubism Core（window.Live2DCubismCore 缺失时动态注入 %BASE_URL%live2d/live2dcubismcore.min.js）
 * - 初始化 PIXI Application（透明底、DPR 适配、随容器 ResizeObserver 缩放）
 * - 用签名 model3 URL 装配模型（model.anchor 底部居中适配）
 * - 消费 applyExpression / applyMotion（白名单校验 → 资产查找 → 驱动）；模型未就绪前的指令入队，加载后 flush
 * - 待机循环：仅当后台配置了待机表情/动作 + 间隔时启用（每 N 秒随机挑一个播放）；未配置则保持静止
 * - 待机开关 idle_auto_play=0：除禁用库自动 Idle 动作外，同时关闭呼吸/眨眼/物理/姿态等自然生理微动，实现完全静止
 * - 说话口型（ParamMouthOpenY 随 volume 驱动）
 *
 * props 由外层页面驱动：modelUrl（签名 URL，变化即重载）、expressionList/motionList（白名单）、
 * volume（0-100，AI TTS 播放音量）、speaking（AI TTS 播放中）。
 * 加载失败 / 资源过期统一 emit("load-error")，由页面决定续签重试或回退语音球。
 */
interface MotionTarget {
  group: string;
  index: number;
}

const props = withDefaults(
  defineProps<{
    /** Live2D 模型签名下载 URL（相对绝对路径，模型内部子资源已由后端改写为签名 URL） */
    modelUrl: string;
    /** 表情资产 id 白名单（文件名不带后缀） */
    expressionList?: string[];
    /** 动作资产 id 白名单（文件名不带后缀） */
    motionList?: string[];
    /** 待机表情 id 列表（白名单子集；空数组 = 不启用待机表情轮换） */
    idleExpressionList?: string[];
    /** 待机动作 id 列表（白名单子集；空数组 = 不启用待机动作轮换） */
    idleMotionList?: string[];
    /** 待机操作间隔（秒）；<=0 或两列表均为空 → 回退现有 Idle 组循环 */
    idleIntervalSeconds?: number;
    /** 是否启用 pixi-live2d-display 自带自动待机（模型 Idle 组）：true=库自动播，false=按后台待机设置/静止 */
    idleAutoPlay?: boolean;
    /** 当前音频音量 0-100（由 AI TTS 播放状态驱动口型） */
    volume?: number;
    /** AI TTS 播放中（驱动口型开合） */
    speaking?: boolean;
  }>(),
  {
    expressionList: () => [],
    motionList: () => [],
    idleExpressionList: () => [],
    idleMotionList: () => [],
    idleIntervalSeconds: 0,
    idleAutoPlay: true,
    volume: 0,
    speaking: false,
  },
);

const emit = defineEmits<{
  (e: "load-error"): void;
  (e: "ready"): void;
}>();

const hostRef = ref<HTMLDivElement | null>(null);

let app: Application | null = null;
let model: Live2DModel | null = null;
let disposed = false;
/** 模型加载代次：只允许最新一次异步加载结果挂载到 stage */
let loadGeneration = 0;
/** idle 动作循环令牌：每次打断（动作播放/卸载）自增，旧循环退出 */
let idleToken = 0;
/** 口型 ticker 令牌 */
let mouthTicker: (() => void) | null = null;
let runtimePromise: Promise<typeof import("pixi-live2d-display/cubism4")> | null = null;
/** 模型未就绪前暂存的待执行指令 */
const pendingExpressions: string[] = [];
const pendingMotions: string[] = [];
let reloadRequested = false;

/* ==================== 运行时 / 核心库懒加载 ==================== */

function ensureRuntime(): Promise<typeof import("pixi-live2d-display/cubism4")> {
  runtimePromise ??= (async () => {
    const w = window as unknown as { Live2DCubismCore?: unknown };
    if (!w.Live2DCubismCore) {
      await new Promise<void>((resolve, reject) => {
        const script = document.createElement("script");
        script.src = `${import.meta.env.BASE_URL}live2d/live2dcubismcore.min.js`;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error("Live2D Cubism 核心库加载失败"));
        document.head.appendChild(script);
      });
    }
    return import("pixi-live2d-display/cubism4");
  })();
  return runtimePromise;
}

/* ==================== PIXI 初始化 / 装配 ==================== */

onMounted(async () => {
  const { Application } = await import("pixi.js");
  if (disposed || !hostRef.value) return;
  const rect = hostRef.value.getBoundingClientRect();
  app = new Application({
    width: Math.max(rect.width || 240, 1),
    height: Math.max(rect.height || 280, 1),
    backgroundAlpha: 0,
    resolution: Math.min(window.devicePixelRatio || 1, 2),
    autoDensity: true,
    antialias: true,
  });
  hostRef.value.appendChild(app.view as HTMLCanvasElement);
  if (props.modelUrl) {
    loadGeneration += 1;
    void loadModel(props.modelUrl, loadGeneration);
  }
});

const { width, height } = useElementSize(hostRef);
watch([width, height], () => {
  if (!app) return;
  app.renderer.resize(Math.max(width.value, 1), Math.max(height.value, 1));
  fitModel();
});

/** 模型整体适配容器：底部居中，等比缩放留 4% 边距 */
function fitModel() {
  if (!model || !app) return;
  const cw = app.renderer.width / app.renderer.resolution;
  const ch = app.renderer.height / app.renderer.resolution;
  const bounds = model.getBounds();
  if (bounds.width > 0 && bounds.height > 0) {
    const scale = Math.min(cw / bounds.width, ch / bounds.height);
    model.scale.set(scale);
  }
  model.anchor.set(0.5, 1);
  model.position.set(cw / 2, ch - 2);
}

function isCurrentLoad(url: string, generation: number) {
  return !disposed && app != null && generation === loadGeneration && url === props.modelUrl;
}

function clearStageModel() {
  idleToken += 1;
  if (mouthTicker && app) {
    app.ticker.remove(mouthTicker);
    mouthTicker = null;
  }
  lastIdleId = null;
  const children = app?.stage.children.slice() ?? [];
  children.forEach((child) => {
    app?.stage.removeChild(child);
    child.destroy();
  });
  model = null;
}

async function loadModel(url: string, generation = loadGeneration) {
  if (!isCurrentLoad(url, generation) || !app) return;
  try {
    if (import.meta.env.DEV) {
      console.debug("[Live2D] 开始加载模型", { generation, canvasCount: hostRef.value?.querySelectorAll("canvas").length });
    }
    const { Live2DModel: Live2DModelClass } = await ensureRuntime();
    const { Ticker } = await import("pixi.js");
    Live2DModelClass.registerTicker(Ticker);
    if (!isCurrentLoad(url, generation)) return;
    const loaded = await Live2DModelClass.from(url, {
      autoInteract: false,
      // 库自动待机开关：idle_auto_play=1 → 指向模型自带 Idle 组（库空闲自动播）；
      // =0 → 指向不存在的组名，库不会自动播 Idle，待机交由 startIdleMotion（后台配置/静止）控制
      idleMotionGroup: props.idleAutoPlay ? "Idle" : "__no_auto_idle__",
    });
    if (!isCurrentLoad(url, generation)) {
      loaded.destroy();
      if (import.meta.env.DEV) console.debug("[Live2D] 丢弃过期模型加载结果", { generation });
      return;
    }
    clearStageModel();
    if (!isCurrentLoad(url, generation)) {
      loaded.destroy();
      return;
    }
    model = loaded;
    app.stage.addChild(loaded);
    fitModel();
    applyIdleGating();
    idleToken += 1;
    void startIdleMotion(generation);
    flushPending();
    setSpeakingState(!!props.speaking);
    emit("ready");
    if (import.meta.env.DEV) {
      console.debug("[Live2D] 模型挂载完成", {
        generation,
        stageChildren: app.stage.children.length,
        canvasCount: hostRef.value?.querySelectorAll("canvas").length,
      });
    }
  } catch (e) {
    if (!isCurrentLoad(url, generation)) return;
    console.warn("[Live2D] 模型加载失败", e);
    emit("load-error");
  }
}

function removeModel() {
  clearStageModel();
}

/** 待机开关门控：
 *  idle_auto_play=0 时需要「完全静止」——不仅禁用库自动 Idle 动作（靠 idleMotionGroup 指向空组），
 *  还需关闭库每帧无条件执行的自然生理微动（呼吸/眨眼/物理摆动），否则模型仍会轻微晃动/眨眼/呼吸。
 *  库 update() 对这些子系统均为 `=== null ? void 0 : ...` 安全访问，置空即可。
 *  注意：pose 不可置空——模型 pose3.json 的 Groups 负责手臂 A/B 遮挡层互斥显示
 *  （如 PartArmLA/PartArmLB），置空后两层同时渲染 → 出现「两只手」。
 */
function applyIdleGating() {
  const im = model?.internalModel as unknown as
    | {
        breath?: unknown;
        eyeBlink?: unknown;
        physics?: unknown;
        pose?: unknown;
      }
    | null;
  if (!im) return;
  if (props.idleAutoPlay) return; // 启用库自动待机 → 保留生理微动（维持现状）
  im.breath = null; // 关呼吸摇摆（头/身 0~15°、胸脯起伏）
  im.eyeBlink = null; // 关自动眨眼
  im.physics = null; // 关头发/衣摆物理摆动
  // 保留 pose（手臂遮挡层）
}

/* ==================== 待机回合柔和复位 ==================== */

/**
 * 柔和停止当前动作（并强制结束 Loop）：
 * 引擎对 Loop=true 动作永不 self-finish，且到了 Duration 会重置起点回卷再播——
 * 因此必须先 startFadeOut（让 fade 权重回落）再 stopAllMotions（硬清队列），
 * 否则停在 loop 转折点会「卡住」并立刻重播。
 */
function softStopCurrentMotion(fadeMs = 500) {
  const mm = model?.internalModel?.motionManager as unknown as {
    queueManager?: { _motions?: Array<{ _motion?: unknown; isFinished?: () => boolean; startFadeOut?: (s: number, t: number) => void }> };
    expressionManager?: { resetExpression?: () => void };
    stopAllMotions?: () => void;
  } | null;
  if (!mm) return;
  try {
    const now = performance.now() / 1000;
    const entries = mm.queueManager?._motions ?? [];
    for (const e of entries) {
      if (e?._motion && !(e.isFinished?.() ?? false)) {
        e.startFadeOut?.(fadeMs / 1000, now);
      }
    }
    mm.expressionManager?.resetExpression?.();
    // fade 触发后仍需硬清队列：否则 Loop 动作在 fadeOut 结束处仍会回卷再播，形成"卡住+重复"
    window.setTimeout(() => {
      if (disposed || !model) return;
      try { mm.stopAllMotions?.(); } catch { /* ignore */ }
    }, fadeMs + 80);
  } catch {
    /* 复位失败不阻塞 */
  }
}

/** 上一轮已播的资产 id（同 kind 排除，候选>1 时避免立刻复读） */
let lastIdleId: string | null = null;

/** 随机挑一项；候选>1 时在「并集层面」排除 excludeId（表情/动作互相可替换），避免立刻复读刚播过的 id */
function pickRandomIdleExcluding(excludeId: string | null): { kind: "expression" | "motion"; id: string } | null {
  const exprs = props.idleExpressionList.filter((id) => props.expressionList.includes(id));
  const motions = props.idleMotionList.filter((id) => props.motionList.includes(id));
  const total = exprs.length + motions.length;
  if (!total) return null;
  if (excludeId && total > 1) {
    // 并集排除：上一轮播过 id 的（无论表情还是动作）本轮不选，表情/动作互相可替换
    const items: { kind: "expression" | "motion"; id: string }[] = [];
    exprs.forEach((id) => items.push({ kind: "expression", id }));
    motions.forEach((id) => items.push({ kind: "motion", id }));
    const remaining = items.filter((it) => it.id !== excludeId);
    return remaining[Math.floor(Math.random() * remaining.length)];
  }
  const idx = Math.floor(Math.random() * total);
  return idx < exprs.length ? { kind: "expression", id: exprs[idx] } : { kind: "motion", id: motions[idx - exprs.length] };
}

/* ==================== 资产查找（白名单是文件名不带后缀） ==================== */

function assetBaseName(path: string, suffix: RegExp): string | undefined {
  // model3.json 内部子资源经后端 ModelFileSigner 改写为签名 URL：
  //   /user/live2d-model/file?path=live2d-models/Mao/Mao/motions/mtn_01.motion3.json&expires=...&sign=...
  // 直接从 URL 取 path 段，否则取到的是接口名 `file`，与白名单 id (mtn_01) 匹配不上 → 动作/表情找不到。
  let raw = path;
  if (path.startsWith("/user/live2d-model/file?")) {
    try {
      const q = path.slice(path.indexOf("?") + 1);
      raw = new URLSearchParams(q).get("path") ?? path.split(/[?#]/, 1)[0];
    } catch {
      raw = path.split(/[?#]/, 1)[0];
    }
  } else {
    raw = path.split(/[?#]/, 1)[0];
  }
  return raw.split("/").pop()?.replace(suffix, "");
}

function motionIndexFromModel(assetId: string): MotionTarget | null {
  if (!model) return null;
  const settings = model.internalModel.settings as unknown as {
    motions?: Record<string, Array<{ File?: string }>>;
  };
  const motions = settings.motions ?? {};
  for (const group of Object.keys(motions)) {
    const files = motions[group] ?? [];
    for (let i = 0; i < files.length; i++) {
      const base = assetBaseName(String(files[i]?.File ?? ""), /\.motion3\.json$/i);
      if (base === assetId) return { group, index: i };
    }
  }
  return null;
}

function expressionNameFromModel(assetId: string): string | null {
  if (!model) return null;
  const settings = model.internalModel.settings as unknown as {
    expressions?: Array<{ Name?: string; File?: string }>;
  };
  const expressions = settings.expressions ?? [];
  for (const expression of expressions) {
    if (expression.Name === assetId) return assetId;
    const base = assetBaseName(String(expression.File ?? ""), /\.exp3\.json$/i);
    if (base === assetId && expression.Name) return expression.Name;
  }
  return null;
}

/* ==================== idle 待机循环 ==================== */

/** 是否启用「可配置待机」：间隔>0 且至少勾选了表情或动作；否则保持静止（不播任何默认待机动作/表情） */
const idleConfigured = computed(
  () => props.idleIntervalSeconds > 0 && (props.idleExpressionList.length > 0 || props.idleMotionList.length > 0),
);

async function playMotionAndWait(target: MotionTarget): Promise<boolean> {
  if (!model) return false;
  const currentModel = model;
  const manager = currentModel.internalModel.motionManager as unknown as {
    once?: (event: string, listener: () => void) => void;
    off?: (event: string, listener: () => void) => void;
  };
  return new Promise((resolve) => {
    let settled = false;
    const timeout = window.setTimeout(() => finish(false), 30000);
    const finish = (completed: boolean) => {
      if (settled) return;
      settled = true;
      window.clearTimeout(timeout);
      manager.off?.("motionFinish", onFinish);
      resolve(completed);
    };
    const onFinish = () => finish(true);
    manager.once?.("motionFinish", onFinish);
    void currentModel
      .motion(target.group, target.index)
      .then((started) => {
        if (!started) finish(false);
      })
      .catch(() => finish(false));
  });
}

/** 从待机表情 ∪ 待机动作中随机挑一项（再次按白名单兜底过滤）；无可用项返回 null */
function pickRandomIdle(): { kind: "expression" | "motion"; id: string } | null {
  const exprs = props.idleExpressionList.filter((id) => props.expressionList.includes(id));
  const motions = props.idleMotionList.filter((id) => props.motionList.includes(id));
  const total = exprs.length + motions.length;
  if (!total) return null;
  const idx = Math.floor(Math.random() * total);
  if (idx < exprs.length) return { kind: "expression", id: exprs[idx] };
  return { kind: "motion", id: motions[idx - exprs.length] };
}function setTimeoutAsync(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

/** 播放一次性动作（不等待完成）：
 *  model.motion() 立即返回是否成功启动；Loop 动作不会触发 motionFinish，
 *  这里不阻塞等播完——由待机循环按 interval 定时切换下一个，保证每轮都有可见动作 */
async function playMotionFireAndForget(target: MotionTarget): Promise<boolean> {
  if (!model) return false;
  return model
    .motion(target.group, target.index)
    .then((started) => !!started)
    .catch(() => false);
}

/**
 * 估算已发起动作的播放时长（毫秒）。
 * Loop 动作引擎永不 self-finish、达到 Duration 会回卷重播；这里读当前队列 motion 对象的
 * `getLoopDuration?.()`（Loop 时返回 `_loopDurationSeconds`，非 Loop 返回 -1）。
 * 返回 null 表示无法获取，调用方用 interval 兜底。
 */
async function motionHoldDurationMs(): Promise<number | null> {
  const mm = model?.internalModel?.motionManager as unknown as {
    queueManager?: { _motions?: Array<{ _motion?: { getLoopDuration?: () => number; getDuration?: () => number } }> };
  } | null;
  const entry = mm?.queueManager?._motions?.find((e) => e?._motion != null);
  if (!entry?._motion) return Promise.resolve(null);
  let secs = entry._motion.getLoopDuration?.();
  if (!(secs != null && secs > 0)) secs = entry._motion.getDuration?.();
  // 兜底：签名 URL 路径中取文件名 → 精确文件名（motion3），但不做网络读取，仅当队首即目标时才可靠；
  // 这里对队首 motion 取时长即可（刚发起的就是队首）
  if (secs == null || secs <= 0 || Number.isNaN(secs)) return Promise.resolve(null);
  return Promise.resolve(Math.min(secs * 1000, 15000));
}

async function startIdleMotion(generation: number) {
  if (!model || disposed || generation !== loadGeneration) return;

  // 未配置待机 → 保持静止（不播任何默认待机动作/表情）；
  // 启用库自动待机（idleAutoPlay）时，待机交给库的 Idle 机制，这里不叠加
  if (!idleConfigured.value || props.idleAutoPlay) return;
  const token = idleToken;
  try {
    // 回合式待机：柔和停止上一轮 → 停留窗口 → 随机播一个（排除刚播过） → 播放其时长后柔和截断
    while (token === idleToken && generation === loadGeneration && model && !disposed) {
      // 说话中不播放也不复位（口型由外部 ticker 驱动）
      if (props.speaking) { await setTimeoutAsync(1000); continue; }
      softStopCurrentMotion(500); // 先柔和停止上一轮（fadeOut + 随后 stopAllMotions）
      await setTimeoutAsync(700); // 归位窗口（fade + 中立），避免刚停又播
      let picked = pickRandomIdleExcluding(lastIdleId); // 排除刚播过的 id（表情/动作互相替换）
      if (!picked) {
        lastIdleId = null;
        picked = pickRandomIdle();
        if (!picked) break;
      }
      lastIdleId = picked.id;
      if (picked.kind === "motion") {
        const target = motionIndexFromModel(picked.id);
        if (target) {
          const started = await playMotionFireAndForget(target);
          if (!started) break;
          // Loop 动作不会自然结束（到 Duration 回卷再播 → 卡住+重复）。
          // 播放本动作时长（读 motion 对象 Loop 时长；失败兜底 interval）后主动柔和截断。
          const holdMs = (await motionHoldDurationMs()) ?? props.idleIntervalSeconds * 1000;
          await setTimeoutAsync(holdMs);
          if (token !== idleToken || generation !== loadGeneration || !model || disposed) break;
          softStopCurrentMotion(500); // 定时截断，避免 loop 回卷
        }
      } else {
        playExpressionFromIdle(picked.id);
        await setTimeoutAsync(props.idleIntervalSeconds * 1000); // 表情保持一个间隔
      }
    }
  } finally {
    // 只有仍由本待机循环主导（token 未变、无 AI 抢占）时才柔和归位；
    // AI 指令会先 idleToken+=1 再播放，此处 token!==idleToken → 跳过，保留 AI 表现
    if (token === idleToken && model && !disposed) softStopCurrentMotion(500);
  }
}

/* ==================== 表情 / 动作指令（对外暴露） ==================== */

function requestReload() {
  if (reloadRequested || disposed || !props.modelUrl) return;
  reloadRequested = true;
  const generation = ++loadGeneration;
  removeModel();
  void loadModel(props.modelUrl, generation).finally(() => {
    reloadRequested = false;
  });
}

/** 直接驱动表情（不参与空闲开关）；模型未就绪返回 false（由调用方决定是否入队） */
function playExpressionNow(assetId: string): boolean {
  if (!model) return false;
  const currentModel = model;
  const name = expressionNameFromModel(assetId);
  if (!name) {
    console.warn("[Live2D] 表情资产在模型内未找到", assetId);
    return false;
  }
  void currentModel.expression(name).catch((e) => {
    console.warn("[Live2D] 表情播放失败", e);
    requestReload();
  });
  return true;
}

function applyExpression(assetId: string) {
  // 白名单校验：未收录的资产id直接忽略（防越权渲染模型内未公开资产）
  if (!props.expressionList.includes(assetId)) {
    console.warn("[Live2D] 表情资产不在白名单，已忽略", assetId);
    return;
  }
  if (!model) {
    pendingExpressions.push(assetId);
    return;
  }
  if (!playExpressionNow(assetId)) return;
  // 可配置待机模式下，AI 表情指令打断当前的待机倒计时并重启一轮：
  // 让 AI 表情至少完整展示一个间隔；非配置模式保持原样（原生 Idle 循环继续）
  if (idleConfigured.value) {
    idleToken += 1;
    void startIdleMotion(loadGeneration);
  }
}

/** 待机轮换播放表情（不做白名单二次校验——空闲候选已按白名单过滤） */
function playExpressionFromIdle(assetId: string) {
  if (!playExpressionNow(assetId)) {
    // 模型内未找到该表情（理论上已按白名单过滤）→ 直接退出本轮待机，避免空转
    idleToken += 1;
  }
}

function applyMotion(assetId: string) {
  if (!props.motionList.includes(assetId)) {
    console.warn("[Live2D] 动作资产不在白名单，已忽略", assetId);
    return;
  }
  if (!model) {
    pendingMotions.push(assetId);
    return;
  }
  const target = motionIndexFromModel(assetId);
  if (!target) {
    console.warn("[Live2D] 动作资产在模型内未找到", assetId);
    return;
  }
  const generation = loadGeneration;
  // 打断 idle 循环，播放一次性动作；结束后回归 idle
  idleToken += 1;
  void playMotionAndWait({ group: target.group, index: target.index }).then((completed) => {
    if (!completed) {
      requestReload();
      return;
    }
    if (!disposed && generation === loadGeneration) {
      idleToken += 1;
      void startIdleMotion(generation);
    }
  });
}

function flushPending() {
  const expressions = pendingExpressions.splice(0);
  const motions = pendingMotions.splice(0);
  expressions.forEach(applyExpression);
  motions.forEach(applyMotion);
}

/** 通过 Cubism4 coreModel 设置口型参数（类型宽松：coreModel 基类型为 object，运行时方法存在） */
function setMouthOpen(value: number) {
  if (!model) return;
  const core = model.internalModel.coreModel as unknown as {
    setParameterValueById?: (id: string, v: number) => void;
  };
  try {
    core.setParameterValueById?.("ParamMouthOpenY", value);
  } catch {
    /* 模型无口型参数，忽略 */
  }
}

function setSpeakingState(speaking: boolean) {
  if (!model || !app) return;
  if (mouthTicker) {
    app.ticker.remove(mouthTicker);
    mouthTicker = null;
  }
  if (speaking) {
    mouthTicker = () => {
      if (disposed || !model) return;
      setMouthOpen(Math.min(Math.max(props.volume / 55, 0), 1));
    };
    app.ticker.add(mouthTicker);
  } else {
    setMouthOpen(0);
  }
}

/* ==================== 响应式驱动 ==================== */

// 签名 URL 变化（页面重新签发后）→ 重建模型
watch(
  () => props.modelUrl,
  (url) => {
    if (!url || disposed) return;
    loadGeneration += 1;
    removeModel();
    void loadModel(url, loadGeneration);
  },
);

watch(
  () => props.speaking,
  (speaking) => setSpeakingState(!!speaking),
);

// 待机开关变化 → 重载模型让新开关生效（idleMotionGroup + 生理微动门控随实例重建；已在语音页时无需刷新页面）
watch(
  () => props.idleAutoPlay,
  () => {
    if (model && !disposed) requestReload();
  },
);

/* ==================== 清理 ==================== */

onBeforeUnmount(() => {
  disposed = true;
  loadGeneration += 1;
  idleToken += 1;
  lastIdleId = null;
  if (mouthTicker && app) {
    app.ticker.remove(mouthTicker);
    mouthTicker = null;
  }
  clearStageModel();
  if (app) {
    app.destroy(true, { children: true, texture: true });
    app = null;
  }
  model = null;
});

defineExpose({
  applyExpression,
  applyMotion,
});
</script>

<style scoped>
.live2d-avatar {
  width: min(94vw, 400px);
  height: clamp(400px, 56vh, 480px);
  position: relative;
  pointer-events: none;
  user-select: none;
}

.live2d-avatar :deep(canvas) {
  display: block;
}
</style>