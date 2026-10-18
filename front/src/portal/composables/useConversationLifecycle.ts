/**
 * 会话生命周期调度（文本页 / 语音页共用）
 *
 * 设计：
 * - 内存态记录「当前已激活（后端存在管道）的会话 → 模式签名」，不做持久化；
 * - 所有生命周期写操作走同一条 Promise 串行链，避免 release/init 乱序互拆；
 * - 文本发送采用「发送前确保已初始化」：会话未激活（新会话 / 切走又切回 / 刷新后管道未知）
 *   或模式不一致时，先幂等 init 再发送唯一一次；已激活且模式一致时直接发送。
 *   不再依赖「先发送 → 2802 → init → 重发」的探测链路（该链路会产生两条发送请求，
 *   与 AdapterController 接口级限流窗口冲突，导致 10001 频繁操作）。
 * - 语音输入不可重放，语音页首次输入由页面层无条件 init（幂等重建）。
 */
import { initConversationLifecycle, deleteConversationLifecycle } from "@/portal/api/conversation/lifecycle";
import { sendUserMessage } from "@/portal/api/conversation/ai-chat";

/** 会话初始化模式（与后端 inputTypes/outputTypes 对齐） */
export interface LifecycleModes {
  inputTypes: string[];
  outputTypes: string[];
}

/** 文本对话默认模式 */
export const TEXT_MODES: LifecycleModes = { inputTypes: ["TEXT"], outputTypes: ["TEXT"] };

/** 后端「会话未初始化 / 适配器不存在」业务码（InputAdapterExceptionEnum.SESSION_NOT_FOUND） */
export const SESSION_NOT_FOUND_CODE = 2802;

/** 已激活会话 id → 模式签名（内存态，刷新即失，靠 2802 自愈） */
const activeMap = new Map<string, string>();

/** 生命周期写操作串行链（全局单链：同一时刻只允许一个 init/release 在飞） */
let chain: Promise<unknown> = Promise.resolve();

function enqueue<T>(task: () => Promise<T>): Promise<T> {
  const run = chain.then(task);
  chain = run.then(
    () => undefined,
    () => undefined,
  );
  return run;
}

function modesKey(modes: LifecycleModes): string {
  const input = [...modes.inputTypes].sort().join(",");
  const output = [...modes.outputTypes].sort().join(",");
  return `${input}|${output}`;
}

/** 本地临时会话 id（尚未在后端创建，禁止用于生命周期接口） */
export function isLocalConversationId(id: string | null | undefined): boolean {
  return !!id && id.startsWith("local-");
}

function errorCode(e: unknown): unknown {
  return (e as { code?: unknown } | null)?.code;
}

/**
 * 初始化（幂等）会话生命周期。
 * @param id 已有会话 id；为空则后端新建
 * @returns 真实会话 id（19 位雪花 ID 字符串直传，禁止 Number()）
 */
export async function initConversation(id: string | undefined, modes: LifecycleModes): Promise<string> {
  const existingId = id && !isLocalConversationId(id) ? id : "";
  const res = await enqueue(() =>
    initConversationLifecycle({
      conversationId: existingId || undefined,
      inputTypes: modes.inputTypes,
      outputTypes: modes.outputTypes,
    }),
  );
  const realId = String(res?.data?.data?.conversationId ?? existingId ?? "");
  if (realId) activeMap.set(realId, modesKey(modes));
  return realId;
}

/** 销毁会话资源（对未初始化会话调用不报错）；本地临时 id 直接跳过 */
export function releaseConversation(id: string | null | undefined): Promise<void> {
  if (!id || isLocalConversationId(id)) return Promise.resolve();
  return enqueue(async () => {
    try {
      await deleteConversationLifecycle(id);
    } catch (e) {
      console.warn(`[lifecycle] 释放会话资源失败 conversationId=${id}`, e);
    } finally {
      activeMap.delete(id);
    }
  });
}

/** 发送文本：仅 2802（未初始化）触发一次 init + 重发（边缘兜底，正常路径不会走到） */
async function sendWithRecovery(
  conversationId: string,
  message: string,
  modes: LifecycleModes,
): Promise<Awaited<ReturnType<typeof sendUserMessage>>> {
  try {
    return await sendUserMessage({ message, conversationId });
  } catch (e) {
    if (errorCode(e) !== SESSION_NOT_FOUND_CODE) throw e;
    activeMap.delete(conversationId);
    const realId = await initConversation(conversationId, modes);
    return sendUserMessage({ message, conversationId: realId || conversationId });
  }
}

/**
 * 带生命周期保障的文本发送：发送前先确保会话生命周期资源已建立（幂等 init）。
 *
 * 设计：
 * - 发送前检查 activeMap 中的激活态；会话未激活（新会话 / 切走又切回 / 刷新后管道未知）
 *   或模式不匹配时，先幂等 init（后端 init 本身会销毁旧管道并重建），确保「先有资源再发送」，
 *   避免先发送 → 2802 → init → 重发的两条发送请求（重发会与上一条撞上接口级限流）。
 * - 已激活且模式一致时走快速路径：直接发送（此时后端管道一定存在，不会有 2802）。
 * - sendWithRecovery 仅作边缘兜底（如 init 成功但后端仍返回 2802），正常情况下不会被触发。
 */
export async function sendTextWithLifecycle(
  conversationId: string,
  message: string,
  modes: LifecycleModes = TEXT_MODES,
): Promise<Awaited<ReturnType<typeof sendUserMessage>>> {
  const active = activeMap.get(conversationId);
  if (active === modesKey(modes)) {
    // 已激活且模式一致：后端管道必然存在，直接发送（快速路径，零多余调用）
    return sendWithRecovery(conversationId, message, modes);
  }
  // 未激活或模式不同：先幂等 init 确保资源存在，再发送唯一一次（避免 2802 探测 + 重发）
  const realId = await initConversation(conversationId, modes);
  return sendWithRecovery(realId || conversationId, message, modes);
}

/** 登出 / 切换账号时清空内存态 */
export function clearLifecycleState(): void {
  activeMap.clear();
}
