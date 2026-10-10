import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";
import { parseJSONWithBigInt } from "@/shared/utils/bigint";
import type { MemoryMessage, PendingActionItem, RoundMemory, ScaleCardActionData } from "./types";

/** 获取对话记忆 */
export function getConversationMemory(conversationId: string, data: any) {
  return httpClient.post<Result<any[]>>(`/user/conversation/dialogue/${conversationId}/memory`, data);
}

/**
 * 拉取并展平对话记忆：后端 records 为轮次单元（含 messages + pendingActions）且倒序返回，
 * 这里展平为「消息列表 + 待处理交互列表」，展示侧由调用方按 createdTime 排序。
 * （文本对话页与语音对话页共用，口径与既有轮询链路一致）
 */
export async function fetchRoundMemory(conversationId: string): Promise<RoundMemory> {
  const res = await getConversationMemory(conversationId, { pageNum: 1, pageSize: 200 });
  const body: any = res?.data?.data;
  const rounds: any[] = Array.isArray(body) ? body : Array.isArray(body?.records) ? body.records : [];

  const messages: MemoryMessage[] = [];
  const pendingActions: PendingActionItem[] = [];
  for (const round of rounds) {
    for (const m of round.messages ?? []) {
      messages.push({
        id: String(m.id),
        content: String(m.content ?? ""),
        type: String(m.type ?? "").toLowerCase(),
        roundNum: Number(m.roundNum ?? round.roundNum ?? 0),
        createdTime: m.createdTime ?? "",
      });
    }
    for (const p of round.pendingActions ?? []) {
      pendingActions.push({
        id: String(p.id),
        actionType: Number(p.actionType ?? 0),
        roundNum: Number(p.roundNum ?? round.roundNum ?? 0),
        actionData: p.actionData ?? undefined,
        status: Number(p.status ?? 0),
        expireTime: p.expireTime ?? "",
        completedTime: p.completedTime ?? "",
        createdTime: p.createdTime ?? "",
      });
    }
  }
  return { messages, pendingActions };
}

/**
 * 解析 /user/queue/pending-action 推送（后端下发的实体中 actionData 是 JSON 字符串，需安全解析）。
 * 使用 parseJSONWithBigInt 防止 19 位雪花 ID 在 JSON.parse 时丢精度；解析失败返回 null 由调用方忽略。
 */
export function parsePendingActionPush(rawJson: string): PendingActionItem | null {
  let raw: any;
  try {
    raw = parseJSONWithBigInt(rawJson);
  } catch {
    return null;
  }
  if (!raw || typeof raw !== "object" || raw.id === undefined || raw.id === null) return null;

  let actionData: ScaleCardActionData | undefined;
  const ad = raw.actionData;
  if (typeof ad === "string" && ad) {
    try {
      actionData = parseJSONWithBigInt(ad) as ScaleCardActionData;
    } catch {
      actionData = undefined;
    }
  } else if (ad && typeof ad === "object") {
    actionData = ad as ScaleCardActionData;
  }

  return {
    id: String(raw.id),
    actionType: Number(raw.actionType ?? 0),
    roundNum: Number(raw.roundNum ?? 0),
    actionData,
    status: Number(raw.status ?? 0),
    expireTime: raw.expireTime ?? "",
    completedTime: raw.completedTime ?? "",
    createdTime: raw.createdTime ?? "",
  };
}
