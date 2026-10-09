import { getToken } from "@shared/api/auth";
import { API_BASE_URL } from "@shared/api/config";

/**
 * 前端日志上报基础设施（独立模块，不依赖 axios 拦截器 / 路由 / nprogress）。
 *
 * 对接后端：POST {API_BASE_URL}/common/logs/frontend/upload
 *  - 接口属于 /common/**（permit-all，无需 token），由 Vite 代理 /api 转发。
 *  - 请求体字段（以后端 FrontendLogUploadRequestDTO / FrontendLogEntryDTO 为准）：
 *    { logs: [{ timestamp, level, module, message, stack }] }
 *
 * 能力：
 *  - 手动上报：reportLog / logInfo / logWarn / logError
 *  - 自动捕获：window error / unhandledrejection / console.error
 *  - 内存队列 + 防抖批量上报（10s 兜底），页面隐藏 / 卸载时 sendBeacon 兜底
 *  - 上报失败静默吞掉，不影响业务
 */

/* ==================== 常量与配置 ==================== */

/** 日志上报总开关：localStorage['log-report-enabled'] === '0' 时关闭，其余（含缺失）默认开启 */
const LOG_REPORT_ENABLED = (() => {
  if (typeof localStorage === "undefined") return true;
  try {
    return localStorage.getItem("log-report-enabled") !== "0";
  } catch {
    return true;
  }
})();

/** 采样率（0~1）：dev 下全量；如需降采样可调低 */
const LOG_SAMPLE_RATE = 1;

/** 上报接口路径（与后端 Controller @RequestMapping 对齐） */
const UPLOAD_PATH = "/common/logs/frontend/upload";

/** 上报地址（开发环境走相对路径由 Vite 代理转发；API_BASE_URL 为完整域名时直连） */
const UPLOAD_URL = `${API_BASE_URL}${UPLOAD_PATH}`;

/** 批量上报防抖间隔（ms） */
const FLUSH_INTERVAL = 10_000;

/** 批量上报的最大条目数（达到即触发上报） */
const MAX_BATCH_SIZE = 50;

/** 内存队列最大容量（超出丢弃最旧，防止无限增长） */
const MAX_QUEUE_SIZE = 500;

/** 页面隐藏（visibilitychange / pagehide）时是否立即兜底上报 */
const FLUSH_ON_HIDE = true;

/** 系统当前时间格式化，对齐后端示例 "2026-09-27 14:30:12.345" */
function formatTimestamp(date: Date): string {
  const pad = (n: number, len = 2) => String(n).padStart(len, "0");
  const ms = pad(date.getMilliseconds(), 3);
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${ms}`
  );
}

/** 按 location.pathname 判断当前端：/admin 前缀为后台，其余为前台 */
function detectAppType(): "portal" | "admin" {
  if (typeof window === "undefined") return "portal";
  return window.location.pathname.startsWith("/admin") ? "admin" : "portal";
}

/** 日志级别（与后端 FrontendLogEntryDTO.level 对齐） */
export type LogLevel = "INFO" | "WARN" | "ERROR";

/** 单条待上报日志（与后端 FrontendLogEntryDTO 字段一致） */
export interface FrontendLogEntry {
  /** 时间戳，格式 "yyyy-MM-dd HH:mm:ss.SSS" */
  timestamp: string;
  /** 日志级别：INFO / WARN / ERROR */
  level: LogLevel;
  /** 模块名 */
  module: string;
  /** 日志消息 */
  message: string;
  /** 异常堆栈（仅 ERROR 级别） */
  stack: string;
}

/** 批量上报请求体（与后端 FrontendLogUploadRequestDTO 一致） */
interface FrontendLogUploadRequest {
  logs: FrontendLogEntry[];
}

/* ==================== 队列与状态 ==================== */

let queue: FrontendLogEntry[] = [];

let flushTimer: number | undefined;

let flushing = false;

/** 是否开启（开关关闭或非浏览器环境则不上报） */
const enabled = LOG_REPORT_ENABLED && typeof window !== "undefined";

/** 从队列中取出并清空 */
function drain(): FrontendLogEntry[] {
  const batch = queue;
  queue = [];
  return batch;
}

/** 入队，并达到批次上限时立即触发上报 */
function enqueue(entry: FrontendLogEntry): void {
  queue.push(entry);
  if (queue.length > MAX_QUEUE_SIZE) {
    queue.splice(0, queue.length - MAX_QUEUE_SIZE);
  }
  if (queue.length >= MAX_BATCH_SIZE) {
    void flush();
  } else {
    scheduleFlush();
  }
}

/* ==================== 定时 / 调度 ==================== */

/** 防抖：窗口期内最后一次触发才真正上报；每次调用重置计时器 */
function scheduleFlush(): void {
  if (!enabled || flushing) return;
  if (flushTimer !== undefined) {
    window.clearTimeout(flushTimer);
  }
  flushTimer = window.setTimeout(() => {
    flushTimer = undefined;
    void flush();
  }, FLUSH_INTERVAL);
}

/* ==================== 上报传输 ==================== */

/** 优先 sendBeacon（keepalive 语义，卸载不丢）；回退 fetch + keepalive；再回退 XMLHttpRequest */
function uploadPayload(payload: FrontendLogUploadRequest): void {
  try {
    const body = JSON.stringify(payload);
    if (typeof navigator !== "undefined" && typeof navigator.sendBeacon === "function") {
      // sendBeacon 只能 POST Blob/FormData，需显式声明 application/json
      const blob = new Blob([body], { type: "application/json; charset=UTF-8" });
      if (navigator.sendBeacon(UPLOAD_URL, blob)) {
        return;
      }
    }
    if (typeof fetch === "function") {
      // keepalive 保证页面卸载瞬间的请求也能发出；纯异步，不阻塞卸载
      void fetch(UPLOAD_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=UTF-8" },
        body,
        keepalive: true,
        credentials: "include",
      });
      return;
    }
    const xhr = new XMLHttpRequest();
    xhr.open("POST", UPLOAD_URL);
    xhr.setRequestHeader("Content-Type", "application/json; charset=UTF-8");
    xhr.send(body);
  } catch {
    /* 静默失败：绝不抛出影响业务 */
  }
}

/** 执行一次批量上报（串行锁，避免并发重复上报） */
function flush(): void {
  if (!enabled || flushing) return;
  const batch = drain();
  if (batch.length === 0) return;

  flushing = true;
  try {
    uploadPayload({ logs: batch });
  } catch {
    /* 静默失败 */
  } finally {
    flushing = false;
    // 防抖窗口内又有新日志，则继续调度
    if (queue.length > 0) scheduleFlush();
  }
}

/* ==================== 对外 API ==================== */

/**
 * 统包上报：手动上报一条日志。
 * @param level  级别（INFO / WARN / ERROR）
 * @param message 日志消息
 * @param context  附加上下文：{ module, error / stack, appType, loggedIn }
 *                 返回时自动合并 { pageUrl, userAgent, timestamp }。
 */
export function reportLog(
  level: LogLevel,
  message: string,
  context: ReportLogContext = {},
): void {
  if (!enabled) return;
  if (Math.random() > LOG_SAMPLE_RATE) return;

  const now = new Date();
  const appType = context.appType ?? detectAppType();
  const stack = context.stack ?? extractStack(context.error);
  const loggedIn = context.loggedIn ?? !!(getToken("user") || getToken("admin"));

  const entry: FrontendLogEntry = {
    timestamp: formatTimestamp(now),
    level,
    module: context.module ?? "App",
    message: buildMessage(message, {
      pageUrl: window.location.href,
      userAgent: navigator.userAgent,
      appType,
      loggedIn,
    }),
    stack,
  };

  enqueue(entry);
}

/** 手动上报的上下文参数类型 */
export interface ReportLogContext {
  /** 模块名（默认按当前端取 "Portal" / "Admin"） */
  module?: string;
  /** 关联的异常对象（用于提取 stack） */
  error?: unknown;
  /** 手动指定堆栈文本（优先级高于 error 提取） */
  stack?: string;
  /** 指定当前端（默认按 location.pathname 判断） */
  appType?: "portal" | "admin";
  /** 手动指定登录态标记（默认按 token 是否存在判断） */
  loggedIn?: boolean;
}

/** INFO 级日志 */
export function logInfo(message: string, context: ReportLogContext = {}): void {
  reportLog("INFO", message, context);
}

/** WARN 级日志 */
export function logWarn(message: string, context: ReportLogContext = {}): void {
  reportLog("WARN", message, context);
}

/** ERROR 级日志 */
export function logError(message: string, context: ReportLogContext = {}): void {
  reportLog("ERROR", message, context);
}

/* ==================== 自动捕获 ==================== */

/** 从任意 error 值中提取堆栈文本 */
function extractStack(error: unknown): string {
  if (error && typeof error === "object" && "stack" in error) {
    const stack = (error as { stack?: unknown }).stack;
    if (typeof stack === "string") return stack;
  }
  return "";
}

/** 把任意 rejection 值格式化为字符串 */
function stringifyReason(reason: unknown): string {
  if (reason === null) return "null";
  if (reason === undefined) return "undefined";
  if (typeof reason === "string") return reason;
  try {
    return JSON.stringify(reason);
  } catch {
    return String(reason);
  }
}

/** 记录一条由浏览器自动捕获的日志（附带调用栈、所在页面模块） */
function captureError(level: LogLevel, message: string, error?: unknown): void {
  const module = detectAppType() === "admin" ? "Admin" : "Portal";
  const stack = extractStack(error);
  reportLog(level, message, { module, error, stack });
}

/** 拦截 window error 事件（含资源加载错误，EventTarget 源无 ErrorEvent 详情） */
function onWindowError(event: ErrorEvent | Event): void {
  if (event instanceof ErrorEvent) {
    const msg = `Uncaught ${event.message} @ ${event.filename || "unknown"}:${event.lineno ?? "?"}`;
    captureError("ERROR", msg);
  } else {
    // 资源加载失败（img / script / link 等），ErrorEvent 不触发但 error 事件仍会上报
    const target = (event as Event).target as HTMLElement | null;
    const src = target?.tagName ? `${target.tagName.toLowerCase()} src=${(target as HTMLImageElement).src}` : "resource";
    captureError("WARN", `Resource load error: ${src}`);
  }
}

/** 拦截未处理的 Promise rejection */
function onUnhandledRejection(event: PromiseRejectionEvent): void {
  const reason = event.reason;
  captureError("ERROR", `Unhandled promise rejection: ${stringifyReason(reason)}`, reason);
}

/** 可选：复制一份 console.error 上报，保留原 console 行为，避免循环 */
function patchConsoleError(): void {
  try {
    const original = console.error;
    const patched = (...args: unknown[]) => {
      // 原行为保留（先调用，防止自身上报逻辑影响控制台输出时序）
      original.apply(console, args);
      try {
        const text = args.map((arg) => (typeof arg === "string" ? arg : safeStringify(arg))).join(" ");
        captureError("ERROR", text);
      } catch {
        /* 忽略 */
      }
    };
    patched.reportLog = reportLog; // 便于排查，避免被简单识别/清理
    console.error = patched;
  } catch {
    /* 静默 */
  }
}
/** 页面可见性/卸载时兜底上报队列中积压的日志 */
function attachPageLifecycle(): void {
  const flushOnUnload = () => {
    if (queue.length > 0) {
      uploadPayload({ logs: drain() });
    }
  };
  if (FLUSH_ON_HIDE) {
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") flushOnUnload();
    });
  }
  // pagehide 比 beforeunload 更可靠（移动端兼容性更好），卸载瞬间用 sendBeacon / keepalive
  window.addEventListener("pagehide", flushOnUnload);
  window.addEventListener("beforeunload", flushOnUnload);
}

/** 判断是否需要捕获 error 事件（页面关闭瞬间可能重复触发，简单去重） */
let captureInstalled = false;

/** 启用自动捕获与页面兜底上报 */
export function setupFrontendLogReporting(): void {
  if (!enabled || captureInstalled) return;
  captureInstalled = true;

  window.addEventListener("error", onWindowError, true);
  window.addEventListener("unhandledrejection", onUnhandledRejection);
  patchConsoleError();
  attachPageLifecycle();
}

/* ==================== 工具 ==================== */

/** 安全序列化任意值（用于 console 参数、rejection 等非字符串） */
function safeStringify(value: unknown): string {
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

/** 组装上报 message（携带页面上下文，便于后端直接查看） */
function buildMessage(
  raw: string,
  ctx: {
    pageUrl: string;
    userAgent: string;
    appType: string;
    loggedIn: boolean;
  },
): string {
  const { pageUrl, userAgent, appType, loggedIn } = ctx;
  return (
    `${raw} | ` +
    `{pageUrl: ${pageUrl}, ` +
    `userAgent: ${userAgent}, ` +
    `appType: ${appType}, ` +
    `loggedIn: ${loggedIn}}`
  );
}
