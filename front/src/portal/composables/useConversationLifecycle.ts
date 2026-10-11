/**
 * 会话生命周期调度（文本页 / 语音页共用）
 *
 * 设计：
 * - 内存态记录「当前已激活（后端存在管道）的会话 → 模式签名」，不做持久化；
 * - 所有生命周期写操作走同一条 Promise 串行链，避免 release/init 乱序互拆；
 * - 文本发送采用懒初始化：发送失败且业务码 2802（会话未初始化）才 init 并重发一次，
 *   其他错误直接抛出（避免 axios 超时等场景下的重复消息）；
 *   刷新后后端管道仍在 → 发送直接成功，不重复 init；后端重启/切换后 → 一次 2802 自愈。
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

/** 发送文本：仅 2802（未初始化）触发一次 init + 重发 */
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
 * 带生命周期保障的文本发送：
 * - 已激活但模式不同 → 先 init 重建再发送；
 * - 其余情况先发送（快速路径：刷新后管道仍在时零多余调用），失败按 2802 自愈。
 */
export async function sendTextWithLifecycle(
  conversationId: string,
  message: string,
  modes: LifecycleModes = TEXT_MODES,
): Promise<Awaited<ReturnType<typeof sendUserMessage>>> {
  const active = activeMap.get(conversationId);
  if (active !== undefined && active !== modesKey(modes)) {
    const realId = await initConversation(conversationId, modes);
    return sendWithRecovery(realId || conversationId, message, modes);
  }
  return sendWithRecovery(conversationId, message, modes);
}

/** 登出 / 切换账号时清空内存态 */
export function clearLifecycleState(): void {
  activeMap.clear();
}
