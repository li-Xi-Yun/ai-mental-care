<template>
  <div ref="hostRef" class="live2d-avatar"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
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
 * - idle 动作循环 + 说话口型（ParamMouthOpenY 随 volume 驱动）
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
    /** 当前音频音量 0-100（由 AI TTS 播放状态驱动口型） */
    volume?: number;
    /** AI TTS 播放中（驱动口型开合） */
    speaking?: boolean;
  }>(),
  {
    expressionList: () => [],
    motionList: () => [],
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
    const loaded = await Live2DModelClass.from(url, { autoInteract: false });
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

/* ==================== idle 动作循环 ==================== */

/** 选择模型自身 Idle 分组的第一个待机动作；兼容模型分组大小写差异 */
function findIdleTarget(): MotionTarget | null {
  if (!model) return null;
  const settings = model.internalModel.settings as unknown as {
    motions?: Record<string, Array<{ File?: string }>>;
  };
  const motions = settings.motions ?? {};
  const idleGroup = Object.keys(motions).find((group) => group.toLowerCase() === "idle");
  const idleMotions = idleGroup ? motions[idleGroup] : undefined;
  if (idleGroup && idleMotions?.length) {
    return { group: idleGroup, index: 0 };
  }
  return null;
}

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

async function startIdleMotion(generation: number) {
  if (!model || disposed || generation !== loadGeneration) return;
  const token = idleToken;
  const target = findIdleTarget();
  while (token === idleToken && generation === loadGeneration && model && !disposed) {
    if (!target) break;
    const completed = await playMotionAndWait(target);
    if (!completed) break;
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

function applyExpression(assetId: string) {
  // 白名单校验：未收录的资产生id直接忽略（防越权渲染模型内未公开资产）
  if (!props.expressionList.includes(assetId)) {
    console.warn("[Live2D] 表情资产不在白名单，已忽略", assetId);
    return;
  }
  if (!model) {
    pendingExpressions.push(assetId);
    return;
  }
  const currentModel = model;
  const name = expressionNameFromModel(assetId);
  if (!name) {
    console.warn("[Live2D] 表情资产在模型内未找到", assetId);
    return;
  }
  void currentModel.expression(name).catch((e) => {
    console.warn("[Live2D] 表情播放失败", e);
    requestReload();
  });
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

defineExpose({ applyExpression, applyMotion });
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