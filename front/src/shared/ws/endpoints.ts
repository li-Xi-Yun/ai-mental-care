/**
 * AI 对话 WebSocket（STOMP）端点与运行时常量
 *
 * 与后端约定（参见 temp 参考实现）：
 * - 握手前必须用 user-token 换取一次性 wsTicket（10s 过期、使用即删），URL 形如
 *   ws://host:port/ws-native?wsTicket=xxx；
 * - 订阅目的地统一为 /user/queue/{子路径}/{conversationId}；
 * - 上行（客户端 SEND）只有音频帧一个目的地：/app/audio/frame
 *   （后端类级 @RequestMapping 不参与 STOMP 映射，此处为 /app 前缀 + @MessageMapping 路径）。
 */

/** 上行音频帧目的地（JSON：{ audioMessage: base64(PCM16LE 16k 单声道), conversationId }） */
export const STOMP_AUDIO_FRAME_DEST = "/app/audio/frame";

/** 拼接用户队列订阅路径：/user/queue + 子路径 + /会话ID */
function userQueueTopic(subPath: string, conversationId: string): string {
  return `/user/queue${subPath}/${conversationId}`;
}

/** AI 文本回复增量（纯字符串 chunk，无结束标记，需空闲 debounce 判定结束） */
export const textReplyTopic = (conversationId: string) => userQueueTopic("/text/reply", conversationId);
/** 会话名称推送（AI 生成标题后下发） */
export const conversationNameTopic = (conversationId: string) => userQueueTopic("/conversation/name", conversationId);
/** 待处理人工交互推送（量表推荐卡片等；actionData 为 JSON 字符串） */
export const pendingActionTopic = (conversationId: string) => userQueueTopic("/pending-action", conversationId);
/** TTS 音频二进制帧（裸 PCM16LE 单声道 24kHz） */
export const audioBinaryTopic = (conversationId: string) => userQueueTopic("/audio/binary", conversationId);
/** ASR 中间识别结果（实时弹幕，最终以 memory 落库文本为准） */
export const asrIntermediateTopic = (conversationId: string) => userQueueTopic("/audio/asr/intermediate", conversationId);

/** 一次性 wsTicket 接口路径（后端真实路径带 /api 前缀，前端 vite 代理会 strip，故必须直连后端 origin） */
export const WS_TICKET_PATH = "/api/get-ws-ticket";
/** 原生 WebSocket 端点（必须原生才能接收 TTS 二进制帧；SockJS 端点 /ws/ai-mental-care 无法承载二进制） */
export const WS_NATIVE_ENDPOINT = "/ws-native";

/** STOMP 心跳（与后端配置对齐：服务端 10s / 客户端 20s） */
export const WS_HEARTBEAT_INCOMING = 10000;
export const WS_HEARTBEAT_OUTGOING = 20000;

/**
 * 解析后端直连 origin（ticket 与 WebSocket 均不走 vite 代理）：
 * - dev：http://localhost:8088；
 * - 生产：默认同源（由网关转发 /ws-native 与 /api/get-ws-ticket）；
 * - 可用 VITE_BACKEND_ORIGIN 覆盖。
 */
export function resolveBackendOrigin(): string {
  const envOrigin = import.meta.env.VITE_BACKEND_ORIGIN as string | undefined;
  if (envOrigin) return envOrigin.replace(/\/+$/, "");
  if (import.meta.env.DEV) return "http://localhost:8088";
  return window.location.origin;
}

/** 由 http(s) origin 推导 ws(s) base */
export function toWsBaseUrl(origin: string): string {
  return origin.replace(/^http/, "ws");
}

/** 构造原生 WebSocket 连接地址（携带一次性 ticket） */
export function buildNativeWsUrl(origin: string, ticket: string): string {
  return `${toWsBaseUrl(origin)}${WS_NATIVE_ENDPOINT}?wsTicket=${encodeURIComponent(ticket)}`;
}
