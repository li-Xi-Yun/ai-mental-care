import { ElMessage } from "element-plus";

/**
 * AI 配额拒绝事件载荷（后端 ConversationWebSocketManager.sendQuotaRejected 下发）
 * 与后端约定保持一致：
 * { "event": "QUOTA_REJECTED", "resourceType": 2|3|4 }
 */
export interface QuotaRejectedPayload {
  event?: string;
  resourceType?: number;
}

/**
 * 异步资源类型常量（与后端 QuotaConfig 对齐：2=ASR 3=TTS 4=AVATAR_2D）。
 * 独立维护，避免 portal 端跨层依赖 admin 常量。
 */
const RESOURCE_TYPE_ASR = 2;
const RESOURCE_TYPE_TTS = 3;
const RESOURCE_TYPE_AVATAR_2D = 4;

/**
 * 异步资源配额拒绝提示文案（与后端降级行为一一对应）：
 * - ASR(2)：识别被拒即句子未落库、未触发回复 → 明确告知识别不可用；
 * - TTS(3)：混合模式降级纯文本 / 纯 TTS 模式本轮无回复 → 告知语音合成不可用、以文本代替；
 * - AVATAR_2D(4)：混合模式正文照常 → 告知形象播报不可用、继续提供文本/语音。
 */
const QUOTA_REJECTED_MESSAGES: Record<number, string> = {
  [RESOURCE_TYPE_ASR]: "语音识别次数已用尽，请联系管理员",
  [RESOURCE_TYPE_TTS]: "语音合成次数已用尽，当前将以文本方式回复",
  [RESOURCE_TYPE_AVATAR_2D]: "2D 形象播报次数已用尽，将继续提供文本/语音回复",
};

/** 无法识别的 resourceType 时的兜底提示 */
const QUOTA_REJECTED_FALLBACK = "AI 资源使用次数已达上限，如需继续使用请联系管理员";

/**
 * 解析 QUOTA_REJECTED 事件并给出轻提示。
 * 供 STOMP 的 onQuotaRejected 回调调用。
 *
 * @param rawJson 后端下发的事件原始 JSON 字符串
 */
export function showQuotaRejected(rawJson: string): void {
  if (!rawJson) return;
  let payload: QuotaRejectedPayload | null = null;
  try {
    payload = JSON.parse(rawJson) as QuotaRejectedPayload;
  } catch {
    // 非 JSON（异常推送），按纯文本处理
    ElMessage.warning(rawJson);
    return;
  }
  if (!payload || payload.event !== "QUOTA_REJECTED") return;
  const type = payload.resourceType;
  const text = type != null ? QUOTA_REJECTED_MESSAGES[type] : undefined;
  ElMessage.warning(text ?? QUOTA_REJECTED_FALLBACK);
}