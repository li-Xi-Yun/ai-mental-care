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
/** AI 配额拒绝事件（异步资源超限/停用时后端下发；载荷 JSON：{ event:"QUOTA_REJECTED", resourceType: 2|3|4 }） */
export const quotaRejectedTopic = (conversationId: string) => userQueueTopic("/quota/rejected", conversationId);

/** 虚拟形象表情指令（持续状态；载荷 JSON 字符串 {"expression":"<资产id>"}） */
export const avatar2dExpressionTopic = (conversationId: string) => userQueueTopic("/avatar-2d/expression", conversationId);
/** 虚拟形象动作指令（一次性触发；载荷 JSON 字符串 {"motion":"<资产id>"}） */
export const avatar2dMotionTopic = (conversationId: string) => userQueueTopic("/avatar-2d/motion", conversationId);

/** 新情绪分析落库通知（后端 EmotionRecognitionNode 落库后下发；载荷 JSON：{ event:"EMOTION_ANALYSIS_UPDATED", id, roundNum }） */
export const emotionAnalysisUpdatedTopic = (conversationId: string) => userQueueTopic("/emotion/analysis", conversationId);
/** 新心理评估（诊断书）落库通知（后端 DiagnosisPersistNode 落库后下发；载荷 JSON：{ event:"DIAGNOSIS_CREATED", id, roundNum }） */
export const diagnosisCreatedTopic = (conversationId: string) => userQueueTopic("/diagnosis", conversationId);
/** 会话空闲超时回收通知（后端 ConversationIdleTimeoutTask 判定超时后下发；载荷 JSON：{ event:"SESSION_TIMEOUT" }） */
export const sessionTimeoutTopic = (conversationId: string) => userQueueTopic("/session/timeout", conversationId);

/** 文件向量解析状态推送（管理端；载荷 JSON：{ fileId, status, progress?, message?, sequence, timestamp }） */
export function fileVectorStatusTopic(fileId: string): string {
  return `/user/queue/file/vector/status/${fileId}`;
}

/**
 * 用户级错误队列（无会话后缀；后端异常处理器 @SendToUser("/queue/error") 下发业务/管道异常，
 * 例如 ASR 会话失效、适配器不存在 2802）。前端订阅后可在界面提示，避免"静默无反应"。
 */
export const USER_ERROR_TOPIC = "/user/queue/error";

/** 一次性 wsTicket 接口路径（后端真实路径为 /api/get-ws-ticket，context-path=/，故不携带 PAGE_BASE_URL 前缀；生产由网关直接转发 /api/get-ws-ticket） */
export const WS_TICKET_PATH = "/api/get-ws-ticket";
/** 管理员一次性 wsTicket 接口路径（管理端 WebSocket 实时通知用，请求头携带 admin-token） */
export const ADMIN_WS_TICKET_PATH = "/admin/get-ws-ticket";
/** 原生 WebSocket 端点（后端真实路径为 /ws-native，context-path=/，故不携带 PAGE_BASE_URL 前缀；生产由网关直接转发 /ws-native） */
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
