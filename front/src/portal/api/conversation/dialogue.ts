import httpClient from "@shared/api/instance";
import type { Result } from "@shared/api/types";

/** 获取对话记忆 */
export function getConversationMemory(conversationId: string, data: any) {
  return httpClient.post<Result<any[]>>(`/user/conversation/dialogue/${conversationId}/memory`, data);
}