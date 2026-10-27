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

/* ==================== 资产查找（白名单是文件名不带后缀） ==================== */

function assetBaseName(path: string, suffix: RegExp): string | undefined {
  const cleanPath = path.split(/[?#]/, 1)[0];
  return cleanPath.split("/").pop()?.replace(suffix, "");
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
}

function setTimeoutAsync(ms: number): Promise<void> {
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

let idleCycleActive = false;
async function startIdleMotion(generation: number) {
  if (!model || disposed || generation !== loadGeneration) return;

  // 未配置待机 → 保持静止（不播任何默认待机动作/表情）；
  // 启用库自动待机（idleAutoPlay）时，待机交给库的 Idle 机制，这里不叠加
  if (!idleConfigured.value || props.idleAutoPlay) return;
  idleCycleActive = true;

  // 可配置待机：每 interval 秒随机挑一个已勾选表情/动作播放；
  // 动作 fire-and-forget（不等待 Loop 动作结束），表情即时生效；说话中跳过本轮（不覆盖口型）
  const token = idleToken;
  while (token === idleToken && generation === loadGeneration && model && !disposed) {
    await setTimeoutAsync(props.idleIntervalSeconds * 1000);
    if (token !== idleToken || generation !== loadGeneration || !model || disposed) break;
    if (props.speaking) continue; // 不打断口型
    const picked = pickRandomIdle();
    if (!picked) break;
    if (picked.kind === "motion") {
      const target = motionIndexFromModel(picked.id);
      if (target) {
        const started = await playMotionFireAndForget(target);
        if (!started) break;
      }
    } else {
      playExpressionFromIdle(picked.id);
    }
  }
  idleCycleActive = false;
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
  _dbgMotionState: () => {
    const st = model?.internalModel?.motionManager?.state as { currentGroup?: unknown; currentIndex?: unknown; currentPriority?: unknown } | null;
    const mgr = model?.internalModel?.motionManager as { definitions?: Record<string, unknown[]>; groups?: { idle?: unknown } } | null;
    const defs = mgr?.definitions ?? {};
    return {
      ...(st ? { cg: st.currentGroup, ci: st.currentIndex, cp: st.currentPriority } : {}),
      defKeys: Object.keys(defs),
      idleDefs: (defs["Idle"] ?? []).length,
      idleGroup: mgr?.groups?.idle,
    };
  },
  _dbgPlay: (id: string): Promise<unknown> => {
    if (!model) return Promise.resolve("no-model");
    const target = motionIndexFromModel(id);
    return model.motion(target?.group ?? "", target?.index ?? 0).then(
      (r) => ({ ok: r, t: target }),
      (e) => ({ err: String(e) }),
    );
  },
  _dbgIdleEntered: () => idleCycleActive,
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